package com.geo.analytics.web.controller;

import com.geo.analytics.application.service.OneClickCompetitorExtractionService;
import com.geo.analytics.web.dto.OneClickCompetitorExtractionRequest;
import com.geo.analytics.web.dto.OneClickCompetitorExtractionResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/competitors")
public class OneClickCompetitorController {

    private final OneClickCompetitorExtractionService oneClickCompetitorExtractionService;

    public OneClickCompetitorController(OneClickCompetitorExtractionService oneClickCompetitorExtractionService) {
        this.oneClickCompetitorExtractionService = oneClickCompetitorExtractionService;
    }

    @PostMapping("/extract")
    @PreAuthorize("@tenantAccessEvaluator.canReadProjectAssetSnapshots(authentication, #projectId)")
    public ResponseEntity<OneClickCompetitorExtractionResponse> extractOneClickCompetitors(
            @PathVariable UUID projectId, @Valid @RequestBody OneClickCompetitorExtractionRequest request) {
        return ResponseEntity.ok(
                OneClickCompetitorExtractionResponse.from(
                        oneClickCompetitorExtractionService.extract(projectId, request.selfUrl())));
    }
}
