package com.geo.analytics.web.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * スコア「コンテンツの充実度」のサイト固有エビデンス（V13 / 2026-06-07）。
 *
 * <p>ルーブリックLLM10項目の各々について、判定（YES/PARTIAL/NO）と
 * <strong>対象サイト本文からの直接引用</strong>（evidence）を露出する。
 * 引用は各サイトの実文章なのでサイトごとに必ず異なり、定型文にならない。
 * {@code criterionId} の人間可読ラベルはフロント側で対応付ける（表示の関心）。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ContentEvidenceItemResponse(
        String criterionId,
        String verdict,
        String evidence,
        double score,
        double maxScore) {
}
