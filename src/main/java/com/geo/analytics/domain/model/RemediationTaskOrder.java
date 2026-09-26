package com.geo.analytics.domain.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 改善タスクを取り組む順に並べる規則の単一情報源（#139 / #141）。
 *
 * <p>Why: 効果の大きい順（S→A→B）に並べ、同じ効果の中はすぐ直せるもの（SPIKE）を先にする。効果 大をすべて
 * 終えてから効果 中へ進む順序（オーナー確定 2026-09-22）。改善ロードマップはこの並びの番号で
 * 「タスク1〜2」と範囲を指すため、画面で並べ替えるとロードマップと番号がずれる。サーバーで一度だけ決める。
 */
public final class RemediationTaskOrder {

    public static final Comparator<RemediationTask> COMPARATOR = Comparator
            .comparingInt((RemediationTask t) -> t.priority().ordinal())
            .thenComparingInt(t -> t.category().ordinal())
            .thenComparing(Comparator.comparingDouble(RemediationTask::impactScore).reversed())
            .thenComparing(RemediationTask::id);

    private RemediationTaskOrder() {}

    public static List<RemediationTask> sort(List<RemediationTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        ArrayList<RemediationTask> sorted = new ArrayList<>(tasks.size());
        for (RemediationTask task : tasks) {
            if (task != null) {
                sorted.add(task);
            }
        }
        sorted.sort(COMPARATOR);
        return List.copyOf(sorted);
    }
}
