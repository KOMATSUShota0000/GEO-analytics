package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.CompetitorExtractionContext;
import com.geo.analytics.application.dto.ExtractedPlace;
import com.geo.analytics.application.dto.SelectedCompetitor;
import com.geo.analytics.application.dto.TargetAttributes;
import com.geo.analytics.domain.enums.IndustryType;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class LocalStoreStrategy implements CompetitorExtractionStrategy {

    private final TargetAttributesInferenceService targetAttributesInferenceService;
    private final LocalStoreRippleSearch localStoreRippleSearch;
    private final CompetitorFilterService competitorFilterService;

    public LocalStoreStrategy(
            TargetAttributesInferenceService targetAttributesInferenceService,
            LocalStoreRippleSearch localStoreRippleSearch,
            CompetitorFilterService competitorFilterService) {
        this.targetAttributesInferenceService = targetAttributesInferenceService;
        this.localStoreRippleSearch = localStoreRippleSearch;
        this.competitorFilterService = competitorFilterService;
    }

    @Override
    public List<SelectedCompetitor> extract(CompetitorExtractionContext ctx) {
        UUID jobId = ctx.jobId();
        UUID projectId = ctx.projectId();
        String targetUrl = ctx.targetUrl();
        Objects.requireNonNull(jobId, "jobId");
        if (projectId == null) {
            return fallbackThreeSynthetic();
        }
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
        List<ExtractedPlace> places = localStoreRippleSearch.collectMergedPlaces(projectId, tradeAreaLabel, industry);
        if (places.isEmpty()) {
            return fallbackThreeSynthetic();
        }
        try {
            return competitorFilterService.filter(projectId, industry, tradeAreaLabel, places);
        } catch (Throwable throwable) {
            return competitorFilterService.filter(projectId, industry, tradeAreaLabel, List.of());
        }
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
