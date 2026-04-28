package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.GeoOnboardingLlmResult;
import com.geo.analytics.domain.entity.ProjectEntity;
import com.geo.analytics.domain.enums.IndustryType;
import com.geo.analytics.domain.model.MinorityReport;
import com.geo.analytics.domain.model.SeoOrganicRow;
import com.geo.analytics.infrastructure.crawler.extraction.StreamTextExtractor;
import com.geo.analytics.infrastructure.crawler.safety.SafeHttpClient;
import com.geo.analytics.infrastructure.repository.ProjectRepository;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import com.geo.analytics.web.dto.DebateOnboardingSseEvent;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.FailedException;

@Service
public class ProjectOnboardingService {
    private static final Logger log = LoggerFactory.getLogger(ProjectOnboardingService.class);
    private static final long ONBOARDING_CREDIT = 1_000L;
    private static final long MAX_EXTRACT_BYTES = 2_000_000L;
    private final CreditVaultService creditVaultService;
    private final ProjectRepository projectRepository;
    private final SafeHttpClient safeHttpClient;
    private final DebateOnboardingOrchestrator debateOnboardingOrchestrator;
    private final OnboardingDebateStreamRegistry onboardingDebateStreamRegistry;
    private final TransactionTemplate transactionTemplate;
    private final ProjectContextTextLimiter projectContextTextLimiter;

    public ProjectOnboardingService(
            CreditVaultService creditVaultService,
            ProjectRepository projectRepository,
            SafeHttpClient safeHttpClient,
            DebateOnboardingOrchestrator debateOnboardingOrchestrator,
            OnboardingDebateStreamRegistry onboardingDebateStreamRegistry,
            TransactionTemplate transactionTemplate,
            ProjectContextTextLimiter projectContextTextLimiter) {
        this.creditVaultService = creditVaultService;
        this.projectRepository = projectRepository;
        this.safeHttpClient = safeHttpClient;
        this.debateOnboardingOrchestrator = debateOnboardingOrchestrator;
        this.onboardingDebateStreamRegistry = onboardingDebateStreamRegistry;
        this.transactionTemplate = transactionTemplate;
        this.projectContextTextLimiter = projectContextTextLimiter;
    }

