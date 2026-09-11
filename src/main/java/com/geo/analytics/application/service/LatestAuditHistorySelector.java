package com.geo.analytics.application.service;

import com.geo.analytics.domain.entity.AuditHistoryEntity;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * ジョブに紐づく監査履歴から「最新の1件」を選ぶ判定の単一情報源。
 *
 * <p>Why: 同じ選択ロジックが複数のサービスに散ると、片方だけ順序規則が変わったときに
 * 別々の監査履歴に結果が紐づき、原因の追えないデータ不整合になる。{@code .cursorrules} 10節（SSOT）。
 */
final class LatestAuditHistorySelector {

    private LatestAuditHistorySelector() {}

    static AuditHistoryEntity pickLatest(List<AuditHistoryEntity> audits) {
        if (audits == null || audits.isEmpty()) {
            return null;
        }
        return audits.stream()
                .filter(Objects::nonNull)
                .max(Comparator.comparing(
                                AuditHistoryEntity::getAuditDate, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(
                                AuditHistoryEntity::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }
}
