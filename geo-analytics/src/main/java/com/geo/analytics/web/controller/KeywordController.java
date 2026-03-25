package com.geo.analytics.web.controller;

import com.geo.analytics.application.dto.KeywordRegistrationRequest;
import com.geo.analytics.application.dto.KeywordRegistrationResult;
import com.geo.analytics.application.dto.KeywordSuggestionRequest;
import com.geo.analytics.application.dto.KeywordSuggestionResponse;
import com.geo.analytics.application.service.KeywordRoutingService;
import com.geo.analytics.application.service.KeywordSuggestionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class KeywordController {
    private final KeywordSuggestionService keywordSuggestionService;
    private final KeywordRoutingService keywordRoutingService;

    public KeywordController(KeywordSuggestionService keywordSuggestionService, KeywordRoutingService keywordRoutingService) {
        this.keywordSuggestionService = keywordSuggestionService;
        this.keywordRoutingService = keywordRoutingService;
    }

    @PostMapping("/keywords/suggest")
    public ResponseEntity<KeywordSuggestionResponse> suggest(@Valid @RequestBody KeywordSuggestionRequest keywordSuggestionRequest) {
        return ResponseEntity.ok(keywordSuggestionService.suggestKeywords(keywordSuggestionRequest));
    }

    @PostMapping("/projects/{projectId}/keywords/batch")
    public ResponseEntity<KeywordRegistrationResult> registerProjectKeywords(
            @PathVariable UUID projectId,
            @Valid @RequestBody KeywordRegistrationRequest keywordRegistrationRequest) {
        return ResponseEntity.ok(keywordRoutingService.registerKeywords(projectId, keywordRegistrationRequest));
    }
}
