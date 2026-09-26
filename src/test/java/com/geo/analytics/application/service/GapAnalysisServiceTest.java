package com.geo.analytics.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geo.analytics.application.dto.ProjectAdviceContext;
import com.geo.analytics.application.dto.StrategyInsight;
import com.geo.analytics.domain.entity.AuditHistoryEntity;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.enums.AdviceSource;
import com.geo.analytics.domain.enums.IndustryType;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.enums.TaskCategory;
import com.geo.analytics.domain.enums.TaskPriority;
import com.geo.analytics.domain.model.RemediationTask;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * #141: 改善タスクは議論より先に保存されている。議論には、タスクを保存した最新の監査履歴から読んだ
 * タスクを取り組む順に並べて渡し、ロードマップの番号と画面の番号を一致させる。
 */
class GapAnalysisServiceTest {

    private final BatchPersistenceService batch = mock(BatchPersistenceService.class);
    private final StrategyInsightService insight = mock(StrategyInsightService.class);
    private final GapAnalysisService service =
            new GapAnalysisService(batch, insight, mock(GapBatchSubmissionService.class));

    private static AuditHistoryEntity row(LocalDate date) {
        AuditHistoryEntity a = new AuditHistoryEntity();
        a.setId(UUID.randomUUID());
        a.setAuditDate(date);
        a.setModifiedZScore(0.1);
        a.setVisibilityStage(5);
        return a;
    }

    private static RemediationTask task(TaskPriority p, TaskCategory c, String title) {
        return new RemediationTask(UUID.randomUUID(), c, p, title, "", 0.5, null, null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 最新の監査履歴の改善タスクを取り組む順に並べて議論へ渡す() {
        UUID jobId = UUID.randomUUID();
        JobEntity job = new JobEntity();
        job.setId(jobId);
        job.setProjectId(UUID.randomUUID());
        job.setAppliedPlan(SubscriptionPlan.STANDARD);
        AuditHistoryEntity older = row(LocalDate.of(2026, 9, 1));
        AuditHistoryEntity latest = row(LocalDate.of(2026, 9, 22));
        List<AuditHistoryEntity> rows = List.of(latest, older);
        when(batch.findJobById(jobId)).thenReturn(job);
        when(batch.findResultsByJobId(jobId)).thenReturn(rows);
        when(batch.findProjectAdviceContext(any()))
                .thenReturn(Optional.of(new ProjectAdviceContext(IndustryType.B2B, "t", "s")));
        when(batch.findRemediationTasks(latest.getId())).thenReturn(List.of(
                task(TaskPriority.A, TaskCategory.SPIKE, "中・すぐ"),
                task(TaskPriority.S, TaskCategory.SLAB, "大・時間"),
                task(TaskPriority.S, TaskCategory.SPIKE, "大・すぐ")));
        when(insight.medianModifiedZ(rows)).thenReturn(0.1);
        when(insight.medianVisibilityStage(rows)).thenReturn(5);
        when(insight.keywordInsightRelative(anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(new StrategyInsight("q", List.of(), 0.1));
        when(insight.rollupJobWithSource(eq(rows), any(), eq(SubscriptionPlan.STANDARD), any()))
                .thenReturn(new StrategyInsightService.JobAdviceRollup(
                        new StrategyInsight("診断", List.of(), 0.1), AdviceSource.AI));

        service.runForJob(jobId);

        ArgumentCaptor<List<RemediationTask>> captor = ArgumentCaptor.forClass(List.class);
        verify(insight).rollupJobWithSource(eq(rows), any(), eq(SubscriptionPlan.STANDARD), captor.capture());
        assertThat(captor.getValue().stream().map(RemediationTask::title).toList())
                .containsExactly("大・すぐ", "大・時間", "中・すぐ");
        verify(batch, never()).findRemediationTasks(older.getId());
    }
}
