package com.geo.analytics.web.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.geo.analytics.domain.enums.AiRecognitionState;
import com.geo.analytics.domain.model.AiRecognitionSummary;

/**
 * AI認識状況のジョブ単位サマリのレスポンス表現（V13 Sprint4a-2）。スコア非算入の定性エビデンス。
 * {@code dominant} は enum 名（RECOGNIZED_CORRECTLY/MISIDENTIFIED/UNKNOWN）でシリアライズされる。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AiRecognitionSummaryResponse(
        AiRecognitionState dominant,
        int recognizedCount,
        int misidentifiedCount,
        int unknownCount,
        int evaluatedCount) {

    public static AiRecognitionSummaryResponse from(AiRecognitionSummary summary) {
        if (summary == null) {
            return null;
        }
        return new AiRecognitionSummaryResponse(
                summary.dominant(),
                summary.recognizedCount(),
                summary.misidentifiedCount(),
                summary.unknownCount(),
                summary.evaluatedCount());
    }
}
