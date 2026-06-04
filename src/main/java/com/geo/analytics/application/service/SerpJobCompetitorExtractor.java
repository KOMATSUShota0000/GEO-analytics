package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.CompetitorExtractionContext;
import com.geo.analytics.application.dto.SelectedCompetitor;
import com.geo.analytics.application.dto.TargetAttributes;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.enums.IndustryType;
import com.geo.analytics.domain.enums.CompetitorExtractionMode;
import com.geo.analytics.infrastructure.api.dto.SerpOrganicResult;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class SerpJobCompetitorExtractor {

    private final JobPersistenceService jobPersistenceService;
    private final TargetAttributesInferenceService targetAttributesInferenceService;
    private final GeoCompetitorSearchService geoCompetitorSearchService;
    private final CompetitorFilterService competitorFilterService;
    private final SyntheticSelectedCompetitorFactory syntheticSelectedCompetitorFactory;

    public SerpJobCompetitorExtractor(
            JobPersistenceService jobPersistenceService,
            TargetAttributesInferenceService targetAttributesInferenceService,
            GeoCompetitorSearchService geoCompetitorSearchService,
            CompetitorFilterService competitorFilterService,
            SyntheticSelectedCompetitorFactory syntheticSelectedCompetitorFactory) {
        this.jobPersistenceService = jobPersistenceService;
        this.targetAttributesInferenceService = targetAttributesInferenceService;
        this.geoCompetitorSearchService = geoCompetitorSearchService;
        this.competitorFilterService = competitorFilterService;
        this.syntheticSelectedCompetitorFactory = syntheticSelectedCompetitorFactory;
    }

    public List<SelectedCompetitor> extract(CompetitorExtractionContext ctx, CompetitorExtractionMode serpProfile) {
        UUID jobId = ctx.jobId();
        UUID projectId = ctx.projectId();
        String targetUrl = ctx.targetUrl();
        Objects.requireNonNull(jobId, "jobId");
        if (projectId == null) {
            return syntheticSelectedCompetitorFactory.threeShortReasoningPlaceholders(
                    IndustryType.OTHER, "全国", SyntheticSelectedCompetitorFactory.SyntheticPadReason.NO_CANDIDATES);
        }
        JobEntity job = jobPersistenceService.findJobById(jobId);
        String brandName = job.getBrandName() != null ? job.getBrandName() : "";
        IndustryType industry = IndustryType.OTHER;
        String tradeAreaLabel = "全国";
        String categoryKeyword = "";
        try {
            TargetAttributes attrs =
                    targetAttributesInferenceService.infer(projectId, targetUrl != null ? targetUrl : "");
            if (attrs != null) {
                if (attrs.industry() != null) {
                    industry = attrs.industry();
                }
                if (attrs.tradeAreaLabel() != null && !attrs.tradeAreaLabel().isBlank()) {
                    tradeAreaLabel = attrs.tradeAreaLabel().trim();
                }
                if (attrs.categoryKeyword() != null && !attrs.categoryKeyword().isBlank()) {
                    categoryKeyword = attrs.categoryKeyword().trim();
                }
            }
        } catch (Throwable throwable) {
        }
        // 具体的職種ワードを最優先。無ければ業種の検索ラベル→粗いラベルへフォールバック。
        String primary;
        if (!categoryKeyword.isEmpty()) {
            primary = categoryKeyword;
        } else if (industry.getSearchLabel() != null && !industry.getSearchLabel().isBlank()) {
            primary = industry.getSearchLabel().trim();
        } else {
            primary = industry.getLabel();
        }
        String summaryHint = "";
        if (job.getBusinessSummary() != null && !job.getBusinessSummary().isBlank()) {
            String s = job.getBusinessSummary().strip();
            summaryHint = s.length() > 120 ? s.substring(0, 120) : s;
        }
        String searchQuery = buildQuery(serpProfile, tradeAreaLabel, primary, brandName, summaryHint);
        List<SerpOrganicResult> organic = geoCompetitorSearchService.searchOrganic(projectId, searchQuery);
        String effectiveTargetUrl = targetUrl != null && !targetUrl.isBlank() ? targetUrl : job.getTargetUrl();
        return competitorFilterService.filterFromSerpOrganic(
                projectId,
                industry,
                tradeAreaLabel,
                brandName,
                effectiveTargetUrl != null ? effectiveTargetUrl : "",
                organic,
                serpProfile);
    }

    private static String buildQuery(
            CompetitorExtractionMode mode,
            String tradeAreaLabel,
            String primary,
            String brandName,
            String summaryHint) {
        String area = tradeAreaLabel != null && !tradeAreaLabel.isBlank() ? tradeAreaLabel.trim() : "日本";
        StringBuilder sb = new StringBuilder();
        sb.append(area).append(" ").append(primary);
        if (brandName != null && !brandName.isBlank()) {
            sb.append(" ").append(brandName.strip());
        }
        if (summaryHint != null && !summaryHint.isBlank()) {
            sb.append(" ").append(summaryHint);
        }
        if (mode == CompetitorExtractionMode.ONLINE_SERVICE) {
            sb.append(" 通販 EC 公式");
        } else {
            sb.append(" 法人向け サービス 企業");
        }
        return sb.toString().trim();
    }
}
