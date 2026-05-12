package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.CompetitorExtractionContext;
import com.geo.analytics.application.dto.SelectedCompetitor;
import com.geo.analytics.application.dto.TargetAttributes;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.enums.IndustryType;
import com.geo.analytics.domain.enums.CompetitorExtractionMode;
import com.geo.analytics.infrastructure.api.dto.SerpOrganicResult;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class SerpJobCompetitorExtractor {

    private final JobPersistenceService jobPersistenceService;
    private final TargetAttributesInferenceService targetAttributesInferenceService;
    private final SerpOrganicSearchService serpOrganicSearchService;
    private final CompetitorFilterService competitorFilterService;

    public SerpJobCompetitorExtractor(
            JobPersistenceService jobPersistenceService,
            TargetAttributesInferenceService targetAttributesInferenceService,
            SerpOrganicSearchService serpOrganicSearchService,
            CompetitorFilterService competitorFilterService) {
        this.jobPersistenceService = jobPersistenceService;
        this.targetAttributesInferenceService = targetAttributesInferenceService;
        this.serpOrganicSearchService = serpOrganicSearchService;
        this.competitorFilterService = competitorFilterService;
    }

    public List<SelectedCompetitor> extract(CompetitorExtractionContext ctx, CompetitorExtractionMode serpProfile) {
        UUID jobId = ctx.jobId();
        UUID projectId = ctx.projectId();
        String targetUrl = ctx.targetUrl();
        Objects.requireNonNull(jobId, "jobId");
        if (projectId == null) {
            return fallbackThreeSynthetic();
        }
        JobEntity job = jobPersistenceService.findJobById(jobId);
        String brandName = job.getBrandName() != null ? job.getBrandName() : "";
        IndustryType industry = IndustryType.OTHER;
        String tradeAreaLabel = "全国";
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
            }
        } catch (Throwable throwable) {
        }
        String primary =
                industry.getSearchLabel() != null && !industry.getSearchLabel().isBlank()
                        ? industry.getSearchLabel().trim()
                        : industry.getLabel();
        String summaryHint = "";
        if (job.getBusinessSummary() != null && !job.getBusinessSummary().isBlank()) {
            String s = job.getBusinessSummary().strip();
            summaryHint = s.length() > 120 ? s.substring(0, 120) : s;
        }
        String searchQuery = buildQuery(serpProfile, tradeAreaLabel, primary, brandName, summaryHint);
        List<SerpOrganicResult> organic = serpOrganicSearchService.searchOrganic(projectId, searchQuery);
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

    private static List<SelectedCompetitor> fallbackThreeSynthetic() {
        IndustryType ind = IndustryType.OTHER;
        String area = "全国";
        String label = ind.getLabel();
        List<SelectedCompetitor> list = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String suffix = String.valueOf((char) ('A' + i));
            String name = area + "における" + label + "の標準モデル競合" + suffix;
            String reasoning =
                    area + "および" + label + "に整合するGEO Readiness評価用の参照モデルとして配置した。";
            list.add(new SelectedCompetitor(name, null, null, null, reasoning, true));
        }
        return List.copyOf(list);
    }
}
