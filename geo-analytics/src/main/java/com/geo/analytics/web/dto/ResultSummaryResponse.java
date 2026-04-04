package com.geo.analytics.web.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.geo.analytics.domain.entity.AuditHistoryEntity;
import java.time.LocalDate;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ResultSummaryResponse(
    String query,
    Double somScore,
    Boolean brandMentioned,
    Integer mentionRank,
    Integer overallScore,
    LocalDate auditDate
) {
    public static ResultSummaryResponse from(AuditHistoryEntity auditHistoryEntity) {
        return new ResultSummaryResponse(
            auditHistoryEntity.getQuery(),
            auditHistoryEntity.getSomScore(),
            auditHistoryEntity.getBrandMentioned(),
            auditHistoryEntity.getMentionRank(),
            auditHistoryEntity.getOverallScore(),
            auditHistoryEntity.getAuditDate());
    }
}
