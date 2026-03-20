package com.geo.analytics.web.dto;

import com.geo.analytics.domain.entity.ResultEntity;

public record ResultSummaryResponse(
    String query,
    Double somScore,
    Boolean brandMentioned,
    Integer mentionRank
) {
    public static ResultSummaryResponse from(ResultEntity resultEntity) {
        return new ResultSummaryResponse(
            resultEntity.getQuery(),
            resultEntity.getSomScore(),
            resultEntity.getBrandMentioned(),
            resultEntity.getMentionRank()
        );
    }
}
