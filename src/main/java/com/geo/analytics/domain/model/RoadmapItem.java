package com.geo.analytics.domain.model;

import com.geo.analytics.domain.enums.RoadmapPhase;

/**
 * 改善ロードマップの1項目（#77）。
 *
 * <p>Why: 改善タスク（{@link RemediationTask}）が「何をやるか」なのに対し、ロードマップは
 * 「どの順番で・どのフェーズで」を示す別の成果物。順序を持つことが本質なので {@link RoadmapPhase} を必須にする。
 *
 * <p>ロードマップは改善タスクの時間割で、このフェーズで終える改善タスクの番号（{@link RemediationTaskOrder}
 * の並びで1始まり）を範囲で持つ（#141）。改善タスクが無い解析と #141 より前のデータでは null。
 *
 * @param firstTaskNumber このフェーズで最初に取り組む改善タスクの番号
 * @param lastTaskNumber  このフェーズで最後に終える改善タスクの番号
 */
public record RoadmapItem(
        RoadmapPhase phase,
        String title,
        String rationale,
        String expectedImpact,
        Integer firstTaskNumber,
        Integer lastTaskNumber) {
}
