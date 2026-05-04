package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.ExtractedPlace;
import com.geo.analytics.application.dto.SelectedCompetitor;
import com.geo.analytics.application.dto.TargetAttributes;
import com.geo.analytics.domain.enums.IndustryType;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class HybridCompetitorPipelineService {

    private final TargetAttributesInferenceService targetAttributesInferenceService;
    private final PlacesSearchService placesSearchService;
    private final CompetitorFilterService competitorFilterService;

    public HybridCompetitorPipelineService(
            TargetAttributesInferenceService targetAttributesInferenceService,
            PlacesSearchService placesSearchService,
            CompetitorFilterService competitorFilterService) {
        this.targetAttributesInferenceService = targetAttributesInferenceService;
        this.placesSearchService = placesSearchService;
        this.competitorFilterService = competitorFilterService;
    }

    public List<SelectedCompetitor> executePipeline(UUID jobId, UUID projectId, String targetUrl) {
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
        List<ExtractedPlace> places = List.of();
        try {
            String textQuery = tradeAreaLabel + " " + industry.getLabel();
            places = placesSearchService.search(projectId, textQuery);
        } catch (Throwable throwable) {
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
