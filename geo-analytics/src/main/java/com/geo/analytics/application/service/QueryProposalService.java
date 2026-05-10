package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.ConvertProposalToJobOutcome;
import com.geo.analytics.application.dto.DomainAnalysisResult;
import com.geo.analytics.application.dto.QueryProposalProposeOutcome;
import com.geo.analytics.application.dto.SuggestedQuery;
import com.geo.analytics.application.dto.UserStrategicKnowledge;
import com.geo.analytics.application.exception.QueryProposalException;
import com.geo.analytics.application.exception.QueryProposalPhase;
import com.geo.analytics.application.validator.QueryQualityValidator;
import com.geo.analytics.domain.entity.DomainAnalysisSnapshotEntity;
import com.geo.analytics.domain.entity.QueryProposalEntity;
import com.geo.analytics.domain.entity.QueryProposalSuggestedQueryEntity;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.exception.ScrapingException;
import com.geo.analytics.domain.port.UrlContentFetcher;
import com.geo.analytics.domain.service.DomainAnalysisAiService;
import com.geo.analytics.infrastructure.repository.DomainAnalysisSnapshotRepository;
import com.geo.analytics.infrastructure.repository.QueryProposalRepository;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * URL 由来の本文とユーザーの戦略知識を結合し、GEO 向けのペルソナ・クエリ案を AI に生成させるオーケストレーター。
 */
@Service
public class QueryProposalService {

    private static final Logger log = LoggerFactory.getLogger(QueryProposalService.class);

    private static final String MDC_PHASE_KEY = "queryProposal.phase";

    private static final int BRAND_NAME_MAX_LENGTH = 255;

    private static final String AI_PERSONA_DIAGNOSTIC_PREFIX = "【AI提案ペルソナ】\n";

    private static final Pattern UNEXPECTED_HTTP_STATUS = Pattern.compile(
            "Unexpected HTTP status:\\s*(\\d{3})", Pattern.CASE_INSENSITIVE);

    private final UrlContentFetcher urlContentFetcher;

    private final DomainAnalysisAiService domainAnalysisAiService;

    private final QueryQualityValidator queryQualityValidator;

    private final DomainAnalysisSnapshotRepository domainAnalysisSnapshotRepository;

    private final QueryProposalRepository queryProposalRepository;

    private final JobPersistenceService jobPersistenceService;

    private final JobQuerySubmissionService jobQuerySubmissionService;

    private final EntityManager entityManager;

    private final QueryProposalService self;

    public QueryProposalService(
            UrlContentFetcher urlContentFetcher,
            DomainAnalysisAiService domainAnalysisAiService,
            QueryQualityValidator queryQualityValidator,
            DomainAnalysisSnapshotRepository domainAnalysisSnapshotRepository,
            QueryProposalRepository queryProposalRepository,
            JobPersistenceService jobPersistenceService,
            JobQuerySubmissionService jobQuerySubmissionService,
            EntityManager entityManager,
            @Lazy QueryProposalService self) {
        this.urlContentFetcher = Objects.requireNonNull(urlContentFetcher, "urlContentFetcher");
        this.domainAnalysisAiService = Objects.requireNonNull(domainAnalysisAiService, "domainAnalysisAiService");
        this.queryQualityValidator = Objects.requireNonNull(queryQualityValidator, "queryQualityValidator");
        this.domainAnalysisSnapshotRepository =
                Objects.requireNonNull(domainAnalysisSnapshotRepository, "domainAnalysisSnapshotRepository");
        this.queryProposalRepository = Objects.requireNonNull(queryProposalRepository, "queryProposalRepository");
        this.jobPersistenceService = Objects.requireNonNull(jobPersistenceService, "jobPersistenceService");
        this.jobQuerySubmissionService = Objects.requireNonNull(jobQuerySubmissionService, "jobQuerySubmissionService");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
        this.self = Objects.requireNonNull(self, "self");
    }

    @Transactional
    public DomainAnalysisResult propose(String url, UserStrategicKnowledge knowledge) {
        return completeProposal(url, knowledge).analysis();
    }

