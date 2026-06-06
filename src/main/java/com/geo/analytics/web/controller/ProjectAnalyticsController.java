package com.geo.analytics.web.controller;

import com.geo.analytics.application.service.AnalyticsAggregationService;
import com.geo.analytics.application.service.GeoAssetSnapshotQueryService;
import com.geo.analytics.application.service.ProjectSettingsService;
import com.geo.analytics.web.dto.AnalyticsSummaryResponse;
import com.geo.analytics.web.dto.AssetSnapshotsChartResponse;
import com.geo.analytics.web.dto.ProjectSettingsPatchRequest;
import com.geo.analytics.web.dto.ProjectSettingsResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectAnalyticsController {

    private final AnalyticsAggregationService analyticsAggregationService;
    private final ProjectSettingsService projectSettingsService;
    private final GeoAssetSnapshotQueryService geoAssetSnapshotQueryService;

    public ProjectAnalyticsController(
            AnalyticsAggregationService analyticsAggregationService,
            ProjectSettingsService projectSettingsService,
            GeoAssetSnapshotQueryService geoAssetSnapshotQueryService) {
        this.analyticsAggregationService = analyticsAggregationService;
        this.projectSettingsService = projectSettingsService;
        this.geoAssetSnapshotQueryService = geoAssetSnapshotQueryService;
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

    @GetMapping("/{projectId}/asset-snapshots")
    @PreAuthorize("@tenantAccessEvaluator.canReadProjectAssetSnapshots(authentication, #projectId)")
    public ResponseEntity<AssetSnapshotsChartResponse> getAssetSnapshots(
            @PathVariable UUID projectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(geoAssetSnapshotQueryService.getChartData(projectId, from, to));
    }

    @PatchMapping("/{projectId}")
    public ResponseEntity<ProjectSettingsResponse> patchProject(
            @PathVariable UUID projectId,
            @Valid @RequestBody ProjectSettingsPatchRequest projectSettingsPatchRequest) {
        return ResponseEntity.ok(projectSettingsService.patch(projectId, projectSettingsPatchRequest));
    }
}
