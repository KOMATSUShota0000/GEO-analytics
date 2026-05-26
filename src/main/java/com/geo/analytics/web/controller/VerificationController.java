package com.geo.analytics.web.controller;
import com.geo.analytics.application.dto.SyncVerificationResult;
import com.geo.analytics.application.service.SyncVerificationService;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.web.dto.SyncVerifyRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/api/v1/verify")
public class VerificationController {
    private final SyncVerificationService syncVerificationService;
    public VerificationController(SyncVerificationService syncVerificationService) {
        this.syncVerificationService = syncVerificationService;
    }
    @PostMapping
    public ResponseEntity<SyncVerificationResult> verifySynchronously(
            @RequestBody @Valid SyncVerifyRequest syncVerifyRequest) {
        SubscriptionPlan plan = syncVerifyRequest.plan() != null
            ? syncVerifyRequest.plan()
            : SubscriptionPlan.STANDARD;
        SyncVerificationResult syncVerificationResult = syncVerificationService.verify(
            syncVerifyRequest.brandName(), syncVerifyRequest.query(), plan);
        return ResponseEntity.ok(syncVerificationResult);
    }
}
