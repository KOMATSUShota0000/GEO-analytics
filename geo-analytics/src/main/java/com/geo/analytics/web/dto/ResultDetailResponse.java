package com.geo.analytics.web.dto;

import com.geo.analytics.domain.entity.AuditHistoryEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ResultDetailResponse(
    UUID resultId,
    String query,
    Double somScore,
    Boolean brandMentioned,
    Integer mentionRank,
    Integer overallScore,
    String rawResponse,
    LocalDate auditDate,
    Instant createdAt
) {
    public static ResultDetailResponse from(AuditHistoryEntity auditHistoryEntity) {
        return new ResultDetailResponse(
            auditHistoryEntity.getId(),
            auditHistoryEntity.getQuery(),
            auditHistoryEntity.getSomScore(),
            auditHistoryEntity.getBrandMentioned(),
            auditHistoryEntity.getMentionRank(),
            auditHistoryEntity.getOverallScore(),
            auditHistoryEntity.getRawResponse(),
            auditHistoryEntity.getAuditDate(),
            auditHistoryEntity.getCreatedAt());
    }
}
