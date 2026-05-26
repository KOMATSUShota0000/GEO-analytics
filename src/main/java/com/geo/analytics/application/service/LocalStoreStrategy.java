package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.CompetitorExtractionContext;
import com.geo.analytics.application.dto.ExtractedPlace;
import com.geo.analytics.application.dto.SelectedCompetitor;
import com.geo.analytics.application.dto.TargetAttributes;
import com.geo.analytics.domain.enums.IndustryType;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class LocalStoreStrategy implements CompetitorExtractionStrategy {

    private final TargetAttributesInferenceService targetAttributesInferenceService;
    private final LocalStoreRippleSearch localStoreRippleSearch;
    private final CompetitorFilterService competitorFilterService;
    private final SyntheticSelectedCompetitorFactory syntheticSelectedCompetitorFactory;

    public LocalStoreStrategy(
            TargetAttributesInferenceService targetAttributesInferenceService,
            LocalStoreRippleSearch localStoreRippleSearch,
            CompetitorFilterService competitorFilterService,
            SyntheticSelectedCompetitorFactory syntheticSelectedCompetitorFactory) {
        this.targetAttributesInferenceService = targetAttributesInferenceService;
        this.localStoreRippleSearch = localStoreRippleSearch;
        this.competitorFilterService = competitorFilterService;
        this.syntheticSelectedCompetitorFactory = syntheticSelectedCompetitorFactory;
    }

    @Override
    public List<SelectedCompetitor> extract(CompetitorExtractionContext ctx) {
        UUID jobId = ctx.jobId();
        UUID projectId = ctx.projectId();
        String targetUrl = ctx.targetUrl();
        Objects.requireNonNull(jobId, "jobId");
        if (projectId == null) {
            return syntheticSelectedCompetitorFactory.threeShortReasoningPlaceholders(
                    IndustryType.OTHER, "全国", SyntheticSelectedCompetitorFactory.SyntheticPadReason.NO_CANDIDATES);
        }
        IndustryType industry = IndustryType.OTHER;
        String tradeAreaLabel = "全国";
        TargetAttributes attrs = null;
        try {
            attrs = targetAttributesInferenceService.infer(projectId, targetUrl != null ? targetUrl : "");
            if (attrs != null) {
                if (attrs.industry() != null) {
                    industry = attrs.industry();
                }
                if (attrs.tradeAreaLabel() != null && !attrs.tradeAreaLabel().isBlank()) {
                    tradeAreaLabel = attrs.tradeAreaLabel().trim();
                } else {
                    String composed = composedGeoLabel(attrs);
                    if (!composed.isEmpty()) {
                        tradeAreaLabel = composed;
                    }
                }
            }
        } catch (Throwable throwable) {
        }
        List<ExtractedPlace> places = localStoreRippleSearch.collectMergedPlaces(projectId, attrs, industry);
        if (places.isEmpty()) {
            return syntheticSelectedCompetitorFactory.threeShortReasoningPlaceholders(
                    IndustryType.OTHER, "全国", SyntheticSelectedCompetitorFactory.SyntheticPadReason.NO_CANDIDATES);
        }
        try {
            return competitorFilterService.filter(projectId, industry, tradeAreaLabel, places);
        } catch (Throwable throwable) {
            return competitorFilterService.filter(projectId, industry, tradeAreaLabel, List.of());
        }
    }

    private static String composedGeoLabel(TargetAttributes attrs) {
        if (attrs == null) {
            return "";
        }
        String city = attrs.city() != null ? attrs.city().trim() : "";
        String ward = attrs.ward() != null ? attrs.ward().trim() : "";
        String town = attrs.town() != null ? attrs.town().trim() : "";
        StringBuilder sb = new StringBuilder();
        if (!city.isEmpty()) {
            sb.append(city);
        }
        if (!ward.isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(ward);
        }
        if (!town.isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(town);
        }
        return sb.toString();
    }
}