    @Transactional
    public QueryProposalProposeOutcome completeProposal(String url, UserStrategicKnowledge knowledge) {
        Objects.requireNonNull(knowledge, "knowledge");
        try {
            MDC.put(MDC_PHASE_KEY, QueryProposalPhase.SCRAPING.name());
            log.atInfo()
                    .addKeyValue("hostHint", safeHostHint(url))
                    .log("Query proposal phase={}", QueryProposalPhase.SCRAPING.name());

            String scrapedText = fetchCleanTextWithOptionalRetry(url);

            MDC.put(MDC_PHASE_KEY, QueryProposalPhase.AI_ANALYSIS.name());
            log.atInfo()
                    .addKeyValue("hostHint", safeHostHint(url))
                    .log("Query proposal phase={}", QueryProposalPhase.AI_ANALYSIS.name());

            DomainAnalysisResult result;
            try {
                result = domainAnalysisAiService.analyze(
                        knowledge.businessDescription(),
                        knowledge.targetAudience(),
                        knowledge.strategicFocus(),
                        scrapedText);
            } catch (RuntimeException e) {
                log.error(
                        "AI analysis failed hostHint={} exceptionClass={} message={}",
                        safeHostHint(url),
                        e.getClass().getName(),
                        e.getMessage(),
                        e);
                throw new QueryProposalException(QueryProposalPhase.AI_ANALYSIS, "AI analysis failed", e);
            }

            MDC.put(MDC_PHASE_KEY, QueryProposalPhase.VALIDATION.name());
            log.atInfo()
                    .addKeyValue("hostHint", safeHostHint(url))
                    .log("Query proposal phase={}", QueryProposalPhase.VALIDATION.name());

            try {
                queryQualityValidator.validate(result);
            } catch (QueryProposalException e) {
                if (e.getPhase() == QueryProposalPhase.VALIDATION) {
                    log.warn(
                            "Query validation failed hostHint={} detail={}",
                            safeHostHint(url),
                            e.getDetail());
                }
                throw e;
            }

            UUID workspaceId = TenantContextHolder.requireContext().tenantId();
            DomainAnalysisSnapshotEntity snapshot = new DomainAnalysisSnapshotEntity();
            snapshot.setWorkspaceId(workspaceId);
            snapshot.setSourceUrl(url);
            snapshot.setInferredPersona(result.inferredPersona());
            snapshot.setQueries(new ArrayList<>(result.queries()));
            domainAnalysisSnapshotRepository.save(snapshot);

            QueryProposalEntity proposalEntity = new QueryProposalEntity();
            proposalEntity.setWorkspaceId(workspaceId);
            proposalEntity.setUrl(url);
            proposalEntity.setBusinessDescription(knowledge.businessDescription());
            proposalEntity.setTargetAudience(knowledge.targetAudience());
            proposalEntity.setStrategicFocus(knowledge.strategicFocus());
            proposalEntity.setInferredPersona(result.inferredPersona());
            List<QueryProposalSuggestedQueryEntity> rows = new ArrayList<>();
            List<SuggestedQuery> queries = result.queries();
            for (int i = 0; i < queries.size(); i++) {
                SuggestedQuery sq = queries.get(i);
                QueryProposalSuggestedQueryEntity row = new QueryProposalSuggestedQueryEntity();
                row.setProposal(proposalEntity);
                row.setQueryText(sq.queryText());
                row.setIntent(sq.intent());
                row.setSortOrder(i);
                rows.add(row);
            }
            proposalEntity.setSuggestedQueries(rows);
            QueryProposalEntity saved = queryProposalRepository.save(proposalEntity);

            return new QueryProposalProposeOutcome(result, saved.getId());
        } finally {
            MDC.remove(MDC_PHASE_KEY);
        }
    }

    /**
     * 保存済みクエリ提案からジョブを生成し、新規作成時のみ提案クエリを登録して解析パイプラインへ載せる。
     *
     * @return ジョブ ID と、その呼び出しで新規ジョブが作られたかどうか
     */
    public ConvertProposalToJobOutcome convertProposalToJob(UUID proposalId, SubscriptionPlan plan) {
        Objects.requireNonNull(proposalId, "proposalId");
        Objects.requireNonNull(plan, "plan");
        QueryProposalEntity proposal = self.loadProposalForJob(proposalId);
        List<QueryProposalSuggestedQueryEntity> suggested = proposal.getSuggestedQueries();
        if (suggested == null || suggested.isEmpty()) {
            throw new IllegalStateException("Query proposal has no suggested queries: " + proposalId);
        }
        List<String> queryTexts =
                suggested.stream().map(QueryProposalSuggestedQueryEntity::getQueryText).collect(Collectors.toList());
        if (queryTexts.stream().anyMatch(t -> t == null || t.isBlank())) {
            throw new IllegalStateException("Query proposal contains blank query text: " + proposalId);
        }
        String brandName = normalizeBrandName(brandNameFromProposalUrl(proposal.getUrl()));
        String proposalTargetUrl = proposal.getUrl();
        if (proposalTargetUrl == null || proposalTargetUrl.isBlank()) {
            proposalTargetUrl = "https://placeholder.invalid/query-proposal";
        }
        UUID proposalWorkspaceId = proposal.getWorkspaceId();
        var proposalFields =
                new JobPersistenceService.JobCreateFields(brandName, proposalTargetUrl, null, null, null);
        var outcome = jobPersistenceService.createJobWithIdempotency(proposalFields, proposalId, proposalWorkspaceId);
        var job = outcome.jobEntity();
        UUID jobId = job.getId();
        if (outcome.created()) {
            String diagnostic = buildPersonaDiagnosticBlock(proposal);
            jobPersistenceService.updateJobStrategyRollup(jobId, diagnostic, List.of());
            jobQuerySubmissionService.submitQueries(jobId, queryTexts, plan);
        }
        return new ConvertProposalToJobOutcome(jobId, outcome.created());
    }

