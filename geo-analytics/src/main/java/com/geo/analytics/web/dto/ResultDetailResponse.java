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
    Integer tokenCount,
    Integer rankPosition,
    Double sentimentIntensity,
    String resolvedEntityLabel,
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
            auditHistoryEntity.getTokenCount() != null ? auditHistoryEntity.getTokenCount() : 0,
            auditHistoryEntity.getRankPosition() != null ? auditHistoryEntity.getRankPosition() : 0,
            auditHistoryEntity.getSentimentIntensity() != null ? auditHistoryEntity.getSentimentIntensity() : 0.0,
            auditHistoryEntity.getResolvedEntityLabel(),
            auditHistoryEntity.getRawResponse(),
            auditHistoryEntity.getAuditDate(),
            auditHistoryEntity.getCreatedAt());
    }
}
