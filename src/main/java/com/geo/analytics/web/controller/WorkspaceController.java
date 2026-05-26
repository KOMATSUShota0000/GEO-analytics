package com.geo.analytics.web.controller;

import com.geo.analytics.application.service.WorkspacePlanResolver;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.web.dto.WorkspaceResponse;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {

    private final WorkspacePlanResolver workspacePlanResolver;

    public WorkspaceController(WorkspacePlanResolver workspacePlanResolver) {
        this.workspacePlanResolver = workspacePlanResolver;
    }

    @GetMapping("/{workspaceId}")
    @PreAuthorize("@tenantAccessEvaluator.canAccessTenant(authentication, #workspaceId)")
    public ResponseEntity<WorkspaceResponse> getWorkspace(@PathVariable UUID workspaceId) {
        SubscriptionPlan plan = workspacePlanResolver.resolvePlan(workspaceId);
        return ResponseEntity.ok(new WorkspaceResponse(plan.name()));
    }
}
