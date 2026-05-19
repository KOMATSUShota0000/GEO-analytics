package com.geo.analytics.infrastructure.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.domain.model.MathDebateAuditExportEvent;
import com.geo.analytics.domain.port.WormAuditExportPort;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * WORM 布石: 監査イベントを JSON として一時ファイルへ書き出すのみ（本番 S3 は未接続）。
 */
@Component
public class LocalWormAuditAdapter implements WormAuditExportPort {

    private static final Logger log = LoggerFactory.getLogger(LocalWormAuditAdapter.class);

    private final ObjectMapper objectMapper;

    public LocalWormAuditAdapter(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public void export(MathDebateAuditExportEvent event) {
        if (event == null) {
            return;
        }
        try {
            String json = toJson(event);
            // 本番 stdout 汚染を避けるため標準出力直書きは廃止。監査 JSON 本体は debug ログにのみ残す。
            log.debug("WORM audit payload eventId={} json={}", event.id(), json);
            Path tmp = Files.createTempFile("worm-audit-", ".json");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            log.info("WORM audit export written to {} eventId={}", tmp.toAbsolutePath(), event.id());
        } catch (JsonProcessingException jsonProcessingException) {
            log.warn("WORM audit JSON serialize failed eventId={}", event.id(), jsonProcessingException);
        } catch (IOException ioException) {
            log.warn("WORM audit temp file write failed eventId={}", event.id(), ioException);
        } catch (RuntimeException runtimeException) {
            log.warn("WORM audit export failed eventId={}", event.id(), runtimeException);
        }
    }

    private String toJson(MathDebateAuditExportEvent event) throws JsonProcessingException {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", event.id().toString());
        envelope.put("targetId", event.targetId().toString());
        envelope.put("workspaceId", event.workspaceId() == null ? null : event.workspaceId().toString());
        envelope.put("eventType", event.eventType());
        envelope.put("createdAt", event.createdAt().toString());
        envelope.put("auditData", event.auditData());
        return objectMapper.writeValueAsString(envelope);
    }
}
