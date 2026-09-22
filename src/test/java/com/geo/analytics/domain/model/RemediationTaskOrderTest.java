package com.geo.analytics.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.domain.enums.TaskCategory;
import com.geo.analytics.domain.enums.TaskPriority;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * #139 / #141: 効果 大をすべて終えてから効果 中へ。同じ効果の中はすぐ直せるものから（オーナー確定 2026-09-22）。
 *
 * <p>改善ロードマップはこの並びの番号で範囲を指すため、並び順はサーバーで一度だけ決める。
 */
class RemediationTaskOrderTest {

    private static RemediationTask task(TaskPriority p, TaskCategory c, double impact, String title) {
        return new RemediationTask(UUID.randomUUID(), c, p, title, "", impact, null, null);
    }

    @Test
    void 効果の大きい順に並べ同じ効果の中はすぐ直せるものを先にする() {
        List<RemediationTask> sorted = RemediationTaskOrder.sort(List.of(
                task(TaskPriority.B, TaskCategory.SPIKE, 0.3, "小・すぐ"),
                task(TaskPriority.A, TaskCategory.SLAB, 0.6, "中・時間"),
                task(TaskPriority.S, TaskCategory.SLAB, 1.0, "大・時間"),
                task(TaskPriority.A, TaskCategory.SPIKE, 0.4, "中・すぐ"),
                task(TaskPriority.S, TaskCategory.SPIKE, 0.8, "大・すぐ")));

        assertThat(sorted.stream().map(RemediationTask::title).toList())
                .containsExactly("大・すぐ", "大・時間", "中・すぐ", "中・時間", "小・すぐ");
    }

    @Test
    void 効果と時間が同じならimpactScoreの大きい順() {
        List<RemediationTask> sorted = RemediationTaskOrder.sort(List.of(
                task(TaskPriority.S, TaskCategory.SPIKE, 0.7, "低"),
                task(TaskPriority.S, TaskCategory.SPIKE, 0.9, "高")));

        assertThat(sorted.stream().map(RemediationTask::title).toList()).containsExactly("高", "低");
    }

    @Test
    void nullと空は空リストを返す() {
        assertThat(RemediationTaskOrder.sort(null)).isEmpty();
        assertThat(RemediationTaskOrder.sort(List.of())).isEmpty();
    }
}
