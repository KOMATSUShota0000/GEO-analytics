package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.CrawledPageData;
import com.geo.analytics.application.dto.DomainDeepAuditContext;
import com.geo.analytics.application.dto.JobAnalysisAggregate;
import com.geo.analytics.application.dto.MeoTrust;
import com.geo.analytics.application.dto.RubricItemAudit;
import com.geo.analytics.application.port.MEODataPort;
import com.geo.analytics.domain.dto.RubricAuditResult;
import com.geo.analytics.domain.entity.AuditHistoryEntity;
import com.geo.analytics.domain.entity.AuditRubricResultEntity;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.ProjectEntity;
import com.geo.analytics.domain.enums.RubricCriterionId;
import com.geo.analytics.domain.enums.RubricVerdictStatus;
import com.geo.analytics.domain.service.ThirdPartyMentionScorer;
import com.geo.analytics.infrastructure.ai.JobPromptContextFormatter;
import com.geo.analytics.infrastructure.crawler.safety.SafeHttpClient;
import com.geo.analytics.infrastructure.repository.AuditRubricResultRepository;
import com.geo.analytics.infrastructure.tenant.ContextPropagator;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import com.geo.analytics.infrastructure.tenant.TenantPlanScope;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.StructuredTaskScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiRubricAuditService {

    private static final Logger log = LoggerFactory.getLogger(AiRubricAuditService.class);
    private static final int STACK_TRACE_LIMIT = 20_000;
    private static final Duration SCOPE_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration LLMS_TXT_TIMEOUT = Duration.ofSeconds(8);
    private static final double PARTIAL_RATIO = 0.5d;
    private static final double MEO_MAX_REVIEWS = 50.0d;
    private static final double MEO_MAX_SCORE = 25.0d;
    private static final double MEO_INV_REVIEWS = 1.0d / MEO_MAX_REVIEWS;

    private final SmartDomainCrawlService smartDomainCrawlService;
    private final SafeHttpClient safeHttpClient;
    private final RubricAuditService rubricAuditService;
    private final MEODataPort meoDataPort;
    private final AuditRubricResultRepository auditRubricResultRepository;
    private final JobPersistenceService jobPersistenceService;
    private final ThirdPartyMentionMeasurementService thirdPartyMentionMeasurementService;
    private AiRubricAuditService self;

    public AiRubricAuditService(
            SmartDomainCrawlService smartDomainCrawlService,
            SafeHttpClient safeHttpClient,
            RubricAuditService rubricAuditService,
            MEODataPort meoDataPort,
            AuditRubricResultRepository auditRubricResultRepository,
            JobPersistenceService jobPersistenceService,
            ThirdPartyMentionMeasurementService thirdPartyMentionMeasurementService) {
        this.smartDomainCrawlService = smartDomainCrawlService;
        this.safeHttpClient = safeHttpClient;
        this.rubricAuditService = rubricAuditService;
        this.meoDataPort = meoDataPort;
        this.auditRubricResultRepository = auditRubricResultRepository;
        this.jobPersistenceService = jobPersistenceService;
        this.thirdPartyMentionMeasurementService = thirdPartyMentionMeasurementService;
    }

    @Autowired
    public void setSelf(@Lazy AiRubricAuditService self) {
        this.self = self;
    }

    /**
     * 解析完了後に自社ドメインを監査する。戻り値は自社分のクロール結果と LLM 監査結果（失敗時は null）。
     *
     * <p>Why: 同じ自社サイトへのクロールと監査をベンチマーク保存側でも行っており、1解析あたりどちらも
     * 2回払っていた（#72）。ここで得た結果を呼び出し元が {@code JobBenchmarkCaptureService} へ渡す。
     */
    public SelfAuditSnapshot runMultiDomainAuditForCompletedJob(UUID jobId) {
        if (jobId == null) {
            return null;
        }
        try {
            JobAnalysisAggregate agg = jobPersistenceService.findJobAnalysisAggregate(jobId);
            JobEntity job = agg.job();
            ProjectEntity project = agg.project();
            if (project == null || job.getProjectId() == null) {
                return null;
            }
            List<AuditHistoryEntity> audits = agg.auditHistories();
            AuditHistoryEntity latest = LatestAuditHistorySelector.pickLatest(audits);
            if (latest == null) {
                return null;
            }
            String targetUrl = project.getTargetUrl();
            if (targetUrl == null || targetUrl.isBlank()) {
                return null;
            }
            String trimmedTarget = targetUrl.trim();
            ArrayList<String> domainUrls = new ArrayList<>();
            domainUrls.add(trimmedTarget);
            String brand = job.getBrandName();
            String meoSearchQuery =
                    brand != null && !brand.isBlank()
                            ? brand.trim()
                            : project.getName() != null ? project.getName().trim() : "";
            UUID workspaceId = Objects.requireNonNullElse(job.getWorkspaceId(), DefaultTenantIds.WORKSPACE_ID);
            UUID projectId = job.getProjectId();
            UUID auditHistoryId = latest.getId();
            return TenantPlanScope.executeWithTenant(
                    workspaceId,
                    () -> self.auditAllDomains(
                                    projectId,
                                    auditHistoryId,
                                    trimmedTarget,
                                    meoSearchQuery,
                                    domainUrls,
                                    jobId)
                            .selfAudit());
        } catch (RuntimeException ex) {
            log.warn("multi_domain_audit_after_job_failed jobId={}", jobId, ex);
            return null;
        }
    }

    /**
     * 自社ドメイン1件分のクロール結果と LLM 監査結果。
     *
     * <p>Why: 同じ自社サイトへのクロールと LLM 監査が、ベンチマーク保存側でも重ねて行われ、1解析あたり
     * どちらも2回払っていた（#72）。ここで得たものを渡して使い回す。
     */
    public record SelfAuditSnapshot(
            CrawledPageData crawled, com.geo.analytics.application.dto.RubricAuditResult llmAudit) {}

    /** 全ドメインの監査結果と、そのうち自社分の成果物。 */
    public record MultiDomainAudit(
            Map<String, List<RubricAuditResult>> byUrl, SelfAuditSnapshot selfAudit) {}

    private record DomainAudit(
            List<RubricAuditResult> rows,
            CrawledPageData crawled,
            com.geo.analytics.application.dto.RubricAuditResult llmAudit) {}

    public MultiDomainAudit auditAllDomains(
            UUID projectId,
            UUID auditHistoryId,
            String selfUrl,
            String meoSearchQuery,
            List<String> domainUrls,
            UUID jobId) {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId");
        }
        if (auditHistoryId == null) {
            throw new IllegalArgumentException("auditHistoryId");
        }
        if (domainUrls == null || domainUrls.isEmpty()) {
            return new MultiDomainAudit(Map.of(), null);
        }
        String normalizedSelfUrl = selfUrl == null ? "" : selfUrl.trim();
        ArrayList<String> normalizedUrls = new ArrayList<>(domainUrls.size());
        for (int i = 0; i < domainUrls.size(); i++) {
            String raw = domainUrls.get(i);
            if (raw == null) {
                continue;
            }
            String trimmed = raw.trim();
            if (!trimmed.isEmpty()) {
                normalizedUrls.add(trimmed);
            }
        }
        if (normalizedUrls.isEmpty()) {
            return new MultiDomainAudit(Map.of(), null);
        }
        final String jobContextBlock =
                jobId == null
                        ? null
                        : jobPersistenceService
                                .findJobByIdOptional(jobId)
                                .map(JobPromptContextFormatter::format)
                                .orElse(null);
        ArrayList<DomainSubtask> tracked = new ArrayList<>(normalizedUrls.size());
        try (StructuredTaskScope<DomainAudit, Void> scope = StructuredTaskScope.open(
                StructuredTaskScope.Joiner.<DomainAudit>awaitAll(),
                cf -> cf.withTimeout(SCOPE_TIMEOUT)
                        .withThreadFactory(Thread.ofVirtual().name("ai-rubric-audit-", 0).factory()))) {
            for (int i = 0; i < normalizedUrls.size(); i++) {
                String url = normalizedUrls.get(i);
                StructuredTaskScope.Subtask<DomainAudit> subtask =
                        scope.fork(() -> ContextPropagator.wrap(
                                        () -> auditOneDomain(projectId, url, jobContextBlock))
                                .get());
                tracked.add(new DomainSubtask(url, subtask));
            }
            try {
                scope.join();
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                log.error(
                        "ai_rubric_audit_interrupted projectId={} trace={}",
                        projectId,
                        truncateStackTrace(interruptedException));
            } catch (StructuredTaskScope.TimeoutException timeoutException) {
                log.error(
                        "ai_rubric_audit_timeout projectId={} trace={}",
                        projectId,
                        truncateStackTrace(timeoutException));
            }
        }
        LinkedHashMap<String, List<RubricAuditResult>> aggregated = new LinkedHashMap<>(tracked.size());
        SelfAuditSnapshot selfSnapshot = null;
        for (int i = 0; i < tracked.size(); i++) {
            DomainSubtask entry = tracked.get(i);
            StructuredTaskScope.Subtask.State state = entry.subtask.state();
            if (state == StructuredTaskScope.Subtask.State.SUCCESS) {
                try {
                    DomainAudit domainAudit = entry.subtask.get();
                    aggregated.put(entry.url, domainAudit.rows());
                    if (entry.url.equals(normalizedSelfUrl)) {
                        selfSnapshot = new SelfAuditSnapshot(domainAudit.crawled(), domainAudit.llmAudit());
                    }
                } catch (RuntimeException runtimeException) {
                    log.error(
                            "ai_rubric_audit_collect_failed projectId={} url={} trace={}",
                            projectId,
                            entry.url,
                            truncateStackTrace(runtimeException));
                }
            } else if (state == StructuredTaskScope.Subtask.State.FAILED) {
                log.error(
                        "ai_rubric_audit_subtask_failed projectId={} url={} trace={}",
                        projectId,
                        entry.url,
                        truncateStackTrace(entry.subtask.exception()));
            } else {
                log.error(
                        "ai_rubric_audit_subtask_unavailable projectId={} url={} state={}",
                        projectId,
                        entry.url,
                        state);
            }
        }
        Map<String, MeoTrust> meoMap = fetchMeoTrustSafely(projectId, meoSearchQuery, normalizedUrls);
        LinkedHashMap<String, List<RubricAuditResult>> finalMap = new LinkedHashMap<>(aggregated.size());
        for (Map.Entry<String, List<RubricAuditResult>> entry : aggregated.entrySet()) {
            String url = entry.getKey();
            ArrayList<RubricAuditResult> withMeo = new ArrayList<>(entry.getValue().size() + 1);
            withMeo.addAll(entry.getValue());
            MeoTrust trust = meoMap.get(url);
            withMeo.add(buildMeoTrustResult(trust));
            finalMap.put(url, List.copyOf(withMeo));
        }
        appendThirdPartyMentionForSelf(finalMap, projectId, meoSearchQuery, normalizedSelfUrl);
        Map<String, List<RubricAuditResult>> result = Map.copyOf(finalMap);
        SelfAuditSnapshot capturedSelfSnapshot = selfSnapshot;
        try {
            self.saveAuditResults(auditHistoryId, normalizedSelfUrl, result);
        } catch (RuntimeException runtimeException) {
            log.error(
                    "ai_rubric_audit_persistence_failed projectId={} auditHistoryId={} trace={}",
                    projectId,
                    auditHistoryId,
                    truncateStackTrace(runtimeException));
        }
        return new MultiDomainAudit(result, capturedSelfSnapshot);
    }

    private void appendThirdPartyMentionForSelf(
            LinkedHashMap<String, List<RubricAuditResult>> finalMap,
            UUID projectId,
            String brandQuery,
            String normalizedSelfUrl) {
        if (normalizedSelfUrl == null || normalizedSelfUrl.isEmpty()) {
            return;
        }
        List<RubricAuditResult> selfRows = finalMap.get(normalizedSelfUrl);
        if (selfRows == null) {
            return;
        }
        RubricAuditResult row;
        try {
            // 第三者言及（権威・エンティティ認知の中核）は自社ブランド単位の測定で SerpAPI 1コール。
            // categoryKeyword による同名対策の本配線は別途（現状はブランド名クエリのみ）。
            ThirdPartyMentionMeasurementService.AuthorityMentionResult measured =
                    thirdPartyMentionMeasurementService.measure(projectId, brandQuery, null, normalizedSelfUrl);
            row = buildThirdPartyMentionResult(measured);
        } catch (RuntimeException runtimeException) {
            log.error(
                    "third_party_mention_measure_failed projectId={} trace={}",
                    projectId,
                    truncateStackTrace(runtimeException));
            return;
        }
        if (row == null) {
            return;
        }
        ArrayList<RubricAuditResult> merged = new ArrayList<>(selfRows.size() + 1);
        merged.addAll(selfRows);
        merged.add(row);
        finalMap.put(normalizedSelfUrl, List.copyOf(merged));
    }

    private static RubricAuditResult buildThirdPartyMentionResult(
            ThirdPartyMentionMeasurementService.AuthorityMentionResult measured) {
        if (measured == null) {
            return null;
        }
        double score = measured.authorityCoreScore();
        int distinct = measured.distinctThirdPartyDomainCount();
        RubricVerdictStatus verdict;
        if (score >= ThirdPartyMentionScorer.MAX_AUTHORITY_CORE) {
            verdict = RubricVerdictStatus.YES;
        } else if (score > 0.0d) {
            verdict = RubricVerdictStatus.PARTIAL;
        } else {
            verdict = RubricVerdictStatus.NO;
        }
        String evidence = "distinct_third_party_domains=" + distinct + "; authority_core=" + score;
        return new RubricAuditResult(RubricCriterionId.THIRD_PARTY_MENTIONS, verdict, evidence, score);
    }

    @Transactional
    public void saveAuditResults(
            UUID auditHistoryId, String selfUrl, Map<String, List<RubricAuditResult>> resultsByUrl) {
        if (auditHistoryId == null || resultsByUrl == null || resultsByUrl.isEmpty()) {
            return;
        }
        UUID workspaceId = TenantContextHolder.requireContext().tenantId();
        if (workspaceId == null) {
            throw new IllegalStateException("workspaceId");
        }
        String normalizedSelfUrl = selfUrl == null ? "" : selfUrl.trim();
        int total = 0;
        for (List<RubricAuditResult> list : resultsByUrl.values()) {
            total += list.size();
        }
        ArrayList<AuditRubricResultEntity> entities = new ArrayList<>(total);
        for (Map.Entry<String, List<RubricAuditResult>> entry : resultsByUrl.entrySet()) {
            String url = entry.getKey();
            boolean self = !normalizedSelfUrl.isEmpty() && normalizedSelfUrl.equals(url);
            List<RubricAuditResult> list = entry.getValue();
            for (int i = 0; i < list.size(); i++) {
                RubricAuditResult result = list.get(i);
                AuditRubricResultEntity entity = new AuditRubricResultEntity();
                entity.setWorkspaceId(workspaceId);
                entity.setAuditHistoryId(auditHistoryId);
                entity.setTargetUrl(url);
                entity.setSelf(self);
                entity.setCriterionId(result.criterionId().name());
                entity.setVerdict(result.verdict().name());
                entity.setEvidence(result.evidence());
                entity.setScore(BigDecimal.valueOf(result.score()).setScale(3, RoundingMode.HALF_EVEN));
                entities.add(entity);
            }
        }
        auditRubricResultRepository.saveAll(entities);
    }

    private DomainAudit auditOneDomain(UUID projectId, String url, String jobContextBlock) {
        try {
            DomainDeepAuditContext bundle = smartDomainCrawlService.compileForAudit(url);
            ArrayList<RubricAuditResult> results = new ArrayList<>(RubricCriterionId.values().length);
            com.geo.analytics.application.dto.RubricAuditResult llmAudit =
                    rubricAuditService.executeAudit(projectId, bundle.mergedAuditText(), jobContextBlock);
            appendLlmAudits(llmAudit, results);
            appendSystemAudits(url, bundle.primaryPage().crawled(), results);
            return new DomainAudit(List.copyOf(results), bundle.primaryPage().crawled(), llmAudit);
        } catch (RuntimeException runtimeException) {
            log.error(
                    "ai_rubric_audit_one_domain_failed projectId={} url={} trace={}",
                    projectId,
                    url,
                    truncateStackTrace(runtimeException));
            throw runtimeException;
        }
    }

    private void appendLlmAudits(
            com.geo.analytics.application.dto.RubricAuditResult llmAudit,
            ArrayList<RubricAuditResult> sink) {
        List<RubricItemAudit> items = llmAudit.items();
        for (int i = 0; i < items.size(); i++) {
            RubricItemAudit item = items.get(i);
            sink.add(new RubricAuditResult(
                    item.criterionId(),
                    item.status(),
                    item.evidence(),
                    computeScore(item.criterionId(), item.status())));
        }
    }

    private void appendSystemAudits(String url, CrawledPageData crawl, ArrayList<RubricAuditResult> sink) {
        boolean jsonLd = crawl != null && crawl.hasJsonLdSignal();
        boolean headings = crawl != null && crawl.headingHierarchyOk();
        LlmsTxtProbe llmsTxtProbe = probeLlmsTxt(url);
        sink.add(buildMachineReadabilitySignal(jsonLd, headings, llmsTxtProbe, crawl));
    }

    private RubricAuditResult buildMachineReadabilitySignal(
            boolean jsonLd, boolean headings, LlmsTxtProbe llmsTxtProbe, CrawledPageData crawl) {
        int matchCount = (jsonLd ? 1 : 0) + (headings ? 1 : 0) + (llmsTxtProbe.available ? 1 : 0);
        RubricVerdictStatus verdict;
        if (matchCount == 3) {
            verdict = RubricVerdictStatus.YES;
        } else if (matchCount == 2) {
            verdict = RubricVerdictStatus.PARTIAL;
        } else {
            verdict = RubricVerdictStatus.NO;
        }
        StringBuilder evidence = new StringBuilder();
        evidence.append("json_ld=").append(jsonLd);
        evidence.append("; heading_hierarchy=").append(headings);
        evidence.append("; llms_txt=").append(llmsTxtProbe.available);
        if (crawl != null && crawl.seoTechnicalEvidenceSummary() != null && !crawl.seoTechnicalEvidenceSummary().isBlank()) {
            evidence.append("; seo_evidence=").append(crawl.seoTechnicalEvidenceSummary().strip());
        }
        if (!llmsTxtProbe.available) {
            evidence.append(" (").append(llmsTxtProbe.failureEvidence).append(")");
        }
        if (verdict != RubricVerdictStatus.YES) {
            evidence.append("; geo_potential_gap=true");
        }
        return new RubricAuditResult(
                RubricCriterionId.MACHINE_READABILITY_SIGNAL,
                verdict,
                evidence.toString(),
                computeScore(RubricCriterionId.MACHINE_READABILITY_SIGNAL, verdict));
    }

    private LlmsTxtProbe probeLlmsTxt(String pageUrl) {
        URI base = parseBaseUri(pageUrl);
        if (base == null) {
            return new LlmsTxtProbe(false, "base url unparsable");
        }
        URI target;
        try {
            target = new URI(base.getScheme(), base.getAuthority(), "/llms.txt", null, null);
        } catch (Exception parseException) {
            return new LlmsTxtProbe(false, "target uri build failed");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(target)
                    .timeout(LLMS_TXT_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<Void> response = safeHttpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int code = response.statusCode();
            if (code >= 200 && code < 300) {
                return new LlmsTxtProbe(true, "");
            }
            if (code == 404) {
                return new LlmsTxtProbe(false, "404 not found");
            }
            return new LlmsTxtProbe(false, "status=" + code);
        } catch (RuntimeException runtimeException) {
            log.error(
                    "ai_rubric_audit_llms_txt_failed url={} trace={}",
                    pageUrl,
                    truncateStackTrace(runtimeException));
            return new LlmsTxtProbe(false, "fetch failed");
        }
    }

    private Map<String, MeoTrust> fetchMeoTrustSafely(UUID projectId, String meoSearchQuery, List<String> urls) {
        try {
            Map<String, MeoTrust> raw = meoDataPort.fetchTrustForArea(projectId, meoSearchQuery, urls);
            if (raw == null) {
                return Map.of();
            }
            return raw;
        } catch (RuntimeException runtimeException) {
            log.error(
                    "ai_rubric_audit_meo_fetch_failed projectId={} trace={}",
                    projectId,
                    truncateStackTrace(runtimeException));
            return Map.of();
        }
    }

    private static RubricAuditResult buildMeoTrustResult(MeoTrust trust) {
        int reviews = trust != null ? trust.userRatingsTotal() : 0;
        double stars = trust != null ? trust.averageStars() : 0.0d;
        double score = computeMeoTrustScore(reviews);
        RubricVerdictStatus verdict;
        if (reviews >= (int) MEO_MAX_REVIEWS) {
            verdict = RubricVerdictStatus.YES;
        } else if (reviews > 0) {
            verdict = RubricVerdictStatus.PARTIAL;
        } else {
            verdict = RubricVerdictStatus.NO;
        }
        StringBuilder evidence = new StringBuilder();
        evidence.append("user_ratings_total=").append(reviews);
        evidence.append("; average_stars=").append(stars);
        evidence.append("; max_score_reached_at=").append((int) MEO_MAX_REVIEWS);
        if (trust == null) {
            evidence.append("; data_source=missing");
        }
        return new RubricAuditResult(RubricCriterionId.MEO_TRUST_SCORE, verdict, evidence.toString(), score);
    }

    private static double computeMeoTrustScore(int userRatingsTotal) {
        int safe = userRatingsTotal > 0 ? userRatingsTotal : 0;
        double ratio = StrictMath.fma((double) safe, MEO_INV_REVIEWS, 0.0d);
        double scaled = StrictMath.fma(ratio, MEO_MAX_SCORE, 0.0d);
        return StrictMath.max(0.0d, StrictMath.min(MEO_MAX_SCORE, scaled));
    }

    private static URI parseBaseUri(String pageUrl) {
        try {
            URI parsed = URI.create(pageUrl.trim());
            if (parsed.getScheme() == null || parsed.getAuthority() == null) {
                return null;
            }
            return parsed;
        } catch (RuntimeException runtimeException) {
            return null;
        }
    }

    private static double computeScore(RubricCriterionId criterionId, RubricVerdictStatus verdict) {
        double max = criterionId.maxScore();
        if (verdict == RubricVerdictStatus.YES) {
            return max;
        }
        if (verdict == RubricVerdictStatus.PARTIAL) {
            return StrictMath.fma(max, PARTIAL_RATIO, 0.0d);
        }
        return 0.0d;
    }

    private static String truncateStackTrace(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        String full = stringWriter.toString();
        if (full.length() <= STACK_TRACE_LIMIT) {
            return full;
        }
        return full.substring(0, STACK_TRACE_LIMIT);
    }

    private record DomainSubtask(String url, StructuredTaskScope.Subtask<DomainAudit> subtask) {}

    private record LlmsTxtProbe(boolean available, String failureEvidence) {}
}
