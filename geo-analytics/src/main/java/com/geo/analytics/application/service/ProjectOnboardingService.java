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
    private final TransactionTemplate transactionTemplate;
    private final ProjectContextTextLimiter projectContextTextLimiter;

    public ProjectOnboardingService(
            CreditVaultService creditVaultService,
            ProjectRepository projectRepository,
            SafeHttpClient safeHttpClient,
            DebateOnboardingOrchestrator debateOnboardingOrchestrator,
            TransactionTemplate transactionTemplate,
            ProjectContextTextLimiter projectContextTextLimiter) {
        this.creditVaultService = creditVaultService;
        this.projectRepository = projectRepository;
        this.safeHttpClient = safeHttpClient;
        this.debateOnboardingOrchestrator = debateOnboardingOrchestrator;
        this.transactionTemplate = transactionTemplate;
        this.projectContextTextLimiter = projectContextTextLimiter;
    }

    public void runOnboarding(UUID projectId, String url, IndustryType industryHint) {
        String trimmedUrl = url.trim();
        validateHttpUrl(trimmedUrl);
        AtomicReference<UUID> reservationId = new AtomicReference<>();
        AtomicBoolean settled = new AtomicBoolean();
        try {
            reservationId.set(creditVaultService.reserve(projectId, ONBOARDING_CREDIT));
            GeoOnboardingLlmResult result;
            try (var scope = StructuredTaskScope.open()) {
                var sub =
                        scope.fork(
                                () ->
                                        com.geo.analytics.infrastructure.tenant.ContextPropagator.wrap(
                                                        () -> runGeoPipeline(projectId, trimmedUrl, industryHint))
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
                        creditVaultService.settle(resId, ONBOARDING_CREDIT);
                        applyProjectSnapshot(projectId, toPersist);
                    });
            settled.set(true);
        } finally {
            UUID rid = reservationId.get();
            if (rid != null && !settled.get()) {
                try {
                    transactionTemplate.executeWithoutResult(s -> creditVaultService.refund(rid));
                } catch (RuntimeException e) {
                    log.error(
                            "[CRITICAL-LEDGER-REFUND-FAILED] Failed to refund credit for reservation: {}", rid, e);
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

    private GeoOnboardingLlmResult runGeoPipeline(UUID projectId, String url, IndustryType industryHint) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException illegalArgumentException) {
            throw new IllegalStateException("url", illegalArgumentException);
        }
        HttpRequest request =
                HttpRequest.newBuilder(uri).GET().timeout(Duration.ofSeconds(45)).header("User-Agent", "GeoAnalyticsBot/1.0").build();
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
                plain, projectId, searchQuery, seoRows, industryHint);
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
                        "フェーズ1.3 のプレースホルダ。実際の検索スニペットは Serp API 連携で置換予定。",
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
