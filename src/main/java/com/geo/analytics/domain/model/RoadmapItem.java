package com.geo.analytics.domain.model;

import com.geo.analytics.domain.enums.RoadmapPhase;

/**
 * 改善ロードマップの1項目（#77）。
 *
 * <p>Why: 改善タスク（{@link RemediationTask}）が「何をやるか」なのに対し、ロードマップは
 * 「どの順番で・どのフェーズで」を示す別の成果物。順序を持つことが本質なので {@link RoadmapPhase} を必須にする。
 */
public record RoadmapItem(RoadmapPhase phase, String title, String rationale, String expectedImpact) {
}
