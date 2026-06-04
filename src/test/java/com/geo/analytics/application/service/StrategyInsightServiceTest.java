package com.geo.analytics.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geo.analytics.application.dto.ProjectAdviceContext;
import com.geo.analytics.application.dto.StrategyInsight;
import com.geo.analytics.domain.entity.AuditHistoryEntity;
import com.geo.analytics.domain.enums.IndustryType;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class StrategyInsightServiceTest {

    private static ObjectProvider<DebateAdviceGeneratorService> providerOf(
            DebateAdviceGeneratorService instance) {
        @SuppressWarnings("unchecked")
        ObjectProvider<DebateAdviceGeneratorService> p =
                (ObjectProvider<DebateAdviceGeneratorService>) mock(ObjectProvider.class);
        lenient().when(p.getIfAvailable()).thenReturn(instance);
        return p;
    }

    private static AuditHistoryEntity rowWith(double z, int stage) {
        AuditHistoryEntity a = new AuditHistoryEntity();
        a.setModifiedZScore(z);
        a.setVisibilityStage(stage);
        return a;
    }

    private static ProjectAdviceContext context() {
        return new ProjectAdviceContext(IndustryType.B2B, "ターゲット", "強み");
    }

    @Test
    void rollupJobWithProjectDelegatesToAiGenerator() {
        DebateAdviceGeneratorService generator = mock(DebateAdviceGeneratorService.class);
        StrategyInsight aiResult = new StrategyInsight("AI生成診断", List.of("a", "b", "c"), 0.3);
        when(generator.generateForJob(any(), any(), any())).thenReturn(aiResult);

        StrategyInsightService svc = new StrategyInsightService(providerOf(generator));

        StrategyInsight result =
                svc.rollupJob(List.of(rowWith(0.3, 5)), context(), SubscriptionPlan.STANDARD);

        assertThat(result.diagnosticMessage()).isEqualTo("AI生成診断");
        verify(generator, times(1)).generateForJob(any(), any(), any());
    }

    @Test
    void rollupJobFallsBackToTemplateWhenGeneratorThrows() {
        DebateAdviceGeneratorService generator = mock(DebateAdviceGeneratorService.class);
        when(generator.generateForJob(any(), any(), any()))
                .thenThrow(
                        new DebateAdviceGeneratorService.DebateAdviceGenerationException(
                                "boom"));

        StrategyInsightService svc = new StrategyInsightService(providerOf(generator));

        StrategyInsight result =
                svc.rollupJob(List.of(rowWith(0.0, 5)), context(), SubscriptionPlan.STANDARD);

        // テンプレフォールバック発動 → 改Z' 0.0 は MSG_REDOCEAN テンプレに該当
        assertThat(result.diagnosticMessage()).contains("レッドオーシャン");
        assertThat(result.recommendedActions()).isNotEmpty();
    }

    @Test
    void rollupJobFallsBackToTemplateWhenGeneratorUnavailable() {
        StrategyInsightService svc = new StrategyInsightService(providerOf(null));

        StrategyInsight result =
                svc.rollupJob(List.of(rowWith(0.0, 5)), context(), SubscriptionPlan.STANDARD);

        assertThat(result.diagnosticMessage()).isNotNull();
        assertThat(result.recommendedActions()).isNotEmpty();
    }

    @Test
    void rollupJobFallsBackToTemplateWhenRowsEmpty() {
        DebateAdviceGeneratorService generator = mock(DebateAdviceGeneratorService.class);
        StrategyInsightService svc = new StrategyInsightService(providerOf(generator));

        StrategyInsight result = svc.rollupJob(List.of(), context(), SubscriptionPlan.STANDARD);

        assertThat(result.diagnosticMessage()).isNull();
        verify(generator, never()).generateForJob(any(), any(), any());
    }

    @Test
    void rollupJobFallsBackToTemplateWhenProjectNull() {
        DebateAdviceGeneratorService generator = mock(DebateAdviceGeneratorService.class);
        StrategyInsightService svc = new StrategyInsightService(providerOf(generator));

        StrategyInsight result =
                svc.rollupJob(List.of(rowWith(0.0, 5)), null, SubscriptionPlan.STANDARD);

        assertThat(result.diagnosticMessage()).isNotNull();
        verify(generator, never()).generateForJob(any(), any(), any());
    }

    @Test
    void legacyRollupJobSingleArgUsesTemplateOnly() {
        DebateAdviceGeneratorService generator = mock(DebateAdviceGeneratorService.class);
        StrategyInsightService svc = new StrategyInsightService(providerOf(generator));

        StrategyInsight result = svc.rollupJob(List.of(rowWith(2.5, 1)));

        // 後方互換 API は AI 駆動には行かない
        assertThat(result.diagnosticMessage()).contains("市場の支配者");
        verify(generator, never()).generateForJob(any(), any(), any());
    }

    @Test
    void rollupJobFromTemplateReturnsBlindspotForLowModifiedZ() {
        StrategyInsightService svc = new StrategyInsightService(providerOf(null));

        StrategyInsight result =
                svc.rollupJobFromTemplate(List.of(rowWith(-2.0, 9), rowWith(-1.5, 8)));

        assertThat(result.diagnosticMessage()).contains("デジタル上の死角");
    }

    private static AuditHistoryEntity rowWithSom(double som, Integer aiPos) {
        AuditHistoryEntity a = new AuditHistoryEntity();
        a.setSomScore(som);
        a.setAiCitationPosition(aiPos);
        return a;
    }

    @Test
    void describeForQuery_embedsActualSomScore_notTemplate() {
        StrategyInsightService svc = new StrategyInsightService(providerOf(null));
        StrategyInsight low = svc.describeForQuery(12.0, null);
        StrategyInsight high = svc.describeForQuery(45.0, 1);
        // 実測値（SoM 点・ティア）が文に埋め込まれる
        assertThat(low.diagnosticMessage()).contains("12.0").contains("Challenger");
        assertThat(high.diagnosticMessage()).contains("45.0").contains("Market Leader");
        // SoM が違えば文も変わる＝定型文ではない
        assertThat(low.diagnosticMessage()).isNotEqualTo(high.diagnosticMessage());
    }

    @Test
    void describeForQuery_reflectsCitationPosition() {
        StrategyInsightService svc = new StrategyInsightService(providerOf(null));
        StrategyInsight ranked = svc.describeForQuery(20.0, 2);
        StrategyInsight unranked = svc.describeForQuery(20.0, null);
        assertThat(ranked.diagnosticMessage()).contains("2番目");
        assertThat(unranked.diagnosticMessage()).contains("未掲載");
        assertThat(ranked.diagnosticMessage()).isNotEqualTo(unranked.diagnosticMessage());
    }

    @Test
    void rollupJobFromTemplate_withSomScores_embedsCountAndMedian() {
        StrategyInsightService svc = new StrategyInsightService(providerOf(null));
        StrategyInsight r = svc.rollupJobFromTemplate(
                List.of(rowWithSom(12.0, null), rowWithSom(20.0, 1), rowWithSom(8.0, null)));
        // クエリ数（3件）が文に入る＝定型文ではない
        assertThat(r.diagnosticMessage()).contains("3件");
    }

    @Test
    void describeForQuery_withSiteTasks_usesTaskInAdviceAndActions() {
        StrategyInsightService svc = new StrategyInsightService(providerOf(null));
        StrategyInsight r = svc.describeForQuery(
                20.0, 1, List.of("Schema.orgのProduct構造化", "H1見出しの追加", "llms.txtの整備"));
        // サイト固有タスクが後半アドバイスに昇格し、推奨アクションもタスクベースになる
        assertThat(r.diagnosticMessage()).contains("Schema.orgのProduct構造化");
        assertThat(r.recommendedActions()).containsExactly(
                "Schema.orgのProduct構造化", "H1見出しの追加", "llms.txtの整備");
    }

    @Test
    void describeForQuery_withEmptySiteTasks_fallsBackToTierTemplate() {
        StrategyInsightService svc = new StrategyInsightService(providerOf(null));
        StrategyInsight withTasks = svc.describeForQuery(20.0, 1, List.of());
        StrategyInsight legacy = svc.describeForQuery(20.0, 1);
        // タスク空のときは従来の帯テンプレと完全一致（フォールバックで劣化しない）
        assertThat(withTasks.diagnosticMessage()).isEqualTo(legacy.diagnosticMessage());
        assertThat(withTasks.recommendedActions()).isEqualTo(legacy.recommendedActions());
    }

    @Test
    void describeForQuery_siteTasks_areCappedAtThreeAndDeduplicated() {
        StrategyInsightService svc = new StrategyInsightService(providerOf(null));
        StrategyInsight r = svc.describeForQuery(
                20.0, 1, List.of("A", "A", "B", "C", "D", "  "));
        assertThat(r.recommendedActions()).containsExactly("A", "B", "C");
    }
}
