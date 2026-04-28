package com.geo.analytics.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.service.OnboardingDebateStreamRegistry;
import com.geo.analytics.application.service.ProjectContextService;
import com.geo.analytics.web.dto.DebateOnboardingSseEvent;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
public class DebateOnboardingStreamController {
    private static final Logger log = LoggerFactory.getLogger(DebateOnboardingStreamController.class);

    private final ProjectContextService projectContextService;
    private final OnboardingDebateStreamRegistry onboardingDebateStreamRegistry;
    private final ObjectMapper objectMapper;

    public DebateOnboardingStreamController(
            ProjectContextService projectContextService,
            OnboardingDebateStreamRegistry onboardingDebateStreamRegistry,
            ObjectMapper objectMapper) {
        this.projectContextService = projectContextService;
        this.onboardingDebateStreamRegistry = onboardingDebateStreamRegistry;
        this.objectMapper = objectMapper;
    }

    @GetMapping(value = "/{projectId}/onboarding/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamOnboardingDebate(
            @PathVariable UUID projectId, @RequestParam("session_id") UUID sessionId) {
        projectContextService
                .getContext(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project not found: " + projectId));
        try {
            return onboardingDebateStreamRegistry.register(projectId, sessionId);
        } catch (RuntimeException ex) {
            log.warn("onboarding debate stream register failed projectId={} sessionId={}", projectId, sessionId, ex);
            return createEphemeralErrorDebateEmitter(
                    sessionId,
                    ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        }
    }

    private SseEmitter createEphemeralErrorDebateEmitter(UUID sessionId, String message) {
        SseEmitter sseEmitter = new SseEmitter(30_000L);
        try {
            DebateOnboardingSseEvent payload = DebateOnboardingSseEvent.error(message, sessionId);
            String jsonPayload = objectMapper.writeValueAsString(payload);
            sseEmitter.send(SseEmitter.event().name("debate").data(jsonPayload));
            sseEmitter.complete();
        } catch (Exception exception) {
            log.debug("ephemeral onboarding debate error sse failed", exception);
            try {
                sseEmitter.complete();
            } catch (Exception ignored) {
            }
        }
        return sseEmitter;
    }
}
