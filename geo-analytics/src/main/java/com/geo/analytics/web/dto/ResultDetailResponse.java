package com.geo.analytics.web.dto;

import com.geo.analytics.domain.entity.ResultEntity;
import java.time.LocalDateTime;
import java.util.UUID;

public record ResultDetailResponse(
    UUID resultId,
    String query,
    Double somScore,
    Boolean brandMentioned,
    Integer mentionRank,
    String rawResponse,
    LocalDateTime createdAt
) {
    public static ResultDetailResponse from(ResultEntity resultEntity) {
        return new ResultDetailResponse(
            resultEntity.getId(),
            resultEntity.getQuery(),
            resultEntity.getSomScore(),
            resultEntity.getBrandMentioned(),
            resultEntity.getMentionRank(),
            resultEntity.getRawResponse(),
            resultEntity.getCreatedAt()
        );
    }
}