    public void runOnboarding(UUID projectId, String url, IndustryType industryHint, UUID sessionId) {
        String trimmedUrl = url.trim();
        validateHttpUrl(trimmedUrl);
        AtomicReference<UUID> reservationId = new AtomicReference<>();
        AtomicBoolean settled = new AtomicBoolean();
        AtomicInteger executedTurns = new AtomicInteger();
        try {
            reservationId.set(creditVaultService.reserve(projectId, ONBOARDING_CREDIT));
            GeoOnboardingLlmResult result;
            try (var scope = StructuredTaskScope.open()) {
                var sub =
                        scope.fork(
                                () ->
                                        com.geo.analytics.infrastructure.tenant.ContextPropagator.wrap(
                                                        () ->
                                                                runGeoPipeline(
                                                                        projectId,
                                                                        trimmedUrl,
                                                                        industryHint,
                                                                        sessionId,
                                                                        executedTurns))
                                                .get());
                scope.join();
                result = sub.get();
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interruptedException);
            } catch (FailedException failedException) {
                throw unwrapFailed(failedException);
            }
            final GeoOnboardingLlmResult toPersist = result;
            final UUID resId = reservationId.get();
            transactionTemplate.executeWithoutResult(
                    s -> {
                        creditVaultService.settle(
                                resId,
                                ONBOARDING_CREDIT,
                                "ONBOARDING_COMPLETED_SETTLE (full consumption)");
                        applyProjectSnapshot(projectId, toPersist);
                    });
            settled.set(true);
        } finally {
            UUID rid = reservationId.get();
            if (rid != null && !settled.get()) {
                try {
                    int t = executedTurns.get();
                    long consumedAmount = (ONBOARDING_CREDIT * t) / DebateOnboardingOrchestrator.MAX_DEBATE_TURNS;
                    String note = String.format("SESSION_CANCELLED_PARTIAL_SETTLE (Executed: %d turns)", t);
                    transactionTemplate.executeWithoutResult(
                            s -> creditVaultService.settle(rid, consumedAmount, note));
                } catch (RuntimeException e) {
                    log.error(
                            "[CRITICAL-LEDGER-PARTIAL-SETTLE-FAILED] Failed partial settle for reservation: {}",
                            rid,
                            e);
                }
            }
            if (sessionId != null) {
                try {
                    onboardingDebateStreamRegistry.complete(projectId, sessionId);
                } catch (RuntimeException e) {
                    log.debug(
                            "onboarding sse registry complete failed projectId={} sessionId={}",
                            projectId,
                            sessionId,
                            e);
                }
            }
        }
    }

    private static RuntimeException unwrapFailed(FailedException failedException) {
        Throwable c = failedException.getCause();
        if (c instanceof RuntimeException) {
            return (RuntimeException) c;
        }
        if (c != null) {
            return new RuntimeException(c);
        }
        return new RuntimeException(failedException);
    }

    private void applyProjectSnapshot(UUID projectId, GeoOnboardingLlmResult result) {
        ProjectEntity project = projectRepository.findById(projectId).orElseThrow(() -> new IllegalArgumentException("projectId"));
        UUID expectedWorkspace = TenantContextHolder.requireContext().tenantId();
        if (!project.getWorkspaceId().equals(expectedWorkspace)) {
            throw new IllegalStateException("project");
        }
        project.setIndustryType(result.industry());
        project.setExtractedStrengths(
                projectContextTextLimiter.limit(String.join("\n", result.strengths())));
        project.setTargetAudience(projectContextTextLimiter.limit(result.targetAudience()));
        List<MinorityReport> reports = result.minorityReports();
        project.setMinorityReports(
                reports.stream()
                        .map(
                                r ->
                                        new MinorityReport(
                                                projectContextTextLimiter.limit(r.insight()),
                                                projectContextTextLimiter.limit(r.conflictReason()),
                                                projectContextTextLimiter.limit(r.evidence())))
                        .toList());
        projectRepository.save(project);
    }

    private GeoOnboardingLlmResult runGeoPipeline(
            UUID projectId, String url, IndustryType industryHint, UUID sessionId, AtomicInteger executedTurns) {
        List<DebateOnboardingSseEvent> narrationLogBuffer = new ArrayList<>(64);
        Thread worker = Thread.currentThread();
        try {
            if (sessionId != null) {
                onboardingDebateStreamRegistry.bindWorkerThread(projectId, sessionId, worker);
            }
            emitNarration(
                    projectId,
                    sessionId,
                    narrationLogBuffer,
                    "公開ページから一次情報を取得しています。ノイズ除去と安全な取得制限を適用します。",
                    DebateOnboardingSseEvent.DebateOnboardingSseEventType.NARRATION,
                    DebateOnboardingSseEvent.DebateStreamPersona.SYSTEM,
                    DebateOnboardingSseEvent.DebateStreamPhase.GATHERING);
            URI uri;
            try {
                uri = URI.create(url);
            } catch (IllegalArgumentException illegalArgumentException) {
                throw new IllegalStateException("url", illegalArgumentException);
            }
            HttpRequest request =
                    HttpRequest.newBuilder(uri)
                            .GET()
                            .timeout(Duration.ofSeconds(45))
                            .header("User-Agent", "GeoAnalyticsBot/1.0")
                            .build();
            HttpResponse<InputStream> response;
            try {
                response = safeHttpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (UncheckedIOException uncheckedIOException) {
                throw new RuntimeException(uncheckedIOException);
            }
            int code = response.statusCode();
            if (code / 100 != 2) {
                InputStream b = response.body();
                if (b != null) {
                    try (InputStream in = b) {
                        in.transferTo(java.io.OutputStream.nullOutputStream());
                    } catch (IOException ioException) {
                        throw new UncheckedIOException(ioException);
                    }
                }
                throw new IllegalStateException("http " + code);
            }
            String plain;
            try (InputStream in = Objects.requireNonNull(response.body(), "body")) {
                plain = StreamTextExtractor.extract(in, MAX_EXTRACT_BYTES, StandardCharsets.UTF_8);
            } catch (IOException ioException) {
                throw new UncheckedIOException(ioException);
            }
            String searchQuery = extractSearchQueryHint(uri);
            List<SeoOrganicRow> seoRows = buildPlaceholderSeoRows(url);
            return debateOnboardingOrchestrator.runDebateOnboarding(
                    plain, projectId, searchQuery, seoRows, industryHint, sessionId, executedTurns, narrationLogBuffer);
        } finally {
            if (sessionId != null) {
                onboardingDebateStreamRegistry.clearWorkerThread(projectId, sessionId, worker);
            }
        }
    }

    private void emitNarration(
            UUID projectId,
            UUID sessionId,
            List<DebateOnboardingSseEvent> narrationLogBuffer,
            String message,
            DebateOnboardingSseEvent.DebateOnboardingSseEventType eventType,
            DebateOnboardingSseEvent.DebateStreamPersona persona,
            DebateOnboardingSseEvent.DebateStreamPhase phase) {
        if (sessionId == null) {
            return;
        }
        Instant timestamp = Instant.now();
        DebateOnboardingSseEvent wireEvent =
                new DebateOnboardingSseEvent(
                        eventType, persona, phase, message, null, timestamp, sessionId);
        DebateOnboardingSseEvent bufferEvent =
                new DebateOnboardingSseEvent(
                        eventType,
                        persona,
                        phase,
                        DebateOnboardingOrchestrator.truncateNarrationForAudit(message),
                        null,
                        timestamp,
                        sessionId);
        synchronized (narrationLogBuffer) {
            DebateOnboardingOrchestrator.enforceNarrationLogBufferCap(narrationLogBuffer);
            narrationLogBuffer.add(bufferEvent);
        }
        try {
            onboardingDebateStreamRegistry.sendEvent(projectId, sessionId, wireEvent);
        } catch (Throwable throwable) {
            log.debug("onboarding narration skipped: {}", throwable.toString());
        }
    }

    /**
     * Serp API のオーガニック結果パースに差し替え予定。現状はオンボ1件のプレースホルダ。
     */
    private static List<SeoOrganicRow> buildPlaceholderSeoRows(String pageUrl) {
        String safeUrl = pageUrl == null ? "" : pageUrl.trim();
        if (safeUrl.isEmpty()) {
            return List.of();
        }
        List<SeoOrganicRow> rows = new ArrayList<>(1);
        rows.add(
                new SeoOrganicRow(
                        safeUrl,
                        "自社サイト（オンボーディング対象）",
                        "フェーズ1.3 のプレースホルダ。実際の生成AI回答内シェア用スニペットは外部取得API連携で置換予定。",
                        Optional.empty(),
                        Optional.empty()));
        return List.copyOf(rows);
    }

    /**
     * 類似度スコア用の簡易クエリ。ホストが取れなければ URL 全体を返す。
     */
    private static String extractSearchQueryHint(URI uri) {
        if (uri == null) {
            return "";
        }
        String host = uri.getHost();
        if (host != null && !host.isEmpty()) {
            return host;
        }
        return uri.toString();
    }

    private static void validateHttpUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException illegalArgumentException) {
            throw new IllegalStateException("url", illegalArgumentException);
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                || uri.getHost() == null) {
            throw new IllegalStateException("url");
        }
    }
}
