package com.geo.analytics.web.controller;

import com.geo.analytics.application.dto.SyncVerificationResult;
import com.geo.analytics.application.service.SyncVerificationService;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/verify")
public class SyncVerificationController {
    private final SyncVerificationService syncVerificationService;

    public SyncVerificationController(SyncVerificationService syncVerificationService) {
        this.syncVerificationService = syncVerificationService;
    }

    @GetMapping
    public ResponseEntity<SyncVerificationResult> verify(
            @RequestParam String brandName,
            @RequestParam String query,
            @RequestParam(required = false) SubscriptionPlan plan) {
        SubscriptionPlan resolvedPlan = plan != null ? plan : SubscriptionPlan.STANDARD;
        SyncVerificationResult syncVerificationResult =
            syncVerificationService.verify(brandName, query, resolvedPlan);
        return ResponseEntity.ok(syncVerificationResult);
    }
}
