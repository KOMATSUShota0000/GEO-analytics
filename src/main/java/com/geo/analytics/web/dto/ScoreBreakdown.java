package com.geo.analytics.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ScoreBreakdown(
        @JsonProperty("ai_audit_total") double aiAuditTotal,
        @JsonProperty("meo_total") double meoTotal,
        @JsonProperty("machine_readability_total") double machineReadabilityTotal,
        @JsonProperty("final_score") double finalScore,
        // V13_GEO4AXIS 新3軸（Sprint4a-1・追加方式）。旧フィールドはフロント移行(4b)まで後方互換のため残す。
        @JsonProperty("content_total") double contentTotal,
        @JsonProperty("technical_total") double technicalTotal,
        @JsonProperty("authority_total") double authorityTotal,
        @JsonProperty("authority_third_party_core") double authorityThirdPartyCore,
        @JsonProperty("authority_local_meo_sub") double authorityLocalMeoSub,
        @JsonProperty("authority_wikipedia_kg_bonus") double authorityWikipediaKgBonus,
        @JsonProperty("calculation_version") String calculationVersion) {

    // 旧モデルの配点上限（後方互換の表示用）。
    public static final double MAX_AI_AUDIT = 50.0d;
    public static final double MAX_MEO = 25.0d;
    public static final double MAX_MACHINE_READABILITY = 25.0d;

    // V13_GEO4AXIS 3軸の配点上限。
    public static final double MAX_CONTENT = 50.0d;
    public static final double MAX_TECHNICAL = 20.0d;
    public static final double MAX_AUTHORITY = 30.0d;

    public static ScoreBreakdown empty() {
        return new ScoreBreakdown(0.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d, null);
    }
}
