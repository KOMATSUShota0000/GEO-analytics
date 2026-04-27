package com.geo.analytics.web.controller;

import com.geo.analytics.application.command.UpdateProjectContextCommand;
import com.geo.analytics.application.service.ProjectContextService;
import com.geo.analytics.application.service.ProjectOnboardingService;
import com.geo.analytics.domain.model.MinorityReport;
import com.geo.analytics.web.dto.ExtractContextRequest;
import com.geo.analytics.web.dto.ProjectContextPatchRequest;
import com.geo.analytics.web.dto.ProjectContextResponse;
import java.util.List;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectOnboardingController {
    private final ProjectOnboardingService projectOnboardingService;
    private final ProjectContextService projectContextService;

    public ProjectOnboardingController(
            ProjectOnboardingService projectOnboardingService, ProjectContextService projectContextService) {
        this.projectOnboardingService = projectOnboardingService;
        this.projectContextService = projectContextService;
    }

    @PostMapping("/{id}/extract-context")
    public ResponseEntity<ProjectContextResponse> extractContext(
            @PathVariable("id") UUID id, @Valid @RequestBody ExtractContextRequest extractContextRequest) {
        projectOnboardingService.runOnboarding(id, extractContextRequest.url());
        return projectContextService
                .getContext(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new EntityNotFoundException("Project not found: " + id));
    }

    @PatchMapping("/{id}/context")
    public ResponseEntity<ProjectContextResponse> patchContext(
            @PathVariable("id") UUID id, @Valid @RequestBody ProjectContextPatchRequest projectContextPatchRequest) {
        List<MinorityReport> minorityReports =
                projectContextPatchRequest.minorityReports().stream()
                        .map(
                                dto ->
                                        new MinorityReport(
                                                dto.insight() == null ? "" : dto.insight(),
                                                dto.conflictReason() == null ? "" : dto.conflictReason(),
                                                dto.evidence() == null ? "" : dto.evidence()))
                        .toList();
        UpdateProjectContextCommand updateProjectContextCommand =
                new UpdateProjectContextCommand(
                        projectContextPatchRequest.industryType(),
                        projectContextPatchRequest.extractedStrengths(),
                        projectContextPatchRequest.targetAudience(),
                        minorityReports);
        return ResponseEntity.ok(projectContextService.patchContext(id, updateProjectContextCommand));
    }
}
