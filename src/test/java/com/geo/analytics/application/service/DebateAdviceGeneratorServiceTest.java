package com.geo.analytics.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.ProjectAdviceContext;
import com.geo.analytics.application.dto.StrategyInsight;
import com.geo.analytics.domain.entity.AuditHistoryEntity;
import com.geo.analytics.domain.enums.IndustryType;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DebateAdviceGeneratorServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** STANDARD（Free パス）用: director のみ実モック、ペルソナ・課金は使われない。 */
    private DebateAdviceGeneratorService newService(ChatLanguageModel directorModel) {
        return newService(directorModel, mock(ChatLanguageModel.class), mock(CreditVaultService.class));
    }

    private DebateAdviceGeneratorService newService(
            ChatLanguageModel directorModel,
            ChatLanguageModel personaModel,
            CreditVaultService creditVaultService) {
        // StrategyInsightService は ObjectProvider 経由で DebateAdviceGenerator を遅延解決するため、
        // ここでは generator -> insight の単方向参照だけ満たせばよい。空 ObjectProvider を渡す。
        StrategyInsightService insight = new StrategyInsightService(emptyProvider());
        return new DebateAdviceGeneratorService(
                directorModel,
                personaModel,
                personaModel,
                personaModel,
                objectMapper,
                insight,
                creditVaultService);
    }

    private static org.springframework.beans.factory.ObjectProvider<DebateAdviceGeneratorService>
            emptyProvider() {
        @SuppressWarnings("unchecked")
        var p =
                (org.springframework.beans.factory.ObjectProvider<DebateAdviceGeneratorService>)
                        mock(org.springframework.beans.factory.ObjectProvider.class);
        lenient().when(p.getIfAvailable()).thenReturn(null);
        return p;
    }

    private static ChatLanguageModel modelReturning(String responseText) {
        ChatLanguageModel m = mock(ChatLanguageModel.class);
        ChatResponse response = ChatResponse.builder().aiMessage(AiMessage.from(responseText)).build();
        when(m.chat(any(ChatRequest.class))).thenReturn(response);
        return m;
    }

    private static ChatLanguageModel modelThrowing(RuntimeException t) {
        ChatLanguageModel m = mock(ChatLanguageModel.class);
        when(m.chat(any(ChatRequest.class))).thenThrow(t);
        return m;
    }

    private static AuditHistoryEntity rowWith(double z, int stage) {
        AuditHistoryEntity a = new AuditHistoryEntity();
        a.setModifiedZScore(z);
        a.setVisibilityStage(stage);
        return a;
    }

    private static ProjectAdviceContext context() {
        return new ProjectAdviceContext(
                IndustryType.B2B, "中小企業のマーケ責任者", "独自データセット\n業界10年の知見");
    }

    /** 課金識別子（project/workspace/organization）を備えた Pro/Expert 用コンテキスト。 */
    private static ProjectAdviceContext billingContext() {
        return new ProjectAdviceContext(
                IndustryType.B2B,
                "中小企業のマーケ責任者",
                "独自データセット\n業界10年の知見",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID());
    }

    private static final String VALID_JSON =
            "{\"diagnostic_message\":\"B2B向けに独自データセットを活かすべき。\","
                    + "\"recommended_actions\":[\"事例公開\",\"統計データ提供\",\"PR記事\"]}";

    @Test
    void proPlanRunsShortDebateAndSettlesTicket() {
        ChatLanguageModel director = modelReturning(VALID_JSON);
        ChatLanguageModel persona = modelReturning("ペルソナの主張テキスト");
        CreditVaultService credit = mock(CreditVaultService.class);
        UUID reservationId = UUID.randomUUID();
        when(credit.reserve(any(), eq(DebateAdviceGeneratorService.DEBATE_CREDIT)))
                .thenReturn(reservationId);
        DebateAdviceGeneratorService svc = newService(director, persona, credit);

        StrategyInsight result =
                svc.generateForJob(List.of(rowWith(0.5, 4)), billingContext(), SubscriptionPlan.PRO);

        assertThat(result.diagnosticMessage()).contains("B2B");
        // 2 ターン × 3 ペルソナ = 6 回の議論 LLM 呼び出し
        verify(persona, times(DebateAdviceGeneratorService.SHORT_DEBATE_TURNS * 3))
                .chat(any(ChatRequest.class));
        // 議論起動につき 1 回予約・成功で精算（返金なし）
        verify(credit).reserve(any(), eq(DebateAdviceGeneratorService.DEBATE_CREDIT));
        verify(credit).settle(eq(reservationId), eq(DebateAdviceGeneratorService.DEBATE_CREDIT), any());
        verify(credit, never()).refund(any());
    }

    @Test
    void proPlanDebateFailureRefundsAndFallsBackToSingleShot() {
        // director は最初の議論注入経路では失敗、フォールバックの単発でも同じモデルを使うため
        // ここでは「議論パスでも単発パスでも成功する director」を使い、ペルソナ側で失敗させる。
        ChatLanguageModel director = modelReturning(VALID_JSON);
        ChatLanguageModel persona = modelThrowing(new RuntimeException("debate llm down"));
        CreditVaultService credit = mock(CreditVaultService.class);
        UUID reservationId = UUID.randomUUID();
        when(credit.reserve(any(), eq(DebateAdviceGeneratorService.DEBATE_CREDIT)))
                .thenReturn(reservationId);
        DebateAdviceGeneratorService svc = newService(director, persona, credit);

        StrategyInsight result =
                svc.generateForJob(List.of(rowWith(0.5, 4)), billingContext(), SubscriptionPlan.PRO);

        // 議論は失敗したが Free パス（単発 director）で結果が返る
        assertThat(result.diagnosticMessage()).contains("B2B");
        // 全額返金され、精算はされない（オーナー決定 2026-05-30）
        verify(credit).reserve(any(), eq(DebateAdviceGeneratorService.DEBATE_CREDIT));
        verify(credit).refund(reservationId);
        verify(credit, never())
                .settle(any(), eq(DebateAdviceGeneratorService.DEBATE_CREDIT), any());
    }

    @Test
    void proPlanWithoutBillingIdentitySkipsDebateAndDoesNotCharge() {
        ChatLanguageModel director = modelReturning(VALID_JSON);
        ChatLanguageModel persona = modelReturning("使われないはず");
        CreditVaultService credit = mock(CreditVaultService.class);
        DebateAdviceGeneratorService svc = newService(director, persona, credit);

        // 課金識別子を持たない context() を渡すと、PRO でも議論は起動しない
        StrategyInsight result =
                svc.generateForJob(List.of(rowWith(0.5, 4)), context(), SubscriptionPlan.PRO);

        assertThat(result.diagnosticMessage()).contains("B2B");
        verify(persona, never()).chat(any(ChatRequest.class));
        verify(credit, never()).reserve(any(), eq(DebateAdviceGeneratorService.DEBATE_CREDIT));
    }

    @Test
    void emptyRowsReturnsEmptyInsightWithoutCallingLlm() {
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        DebateAdviceGeneratorService svc = newService(model);

        StrategyInsight result = svc.generateForJob(List.of(), context(), SubscriptionPlan.STANDARD);

        assertThat(result.diagnosticMessage()).isNull();
        assertThat(result.recommendedActions()).isEmpty();
        verify(model, never()).chat(any(ChatRequest.class));
    }

    @Test
    void successfulLlmReturnsParsedInsight() {
        String json =
                "{\"diagnostic_message\":\"B2B向けに独自データセットを活かすべき。\","
                        + "\"recommended_actions\":[\"事例公開\",\"統計データ提供\",\"PR記事\"]}";
        ChatLanguageModel model = modelReturning(json);
        DebateAdviceGeneratorService svc = newService(model);

        StrategyInsight result =
                svc.generateForJob(List.of(rowWith(0.5, 4)), context(), SubscriptionPlan.STANDARD);

        assertThat(result.diagnosticMessage()).contains("B2B");
        assertThat(result.recommendedActions()).hasSize(3);
    }

    @Test
    void codeFencedJsonIsStripped() {
        String json =
                "```json\n{\"diagnostic_message\":\"テスト診断\",\"recommended_actions\":[\"a\",\"b\",\"c\"]}\n```";
        ChatLanguageModel model = modelReturning(json);
        DebateAdviceGeneratorService svc = newService(model);

        StrategyInsight result =
                svc.generateForJob(List.of(rowWith(0.0, 6)), context(), SubscriptionPlan.STANDARD);

        assertThat(result.diagnosticMessage()).isEqualTo("テスト診断");
        assertThat(result.recommendedActions()).containsExactly("a", "b", "c");
    }

    @Test
    void llmExceptionWrapsToDebateAdviceGenerationException() {
        ChatLanguageModel model = modelThrowing(new RuntimeException("network down"));
        DebateAdviceGeneratorService svc = newService(model);

        assertThatThrownBy(
                        () ->
                                svc.generateForJob(
                                        List.of(rowWith(-0.5, 8)),
                                        context(),
                                        SubscriptionPlan.STANDARD))
                .isInstanceOf(DebateAdviceGeneratorService.DebateAdviceGenerationException.class)
                .hasMessageContaining("LLM call failed");
    }

    @Test
    void invalidJsonWrapsToDebateAdviceGenerationException() {
        ChatLanguageModel model = modelReturning("not a json at all");
        DebateAdviceGeneratorService svc = newService(model);

        assertThatThrownBy(
                        () ->
                                svc.generateForJob(
                                        List.of(rowWith(0.0, 5)),
                                        context(),
                                        SubscriptionPlan.STANDARD))
                .isInstanceOf(DebateAdviceGeneratorService.DebateAdviceGenerationException.class)
                .hasMessageContaining("JSON parse failed");
    }

    @Test
    void emptyDiagnosticMessageThrowsException() {
        String json =
                "{\"diagnostic_message\":\"\",\"recommended_actions\":[\"a\",\"b\",\"c\"]}";
        ChatLanguageModel model = modelReturning(json);
        DebateAdviceGeneratorService svc = newService(model);

        assertThatThrownBy(
                        () ->
                                svc.generateForJob(
                                        List.of(rowWith(0.0, 5)),
                                        context(),
                                        SubscriptionPlan.STANDARD))
                .isInstanceOf(DebateAdviceGeneratorService.DebateAdviceGenerationException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void diagnosticOver300CharsIsTruncated() {
        String longMsg = "あ".repeat(400);
        String json =
                "{\"diagnostic_message\":\""
                        + longMsg
                        + "\",\"recommended_actions\":[\"a\",\"b\",\"c\"]}";
        ChatLanguageModel model = modelReturning(json);
        DebateAdviceGeneratorService svc = newService(model);

        StrategyInsight result =
                svc.generateForJob(List.of(rowWith(0.0, 5)), context(), SubscriptionPlan.STANDARD);

        assertThat(result.diagnosticMessage()).hasSize(300);
    }

    @Test
    void codeFenceWithoutNewlineDoesNotCrashAndFailsGracefully() {
        // 監査指摘: ```json{...}``` のように改行を含まないコードフェンスはコード上
        // firstNl > 0 条件を満たさず、バッククォート文字列が JSON パーサーに渡る。
        // → DebateAdviceGenerationException で適切にラップされることを保証する。
        String noNewlineFenced =
                "```json{\"diagnostic_message\":\"ok\",\"recommended_actions\":[\"a\",\"b\",\"c\"]}```";
        ChatLanguageModel model = modelReturning(noNewlineFenced);
        DebateAdviceGeneratorService svc = newService(model);

        assertThatThrownBy(
                        () ->
                                svc.generateForJob(
                                        List.of(rowWith(0.0, 5)),
                                        context(),
                                        SubscriptionPlan.STANDARD))
                .isInstanceOf(DebateAdviceGeneratorService.DebateAdviceGenerationException.class)
                .hasMessageContaining("JSON parse failed");
    }

    @Test
    void actionsOver60CharsAreTruncatedAndLimitedTo3() {
        String longAction = "あ".repeat(100);
        String json =
                "{\"diagnostic_message\":\"ok\",\"recommended_actions\":[\""
                        + longAction
                        + "\",\"normal\",\"third\",\"fourth\"]}";
        ChatLanguageModel model = modelReturning(json);
        DebateAdviceGeneratorService svc = newService(model);

        StrategyInsight result =
                svc.generateForJob(List.of(rowWith(0.0, 5)), context(), SubscriptionPlan.STANDARD);

        assertThat(result.recommendedActions()).hasSize(3);
        assertThat(result.recommendedActions().get(0)).hasSize(60);
    }
}
