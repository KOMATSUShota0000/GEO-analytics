package com.geo.analytics.domain.port;

import com.geo.analytics.domain.model.MathDebateAuditExportEvent;

/**
 * 監査イベントを WORM 相当ストレージへエクスポートするポート（S3 Object Lock 等の実装はアダプタで差し替え）。
 */
public interface WormAuditExportPort {

    void export(MathDebateAuditExportEvent event);
}
