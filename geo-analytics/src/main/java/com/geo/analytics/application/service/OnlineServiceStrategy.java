package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.CompetitorExtractionContext;
import com.geo.analytics.application.dto.SelectedCompetitor;
import com.geo.analytics.domain.enums.CompetitorExtractionMode;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class OnlineServiceStrategy implements CompetitorExtractionStrategy {

    private final SerpJobCompetitorExtractor serpJobCompetitorExtractor;

    public OnlineServiceStrategy(SerpJobCompetitorExtractor serpJobCompetitorExtractor) {
        this.serpJobCompetitorExtractor = serpJobCompetitorExtractor;
    }

    @Override
    public List<SelectedCompetitor> extract(CompetitorExtractionContext ctx) {
        return serpJobCompetitorExtractor.extract(ctx, CompetitorExtractionMode.ONLINE_SERVICE);
    }
}
