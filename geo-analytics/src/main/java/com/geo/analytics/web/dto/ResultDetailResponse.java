package com.geo.analytics.web.dto;

import com.geo.analytics.domain.entity.AuditHistoryEntity;
import com.geo.analytics.domain.model.VisibilityStageMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ResultDetailResponse(
    UUID resultId,
    String query,
    Double somScore,
    Double gbvsNormalizedScore,
    Boolean brandMentioned,
    Integer mentionRank,
    Integer overallScore,
    Integer tokenCount,
    Integer rankPosition,
    Double sentimentIntensity,
    String resolvedEntityLabel,
    Integer visibilityStage,
    String visibilityStageBand,
    String visibilityStageNarrative,
    String calculationVersion,
    Boolean negativeAlert,
    String rawResponse,
    LocalDate auditDate,
    Instant createdAt
) {
    public static ResultDetailResponse from(AuditHistoryEntity auditHistoryEntity) {
        var som = auditHistoryEntity.getSomScore();
        var stageDef = VisibilityStageMapper.define(auditHistoryEntity.getVisibilityStage());
        return new ResultDetailResponse(
            auditHistoryEntity.getId(),
            auditHistoryEntity.getQuery(),
            som,
            som,
            auditHistoryEntity.getBrandMentioned(),
            auditHistoryEntity.getMentionRank(),
            auditHistoryEntity.getOverallScore(),
            auditHistoryEntity.getTokenCount() != null ? auditHistoryEntity.getTokenCount() : 0,
            auditHistoryEntity.getRankPosition() != null ? auditHistoryEntity.getRankPosition() : 0,
            auditHistoryEntity.getSentimentIntensity() != null ? auditHistoryEntity.getSentimentIntensity() : 0.0,
            auditHistoryEntity.getResolvedEntityLabel(),
            auditHistoryEntity.getVisibilityStage(),
            stageDef.bandLabel(),
            stageDef.narrative(),
            auditHistoryEntity.getCalculationVersion(),
            Boolean.TRUE.equals(auditHistoryEntity.getNegativeAlert()),
            auditHistoryEntity.getRawResponse(),
            auditHistoryEntity.getAuditDate(),
            auditHistoryEntity.getCreatedAt());
    }
}
