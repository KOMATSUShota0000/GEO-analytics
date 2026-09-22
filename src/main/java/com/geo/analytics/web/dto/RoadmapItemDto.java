package com.geo.analytics.web.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Size;

/**
 * 改善ロードマップの1項目（#77）。
 *
 * <p>Why: {@code phaseLabel}（「今すぐ」等）をサーバ側で付けて返す。フェーズ名の日本語表記は
 * 画面・PDF・将来の帳票で共通であるべきで、表示側に散らすと表記ゆれの温床になる。
 *
 * @param firstTaskNumber このフェーズで最初に取り組む改善タスクの番号（#141）。番号の無いデータでは null
 * @param lastTaskNumber  このフェーズで最後に終える改善タスクの番号（#141）。番号の無いデータでは null
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RoadmapItemDto(
        String phase,
        String phaseLabel,
        @Size(max = 200) String title,
        @Size(max = 400) String rationale,
        @Size(max = 400) String expectedImpact,
        Integer firstTaskNumber,
        Integer lastTaskNumber) {}
