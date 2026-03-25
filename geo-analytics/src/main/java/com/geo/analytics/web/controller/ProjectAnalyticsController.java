package com.geo.analytics.web.controller;
import com.geo.analytics.application.service.AnalyticsAggregationService;
import com.geo.analytics.web.dto.AnalyticsSummaryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectAnalyticsController {
    private final AnalyticsAggregationService analyticsAggregationService;
    public ProjectAnalyticsController(AnalyticsAggregationService analyticsAggregationService) {
        this.analyticsAggregationService = analyticsAggregationService;
    }
    @GetMapping("/{projectId}/analytics")
    public ResponseEntity<AnalyticsSummaryResponse> getAnalytics(@PathVariable UUID projectId) {
        return analyticsAggregationService
            .summarizeProject(projectId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
