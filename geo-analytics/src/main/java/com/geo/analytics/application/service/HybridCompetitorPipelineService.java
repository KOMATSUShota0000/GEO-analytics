package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.CompetitorExtractionContext;
import com.geo.analytics.application.dto.SelectedCompetitor;
import com.geo.analytics.domain.enums.CompetitorExtractionMode;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
public class HybridCompetitorPipelineService {

    private final CompetitorExtractionStrategyFactory competitorExtractionStrategyFactory;

    public HybridCompetitorPipelineService(CompetitorExtractionStrategyFactory competitorExtractionStrategyFactory) {
        this.competitorExtractionStrategyFactory = competitorExtractionStrategyFactory;
    }

    public List<SelectedCompetitor> executePipeline(
            UUID jobId, UUID projectId, String targetUrl, CompetitorExtractionMode competitorExtractionMode) {
        CompetitorExtractionMode resolved =
                competitorExtractionMode != null ? competitorExtractionMode : CompetitorExtractionMode.LOCAL_STORE;
        CompetitorExtractionContext ctx = new CompetitorExtractionContext(jobId, projectId, targetUrl);
        return competitorExtractionStrategyFactory.resolve(resolved).extract(ctx);
    }
}
