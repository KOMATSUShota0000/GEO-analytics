package com.geo.analytics.application.event;

import java.util.List;
import java.util.UUID;

/**
 * @param revokedSessionIds 論理削除されたセッション ID。キャッシュ無効化などに使う（空可）。
 */
public record UserSessionEvictedEvent(UUID userId, UUID organizationId, List<UUID> revokedSessionIds) {

    public UserSessionEvictedEvent {
        revokedSessionIds = List.copyOf(revokedSessionIds);
    }
}
