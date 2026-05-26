package com.geo.analytics.web.controller;

import com.geo.analytics.application.service.SubscriptionManagementService;
import com.geo.analytics.web.dto.ChangeSubscriptionRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceSubscriptionController {
    private final SubscriptionManagementService subscriptionManagementService;

    public WorkspaceSubscriptionController(SubscriptionManagementService subscriptionManagementService) {
        this.subscriptionManagementService = subscriptionManagementService;
    }

    @PatchMapping("/{workspaceId}/subscription")
    public ResponseEntity<Void> changeSubscription(
            @PathVariable UUID workspaceId,
            @RequestBody @Valid ChangeSubscriptionRequest changeSubscriptionRequest) {
        subscriptionManagementService.changePlan(workspaceId, changeSubscriptionRequest.plan());
        return ResponseEntity.noContent().build();
    }
}
