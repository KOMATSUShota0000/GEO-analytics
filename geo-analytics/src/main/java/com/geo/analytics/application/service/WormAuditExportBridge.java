package com.geo.analytics.application.service;

import com.geo.analytics.domain.model.MathDebateAuditExportEvent;
import com.geo.analytics.domain.port.WormAuditExportPort;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class WormAuditExportBridge {

    private static final Logger log = LoggerFactory.getLogger(WormAuditExportBridge.class);

    private final WormAuditExportPort wormAuditExportPort;

    public WormAuditExportBridge(WormAuditExportPort wormAuditExportPort) {
        this.wormAuditExportPort = Objects.requireNonNull(wormAuditExportPort, "wormAuditExportPort");
    }

    /**
     * オンボーディング等のメイン処理をブロックせず WORM エクスポートをスケジュールする。失敗しても例外を外に伝播しない。
     */
    @Async
    public void exportAsync(MathDebateAuditExportEvent event) {
        if (event == null) {
            return;
        }
        try {
            wormAuditExportPort.export(event);
        } catch (Exception e) {
            log.warn("async WORM export failed eventId={}", event.id(), e);
        }
    }
}
