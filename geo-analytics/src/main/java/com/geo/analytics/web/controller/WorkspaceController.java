package com.geo.analytics.web.controller;

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

    @GetMapping("/{workspaceId}")
    @PreAuthorize("@tenantAccessEvaluator.canAccessTenant(authentication, #workspaceId)")
    public ResponseEntity<Void> getWorkspace(@PathVariable UUID workspaceId) {
        return ResponseEntity.noContent().build();
    }
}
