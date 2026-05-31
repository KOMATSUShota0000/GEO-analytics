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
}
