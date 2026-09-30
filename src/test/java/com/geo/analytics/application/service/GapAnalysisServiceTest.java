package com.geo.analytics.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geo.analytics.application.dto.DebateJobFacts;
import com.geo.analytics.application.dto.ProjectAdviceContext;
import com.geo.analytics.application.dto.StrategyInsight;
import com.geo.analytics.domain.ai.DebatePersona;
import com.geo.analytics.domain.entity.AuditHistoryEntity;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.enums.AdviceSource;
import com.geo.analytics.domain.enums.DebateStatus;
import com.geo.analytics.domain.enums.IndustryType;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.enums.TaskCategory;
import com.geo.analytics.domain.enums.TaskPriority;
import com.geo.analytics.domain.model.DebateUtterance;
import com.geo.analytics.domain.model.RemediationTask;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

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
        when(insight.rollupJobWithSource(eq(rows), any(), eq(SubscriptionPlan.STANDARD), any(), any(), any()))
                .thenReturn(new StrategyInsightService.JobAdviceRollup(
                        new StrategyInsight("診断", List.of(), 0.1), AdviceSource.AI));

        service.runForJob(jobId);

        ArgumentCaptor<List<RemediationTask>> captor = ArgumentCaptor.forClass(List.class);
        verify(insight).rollupJobWithSource(eq(rows), any(), eq(SubscriptionPlan.STANDARD), captor.capture(), any(), any());
        assertThat(captor.getValue().stream().map(RemediationTask::title).toList())
                .containsExactly("大・すぐ", "大・時間", "中・すぐ");
        verify(batch, never()).findRemediationTasks(older.getId());
    }

    /** #195: プロジェクト側の前提は実際には空のため、依頼時の入力とサイト診断の結果をジョブから読んで議論に渡す。 */
    @Test
    void 依頼時の事業情報と診断結果を議論の材料として渡す() {
        UUID jobId = UUID.randomUUID();
        JobEntity job = new JobEntity();
        job.setId(jobId);
        job.setProjectId(UUID.randomUUID());
        job.setAppliedPlan(SubscriptionPlan.STANDARD);
        job.setBusinessSummary("自然素材の注文住宅");
        job.setTargetAudience("子育て世帯");
        job.setFocusPoints("施工事例");
        job.setSelfRubricAuditJson("{\"items\":[]}");
        List<AuditHistoryEntity> rows = List.of(row(LocalDate.of(2026, 9, 22)), row(LocalDate.of(2026, 9, 1)));
        when(batch.findJobById(jobId)).thenReturn(job);
        when(batch.findResultsByJobId(jobId)).thenReturn(rows);
        when(batch.findProjectAdviceContext(any()))
                .thenReturn(Optional.of(new ProjectAdviceContext(IndustryType.B2B, "t", "s")));
        when(insight.medianModifiedZ(rows)).thenReturn(0.1);
        when(insight.medianVisibilityStage(rows)).thenReturn(5);
        when(insight.keywordInsightRelative(anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(new StrategyInsight("q", List.of(), 0.1));
        when(insight.rollupJobWithSource(eq(rows), any(), eq(SubscriptionPlan.STANDARD), any(), any(), any()))
                .thenReturn(new StrategyInsightService.JobAdviceRollup(
                        new StrategyInsight("診断", List.of(), 0.1), AdviceSource.AI));

        service.runForJob(jobId);

        ArgumentCaptor<DebateJobFacts> captor = ArgumentCaptor.forClass(DebateJobFacts.class);
        verify(insight).rollupJobWithSource(eq(rows), any(), eq(SubscriptionPlan.STANDARD), any(), captor.capture(), any());
        assertThat(captor.getValue())
                .isEqualTo(new DebateJobFacts("自然素材の注文住宅", "子育て世帯", "施工事例", "{\"items\":[]}"));
    }

    private static final UUID ORG = UUID.randomUUID();
    private static final DebateUtterance FIRST = DebateUtterance.summaryOnly(1, DebatePersona.ANALYST, "一言目");
    private static final DebateUtterance SECOND = DebateUtterance.summaryOnly(1, DebatePersona.INNOVATOR, "二言目");

    /** 議論の本体の代わりに、受け手（DebateRecorder）へ発言と失敗を渡す。 */
    private UUID jobWhoseDebate(Consumer<DebateRecorder> debate) {
        UUID jobId = UUID.randomUUID();
        JobEntity job = new JobEntity();
        job.setId(jobId);
        job.setProjectId(UUID.randomUUID());
        job.setAppliedPlan(SubscriptionPlan.STANDARD);
        List<AuditHistoryEntity> rows = List.of(row(LocalDate.of(2026, 9, 30)), row(LocalDate.of(2026, 9, 29)));
        when(batch.findJobById(jobId)).thenReturn(job);
        when(batch.findResultsByJobId(jobId)).thenReturn(rows);
        when(batch.findProjectAdviceContext(any())).thenReturn(Optional.of(new ProjectAdviceContext(
                IndustryType.B2B, "t", "s", UUID.randomUUID(), UUID.randomUUID(), ORG)));
        when(insight.medianModifiedZ(rows)).thenReturn(0.1);
        when(insight.medianVisibilityStage(rows)).thenReturn(5);
        when(insight.keywordInsightRelative(anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(new StrategyInsight("q", List.of(), 0.1));
        when(insight.rollupJobWithSource(eq(rows), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            debate.accept(invocation.getArgument(5));
            return new StrategyInsightService.JobAdviceRollup(
                    new StrategyInsight("診断", List.of(), 0.1), AdviceSource.AI);
        });
        return jobId;
    }

    /** #197: 発言はできた順に番号を付けて保存し、終わりの印は総合診断を書いたあとに付ける。 */
    @Test
    void 議論の発言を順に保存し_総合診断のあとで完了にする() {
        UUID jobId = jobWhoseDebate(recorder -> {
            recorder.spoke(FIRST);
            recorder.spoke(SECOND);
        });

        service.runForJob(jobId);

        InOrder order = inOrder(batch);
        order.verify(batch).insertDebateUtterance(jobId, ORG, 1, FIRST);
        order.verify(batch).insertDebateUtterance(jobId, ORG, 2, SECOND);
        order.verify(batch).updateJobStrategyRollup(eq(jobId), any(), any(), any(), any(), any());
        order.verify(batch).finishDebate(jobId, DebateStatus.COMPLETED);
    }

    @Test
    void 議論が失敗したら失敗の印を付ける() {
        UUID jobId = jobWhoseDebate(recorder -> {
            recorder.spoke(FIRST);
            recorder.failed();
        });

        service.runForJob(jobId);

        verify(batch).finishDebate(jobId, DebateStatus.FAILED);
    }

    @Test
    void 議論が走らなかったら議論なしの印を付ける() {
        UUID jobId = jobWhoseDebate(recorder -> {});

        service.runForJob(jobId);

        verify(batch).finishDebate(jobId, DebateStatus.SKIPPED);
    }

    @Test
    void 発言の保存に失敗しても議論と総合診断は止めない() {
        UUID jobId = jobWhoseDebate(recorder -> {
            recorder.spoke(FIRST);
            recorder.spoke(SECOND);
        });
        doThrow(new RuntimeException("db down")).when(batch).insertDebateUtterance(jobId, ORG, 1, FIRST);

        service.runForJob(jobId);

        verify(batch).insertDebateUtterance(jobId, ORG, 2, SECOND);
        verify(batch).finishDebate(jobId, DebateStatus.COMPLETED);
    }

    @Test
    void 質問が2本未満なら議論なしの印を付ける() {
        UUID jobId = UUID.randomUUID();
        JobEntity job = new JobEntity();
        job.setId(jobId);
        when(batch.findJobById(jobId)).thenReturn(job);
        when(batch.findResultsByJobId(jobId)).thenReturn(List.of(row(LocalDate.of(2026, 9, 30))));
        when(insight.rollupJob(any())).thenReturn(new StrategyInsight("テンプレ", List.of(), null));

        service.runForJob(jobId);

        verify(batch).finishDebate(jobId, DebateStatus.SKIPPED);
    }

    /** #197: 議論中の印は裏の処理を起こす前に付け、裏の処理が落ちたら議論中のまま残さない。 */
    @Test
    void 議論中の印を先に付け_裏の処理が落ちたら議論中のまま残さない() {
        UUID jobId = UUID.randomUUID();
        when(batch.findJobById(jobId)).thenThrow(new RuntimeException("job lookup failed"));

        service.scheduleForJob(jobId);

        verify(batch).beginDebate(jobId);
        verify(batch, timeout(5_000)).abandonDebate(jobId);
    }
}
