package com.geo.analytics.web.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.geo.analytics.application.service.StrategyInsightService;
import com.geo.analytics.domain.entity.AuditHistoryEntity;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.model.VisibilityStageMapper;
import java.lang.StrictMath;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
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
    Double visibilityStageProgress,
    String calculationVersion,
    Boolean negativeAlert,
    Double modifiedZScore,
    String diagnosticMessage,
    List<String> recommendedActions,
    Boolean significantDeviation,
    String rawResponse,
    LocalDate auditDate,
    Instant createdAt
) {
    public ResultDetailResponse withMentionRank(Integer newMentionRank) {
        return new ResultDetailResponse(
                resultId,
                query,
                somScore,
                gbvsNormalizedScore,
                brandMentioned,
                newMentionRank,
                overallScore,
                tokenCount,
                rankPosition,
                sentimentIntensity,
                resolvedEntityLabel,
                visibilityStage,
                visibilityStageBand,
                visibilityStageNarrative,
                visibilityStageProgress,
                calculationVersion,
                negativeAlert,
                modifiedZScore,
                diagnosticMessage,
                recommendedActions,
                significantDeviation,
                rawResponse,
                auditDate,
                createdAt);
    }

    public static ResultDetailResponse from(
            AuditHistoryEntity auditHistoryEntity,
            StrategyInsightService strategyInsightService,
            Double jobMedianModifiedZ,
            SubscriptionPlan subscriptionPlan) {
        var plan = subscriptionPlan != null ? subscriptionPlan : SubscriptionPlan.STANDARD;
        var som = auditHistoryEntity.getSomScore();
        var gbvs = auditHistoryEntity.getGbvsNormalizedScore();
        var stageDef = VisibilityStageMapper.define(
            auditHistoryEntity.getVisibilityStage(),
            auditHistoryEntity.getRankPosition());
        var insight = strategyInsightService.resolveForAudit(auditHistoryEntity);
        var mz = auditHistoryEntity.getModifiedZScore() != null
            ? auditHistoryEntity.getModifiedZScore()
            : insight.representativeModifiedZ();
        Boolean sig = Boolean.FALSE;
        if (plan.usesProTierFeatures()
            && jobMedianModifiedZ != null
            && auditHistoryEntity.getModifiedZScore() != null) {
            sig = StrictMath.abs(auditHistoryEntity.getModifiedZScore() - jobMedianModifiedZ) >= 1.0;
        }
        return new ResultDetailResponse(
            auditHistoryEntity.getId(),
            auditHistoryEntity.getQuery(),
            som,
            gbvs,
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
            stageDef.progressRate(),
            auditHistoryEntity.getCalculationVersion(),
            Boolean.TRUE.equals(auditHistoryEntity.getNegativeAlert()),
            mz,
            insight.diagnosticMessage(),
            List.copyOf(insight.recommendedActions()),
            sig,
            auditHistoryEntity.getRawResponse(),
            auditHistoryEntity.getAuditDate(),
            auditHistoryEntity.getCreatedAt());
    }
}
