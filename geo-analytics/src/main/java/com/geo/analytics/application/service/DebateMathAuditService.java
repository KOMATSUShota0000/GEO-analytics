package com.geo.analytics.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.domain.entity.MathDebateAuditEventEntity;
import com.geo.analytics.domain.logic.ConvergenceController;
import com.geo.analytics.domain.model.AuditEvidenceRef;
import com.geo.analytics.domain.model.MathDebateAuditExportEvent;
import com.geo.analytics.domain.model.SeoEvidence;
import com.geo.analytics.infrastructure.repository.MathDebateAuditEventRepository;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import com.geo.analytics.web.dto.DebateOnboardingSseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * オンボーディング・ディベートの数理監査を、LLM I/O の外で短いトランザクションだけで永続化する。
 */
@Service
public class DebateMathAuditService {

    private static final Logger log = LoggerFactory.getLogger(DebateMathAuditService.class);
    private static final String AUDIT_EVENT_TYPE = "MATH_DEBATE_ONBOARDING";

    private final MathDebateAuditEventRepository mathDebateAuditEventRepository;
    private final WormAuditExportBridge wormAuditExportBridge;
    private final ObjectMapper objectMapper;

    public DebateMathAuditService(
            MathDebateAuditEventRepository mathDebateAuditEventRepository,
            WormAuditExportBridge wormAuditExportBridge,
            ObjectMapper objectMapper) {
        this.mathDebateAuditEventRepository =
                Objects.requireNonNull(mathDebateAuditEventRepository, "mathDebateAuditEventRepository");
        this.wormAuditExportBridge = Objects.requireNonNull(wormAuditExportBridge, "wormAuditExportBridge");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveOnboardingAudit(
            UUID targetId,
            int turnCount,
            String stopReason,
            ConvergenceController.ConvergenceSnapshot snapshot,
            double geoIgScore,
            double trustScore,
            double friction,
            boolean competitorXmlIncluded,
            List<SeoEvidence> usedEvidences,
            List<DebateOnboardingSseEvent> narrationLogBuffer,
            String sessionOutcome,
            int executedTurnsAtEnd) {
        MathDebateAuditEventEntity row = new MathDebateAuditEventEntity();
        row.setTargetId(targetId);
        row.setEventType(AUDIT_EVENT_TYPE);
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("turnCount", turnCount);
        audit.put("stopReason", stopReason);
        if (snapshot != null) {
            audit.put("wasserstein1", snapshot.wasserstein1());
            audit.put("threshold", snapshot.threshold());
            audit.put("informationGeometryDrift", snapshot.informationGeometryDrift());
        } else {
            audit.put("wasserstein1", null);
            audit.put("threshold", null);
            audit.put("informationGeometryDrift", null);
        }
        audit.put("geoIgScore", geoIgScore);
        audit.put("trustScore", trustScore);
        audit.put("friction", friction);
        audit.put("competitorXmlIncluded", competitorXmlIncluded);
        audit.put("usedEvidenceRefs", toAuditEvidenceRefMaps(usedEvidences));
        audit.put("narration_logs", narrationEventsToJsonMaps(narrationLogBuffer));
        audit.put("session_outcome", sessionOutcome);
        audit.put("executed_turns_at_end", executedTurnsAtEnd);
        row.setAuditData(audit);
        MathDebateAuditEventEntity saved = mathDebateAuditEventRepository.save(row);

        UUID workspaceId = TenantContextHolder.getTenantId().orElse(null);
        Map<String, Object> exportAuditPayload = new LinkedHashMap<>(saved.getAuditData());
        MathDebateAuditExportEvent exportEvent =
                new MathDebateAuditExportEvent(
                        saved.getId(),
                        saved.getTargetId(),
                        workspaceId,
                        saved.getEventType(),
                        exportAuditPayload,
                        saved.getCreatedAt());
        try {
            wormAuditExportBridge.exportAsync(exportEvent);
        } catch (RuntimeException e) {
            log.warn("failed to schedule async WORM export eventId={}", saved.getId(), e);
        }
    }

    private List<Map<String, Object>> narrationEventsToJsonMaps(List<DebateOnboardingSseEvent> narrationLogBuffer) {
        if (narrationLogBuffer == null || narrationLogBuffer.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>(narrationLogBuffer.size());
        synchronized (narrationLogBuffer) {
            for (DebateOnboardingSseEvent event : narrationLogBuffer) {
                if (event == null) {
                    continue;
                }
                out.add(objectMapper.convertValue(event, new TypeReference<LinkedHashMap<String, Object>>() {}));
            }
        }
        return out;
    }

    private static List<Map<String, Object>> toAuditEvidenceRefMaps(List<SeoEvidence> used) {
        if (used == null || used.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (SeoEvidence e : used) {
            if (e == null) {
                continue;
            }
            AuditEvidenceRef ref =
                    new AuditEvidenceRef(
                            e.url() == null ? "" : e.url(),
                            e.title() == null ? "" : e.title(),
                            e.priorityScore(),
                            e.relevanceCategory() == null ? "" : e.relevanceCategory());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("url", ref.url());
            m.put("title", ref.title());
            m.put("priorityScore", ref.priorityScore());
            m.put("relevanceCategory", ref.relevanceCategory());
            out.add(m);
        }
        return out;
    }
}
