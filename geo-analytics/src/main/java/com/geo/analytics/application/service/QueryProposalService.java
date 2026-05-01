package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.DomainAnalysisResult;
import com.geo.analytics.application.dto.UserStrategicKnowledge;
import com.geo.analytics.application.exception.QueryProposalException;
import com.geo.analytics.application.exception.QueryProposalPhase;
import com.geo.analytics.application.validator.QueryQualityValidator;
import com.geo.analytics.domain.entity.DomainAnalysisSnapshotEntity;
import com.geo.analytics.domain.exception.ScrapingException;
import com.geo.analytics.domain.port.UrlContentFetcher;
import com.geo.analytics.domain.service.DomainAnalysisAiService;
import com.geo.analytics.infrastructure.repository.DomainAnalysisSnapshotRepository;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * URL 由来の本文とユーザーの戦略知識を結合し、GEO 向けのペルソナ・クエリ案を AI に生成させるオーケストレーター。
 */
@Service
public class QueryProposalService {

    private static final Logger log = LoggerFactory.getLogger(QueryProposalService.class);

    private static final String MDC_PHASE_KEY = "queryProposal.phase";

    private static final Pattern UNEXPECTED_HTTP_STATUS = Pattern.compile(
            "Unexpected HTTP status:\\s*(\\d{3})", Pattern.CASE_INSENSITIVE);

    private final UrlContentFetcher urlContentFetcher;

    private final DomainAnalysisAiService domainAnalysisAiService;

    private final QueryQualityValidator queryQualityValidator;

    private final DomainAnalysisSnapshotRepository domainAnalysisSnapshotRepository;

    public QueryProposalService(
            UrlContentFetcher urlContentFetcher,
            DomainAnalysisAiService domainAnalysisAiService,
            QueryQualityValidator queryQualityValidator,
            DomainAnalysisSnapshotRepository domainAnalysisSnapshotRepository) {
        this.urlContentFetcher = Objects.requireNonNull(urlContentFetcher, "urlContentFetcher");
        this.domainAnalysisAiService = Objects.requireNonNull(domainAnalysisAiService, "domainAnalysisAiService");
        this.queryQualityValidator = Objects.requireNonNull(queryQualityValidator, "queryQualityValidator");
        this.domainAnalysisSnapshotRepository =
                Objects.requireNonNull(domainAnalysisSnapshotRepository, "domainAnalysisSnapshotRepository");
    }

    @Transactional
    public DomainAnalysisResult propose(String url, UserStrategicKnowledge knowledge) {
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

            return result;
        } finally {
            MDC.remove(MDC_PHASE_KEY);
        }
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
