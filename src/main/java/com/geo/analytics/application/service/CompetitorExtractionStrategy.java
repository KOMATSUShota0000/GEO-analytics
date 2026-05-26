package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.CompetitorExtractionContext;
import com.geo.analytics.application.dto.SelectedCompetitor;
import java.util.List;

public interface CompetitorExtractionStrategy {
    List<SelectedCompetitor> extract(CompetitorExtractionContext ctx);
}
