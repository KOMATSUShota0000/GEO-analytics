package com.geo.analytics.web.controller;
import com.geo.analytics.application.service.AnalyticsAggregationService;
import com.geo.analytics.application.service.ProjectSettingsService;
import com.geo.analytics.web.dto.AnalyticsSummaryResponse;
import com.geo.analytics.web.dto.ProjectSettingsPatchRequest;
import com.geo.analytics.web.dto.ProjectSettingsResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectAnalyticsController {
    private final AnalyticsAggregationService analyticsAggregationService;
    private final ProjectSettingsService projectSettingsService;
    public ProjectAnalyticsController(
            AnalyticsAggregationService analyticsAggregationService,
            ProjectSettingsService projectSettingsService) {
        this.analyticsAggregationService = analyticsAggregationService;
        this.projectSettingsService = projectSettingsService;
    }
    @GetMapping("/{projectId}/analytics")
    public ResponseEntity<AnalyticsSummaryResponse> getAnalytics(@PathVariable UUID projectId) {
        return analyticsAggregationService
            .summarizeProject(projectId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
    @GetMapping("/{projectId}/settings")
    public ResponseEntity<ProjectSettingsResponse> getSettings(@PathVariable UUID projectId) {
        return projectSettingsService
            .getSettings(projectId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
    @PatchMapping("/{projectId}")
    public ResponseEntity<ProjectSettingsResponse> patchProject(
            @PathVariable UUID projectId,
            @Valid @RequestBody ProjectSettingsPatchRequest projectSettingsPatchRequest) {
        return ResponseEntity.ok(projectSettingsService.patch(projectId, projectSettingsPatchRequest));
    }
}