    @Transactional(readOnly = true)
    public QueryProposalEntity loadProposalForJob(UUID proposalId) {
        String tenantId = TenantContextHolder.requireContext().tenantId().toString();
        List<QueryProposalEntity> list = entityManager
                .createQuery(
                        "SELECT DISTINCT p FROM QueryProposalEntity p LEFT JOIN FETCH p.suggestedQueries WHERE p.id = :id AND p.tenantId = :tid",
                        QueryProposalEntity.class)
                .setParameter("id", proposalId)
                .setParameter("tid", tenantId)
                .getResultList();
        if (list.isEmpty()) {
            throw new EntityNotFoundException("Query proposal not found: " + proposalId);
        }
        return list.get(0);
    }

    private static String brandNameFromProposalUrl(String url) {
        if (url == null || url.isBlank()) {
            return "query-proposal";
        }
        try {
            URI uri = new URI(url.trim());
            String host = uri.getHost();
            if (host != null && !host.isBlank()) {
                return host;
            }
        } catch (URISyntaxException e) {
            log.debug("brandNameFromProposalUrl parse failed url={}", truncate(url, 64));
        }
        return truncate(url.trim(), BRAND_NAME_MAX_LENGTH);
    }

    private static String normalizeBrandName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "query-proposal";
        }
        String s = raw.trim();
        if (s.length() > BRAND_NAME_MAX_LENGTH) {
            return s.substring(0, BRAND_NAME_MAX_LENGTH);
        }
        return s;
    }

    private static String buildPersonaDiagnosticBlock(QueryProposalEntity proposal) {
        String persona = proposal.getInferredPersona() != null ? proposal.getInferredPersona().trim() : "";
        StringBuilder sb = new StringBuilder(AI_PERSONA_DIAGNOSTIC_PREFIX);
        if (!persona.isEmpty()) {
            sb.append(persona);
        }
        List<QueryProposalSuggestedQueryEntity> rows = proposal.getSuggestedQueries();
        if (rows != null && !rows.isEmpty()) {
            sb.append("\n\n【提案クエリと意図】\n");
            for (QueryProposalSuggestedQueryEntity row : rows) {
                String q = row.getQueryText() != null ? row.getQueryText().trim() : "";
                String intent = row.getIntent() != null ? row.getIntent().trim() : "";
                sb.append("- ").append(q);
                if (!intent.isEmpty()) {
                    sb.append(" （意図: ").append(intent).append('）');
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private String fetchCleanTextWithOptionalRetry(String url) {
        try {
            return urlContentFetcher.fetchCleanText(url);
        } catch (ScrapingException first) {
            if (!isRetryableScrapingException(first)) {
                throw new QueryProposalException(
                        QueryProposalPhase.SCRAPING, "Failed to fetch URL content", first);
            }
            log.warn(
                    "Scraping failed; retrying once hostHint={} exceptionClass={} message={}",
                    safeHostHint(url),
                    first.getClass().getName(),
                    first.getMessage(),
                    first);
            try {
                return urlContentFetcher.fetchCleanText(url);
            } catch (ScrapingException second) {
                throw new QueryProposalException(
                        QueryProposalPhase.SCRAPING, "Failed to fetch URL content after retry", second);
            }
        }
    }

    private static boolean isRetryableScrapingException(ScrapingException e) {
        if (isDefinitiveScrapingFailure(e)) {
            return false;
        }
        if (hasIOExceptionInCauseChain(e)) {
            return true;
        }
        return impliesTransientHttpFailure(e.getMessage());
    }

    private static boolean isDefinitiveScrapingFailure(ScrapingException e) {
        String msg = e.getMessage();
        if (msg == null) {
            return false;
        }
        String m = msg.toLowerCase(Locale.ROOT);
        if (m.contains("private ip")
                || m.contains("invalid scheme")
                || m.contains("user info")
                || m.contains("invalid url syntax")
                || m.contains("host is required")
                || m.contains("invalid host")
                || m.contains("too many redirects")
                || m.contains("url must not be null")
                || m.contains("redirect location")) {
            return true;
        }
        Integer status = extractUnexpectedHttpStatus(msg);
        if (status != null && status >= 400 && status < 500) {
            return true;
        }
        return false;
    }

    private static boolean impliesTransientHttpFailure(String rawMessage) {
        if (rawMessage == null) {
            return false;
        }
        String m = rawMessage.toLowerCase(Locale.ROOT);
        if (m.contains("502") || m.contains("503") || m.contains("504")) {
            return true;
        }
        Integer status = extractUnexpectedHttpStatus(rawMessage);
        return status != null && status >= 500 && status < 600;
    }

    private static Integer extractUnexpectedHttpStatus(String message) {
        Matcher matcher = UNEXPECTED_HTTP_STATUS.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean hasIOExceptionInCauseChain(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof IOException) {
                return true;
            }
        }
        return false;
    }

    private static String safeHostHint(String url) {
        if (url == null) {
            return "(null)";
        }
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            return host != null ? host : truncate(url, 64);
        } catch (URISyntaxException e) {
            return truncate(url, 64);
        }
    }

    private static String truncate(String s, int maxChars) {
        if (s.length() <= maxChars) {
            return s;
        }
        return s.substring(0, maxChars) + "...";
    }
}
