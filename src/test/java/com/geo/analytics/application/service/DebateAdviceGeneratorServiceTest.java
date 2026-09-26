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
import com.geo.analytics.domain.enums.RoadmapPhase;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.enums.TaskCategory;
import com.geo.analytics.domain.enums.TaskPriority;
import com.geo.analytics.domain.model.RemediationTask;
import com.geo.analytics.domain.model.RoadmapItem;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
            "{\"diagnostic_message\":\"B2B向けに独自データセットを活かすべき。\"}";

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
                svc.generateForJob(List.of(rowWith(0.5, 4)), billingContext(), SubscriptionPlan.PRO, List.of()).insight();

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
                svc.generateForJob(List.of(rowWith(0.5, 4)), billingContext(), SubscriptionPlan.PRO, List.of()).insight();

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
                svc.generateForJob(List.of(rowWith(0.5, 4)), context(), SubscriptionPlan.PRO, List.of()).insight();

        assertThat(result.diagnosticMessage()).contains("B2B");
        verify(persona, never()).chat(any(ChatRequest.class));
        verify(credit, never()).reserve(any(), eq(DebateAdviceGeneratorService.DEBATE_CREDIT));
    }

    @Test
    void emptyRowsReturnsEmptyInsightWithoutCallingLlm() {
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        DebateAdviceGeneratorService svc = newService(model);

        StrategyInsight result = svc.generateForJob(List.of(), context(), SubscriptionPlan.STANDARD, List.of()).insight();

        assertThat(result.diagnosticMessage()).isNull();
        assertThat(result.recommendedActions()).isEmpty();
        verify(model, never()).chat(any(ChatRequest.class));
    }

    @Test
    void successfulLlmReturnsParsedInsight() {
        ChatLanguageModel model = modelReturning(VALID_JSON);
        DebateAdviceGeneratorService svc = newService(model);

        StrategyInsight result =
                svc.generateForJob(List.of(rowWith(0.5, 4)), context(), SubscriptionPlan.STANDARD, List.of()).insight();

        assertThat(result.diagnosticMessage()).contains("B2B");
    }

    /** #141: 推奨アクションは作らせない。旧形式の応答に含まれていても捨てる。 */
    @Test
    void recommendedActionsInLegacyResponseAreIgnored() {
        String json =
                "{\"diagnostic_message\":\"B2B向けに独自データセットを活かすべき。\","
                        + "\"recommended_actions\":[\"事例公開\",\"統計データ提供\",\"PR記事\"]}";
        ChatLanguageModel model = modelReturning(json);
        DebateAdviceGeneratorService svc = newService(model);

        StrategyInsight result =
                svc.generateForJob(List.of(rowWith(0.5, 4)), context(), SubscriptionPlan.STANDARD, List.of()).insight();

        assertThat(result.diagnosticMessage()).contains("B2B");
        assertThat(result.recommendedActions()).isEmpty();
    }

    @Test
    void minorityReportsAreParsedTrimmedAndCapped() {
        String json =
                "{\"diagnostic_message\":\"B2B向けに独自データセットを活かすべき。\","
                        + "\"recommended_actions\":[\"事例公開\",\"統計データ提供\",\"PR記事\"],"
                        + "\"minority_reports\":["
                        + "{\"insight\":\" 業界統計を自社で作る \",\"conflict_reason\":\"工数が読めない\","
                        + "\"evidence\":\"強み: 独自データ\"},"
                        + "{\"insight\":\"\",\"conflict_reason\":\"空なので落ちる\",\"evidence\":\"\"},"
                        + "{\"insight\":\"二件目\",\"conflict_reason\":\"理由2\",\"evidence\":\"根拠2\"},"
                        + "{\"insight\":\"三件目は上限超過で落ちる\",\"conflict_reason\":\"r\",\"evidence\":\"e\"}]}";
        ChatLanguageModel model = modelReturning(json);
        DebateAdviceGeneratorService svc = newService(model);

        var advice = svc.generateForJob(List.of(rowWith(0.5, 4)), context(), SubscriptionPlan.STANDARD, List.of());

        assertThat(advice.minorityReports()).hasSize(2);
        assertThat(advice.minorityReports().getFirst().insight()).isEqualTo("業界統計を自社で作る");
        assertThat(advice.minorityReports().getFirst().conflictReason()).isEqualTo("工数が読めない");
        assertThat(advice.minorityReports().getLast().insight()).isEqualTo("二件目");
    }

    @Test
    void missingMinorityReportsFieldYieldsEmptyList() {
        ChatLanguageModel model = modelReturning(VALID_JSON);
        DebateAdviceGeneratorService svc = newService(model);

        var advice = svc.generateForJob(List.of(rowWith(0.5, 4)), context(), SubscriptionPlan.STANDARD, List.of());

        assertThat(advice.minorityReports()).isEmpty();
    }

    @Test
    void roadmapItemsAreParsedSortedByPhaseAndCapped() {
        String json =
                "{\"diagnostic_message\":\"B2B向けに独自データセットを活かすべき。\","
                        + "\"recommended_actions\":[\"事例公開\",\"統計データ提供\",\"PR記事\"],"
                        + "\"roadmap_items\":["
                        + "{\"phase\":\"MID_TERM\",\"title\":\"業界レポートの定期刊行\","
                        + "\"rationale\":\"一次情報の蓄積が要る\",\"expected_impact\":\"引用元としての定着\"},"
                        + "{\"phase\":\"NOW\",\"title\":\" 構造化データの整備 \","
                        + "\"rationale\":\"最短で効く\",\"expected_impact\":\"AI回答での認識率向上\"},"
                        + "{\"phase\":\"UNKNOWN_PHASE\",\"title\":\"未知フェーズは落ちる\","
                        + "\"rationale\":\"r\",\"expected_impact\":\"e\"},"
                        + "{\"phase\":\"SHORT_TERM\",\"title\":\"事例ページの拡充\","
                        + "\"rationale\":\"中間の打ち手\",\"expected_impact\":\"比較文脈での露出\"}]}";
        ChatLanguageModel model = modelReturning(json);
        DebateAdviceGeneratorService svc = newService(model);

        var advice = svc.generateForJob(List.of(rowWith(0.5, 4)), context(), SubscriptionPlan.STANDARD, List.of());

        assertThat(advice.roadmapItems()).hasSize(3);
        assertThat(advice.roadmapItems().stream().map(i -> i.phase().name()).toList())
                .containsExactly("NOW", "SHORT_TERM", "MID_TERM");
        assertThat(advice.roadmapItems().getFirst().title()).isEqualTo("構造化データの整備");
    }

    @Test
    void missingRoadmapItemsFieldYieldsEmptyList() {
        ChatLanguageModel model = modelReturning(VALID_JSON);
        DebateAdviceGeneratorService svc = newService(model);

        var advice = svc.generateForJob(List.of(rowWith(0.5, 4)), context(), SubscriptionPlan.STANDARD, List.of());

        assertThat(advice.roadmapItems()).isEmpty();
    }

    @Test
    void codeFencedJsonIsStripped() {
        String json =
                "```json\n{\"diagnostic_message\":\"テスト診断\",\"recommended_actions\":[\"a\",\"b\",\"c\"]}\n```";
        ChatLanguageModel model = modelReturning(json);
        DebateAdviceGeneratorService svc = newService(model);

        StrategyInsight result =
                svc.generateForJob(List.of(rowWith(0.0, 6)), context(), SubscriptionPlan.STANDARD, List.of()).insight();

        assertThat(result.diagnosticMessage()).isEqualTo("テスト診断");
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
                                        SubscriptionPlan.STANDARD, List.of()))
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
                                        SubscriptionPlan.STANDARD, List.of()))
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
                                        SubscriptionPlan.STANDARD, List.of()))
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
                svc.generateForJob(List.of(rowWith(0.0, 5)), context(), SubscriptionPlan.STANDARD, List.of()).insight();

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
                                        SubscriptionPlan.STANDARD, List.of()))
                .isInstanceOf(DebateAdviceGeneratorService.DebateAdviceGenerationException.class)
                .hasMessageContaining("JSON parse failed");
    }

    private static RemediationTask task(TaskPriority priority, TaskCategory category, String title) {
        return new RemediationTask(UUID.randomUUID(), category, priority, title, "1. 手順", 0.5d, "理由", "根拠");
    }

    private static List<RemediationTask> tasks(int count) {
        List<RemediationTask> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(task(TaskPriority.S, TaskCategory.SPIKE, "タスク" + (i + 1)));
        }
        return out;
    }

    private static String roadmapJson(String... items) {
        return "{\"diagnostic_message\":\"B2B向けに独自データセットを活かすべき。\",\"roadmap_items\":["
                + String.join(",", items) + "]}";
    }

    private static String phase(String phase, int lastTask) {
        return "{\"phase\":\"" + phase + "\",\"last_task_number\":" + lastTask
                + ",\"title\":\"" + phase + "の目標\",\"rationale\":\"r\",\"expected_impact\":\"e\"}";
    }

    private static List<int[]> ranges(List<RoadmapItem> items) {
        return items.stream().map(i -> new int[] {i.firstTaskNumber(), i.lastTaskNumber()}).toList();
    }

    /** #141: ロードマップは改善タスクの時間割。AI が返した区切りどおりに連続した番号の範囲を持つ。 */
    @Test
    void roadmapWithTasksGetsContiguousTaskRanges() {
        ChatLanguageModel model =
                modelReturning(roadmapJson(phase("MID_TERM", 5), phase("NOW", 2), phase("SHORT_TERM", 4)));
        DebateAdviceGeneratorService svc = newService(model);

        var advice = svc.generateForJob(List.of(rowWith(0.5, 4)), context(), SubscriptionPlan.STANDARD, tasks(5));

        assertThat(advice.roadmapItems().stream().map(RoadmapItem::phase).toList())
                .containsExactly(RoadmapPhase.NOW, RoadmapPhase.SHORT_TERM, RoadmapPhase.MID_TERM);
        assertThat(ranges(advice.roadmapItems()))
                .containsExactly(new int[] {1, 2}, new int[] {3, 4}, new int[] {5, 5});
    }

    /** #141: 逆順・抜けのある番号は、前のフェーズの続きから始まるよう詰め、最後のフェーズを最後のタスクで閉じる。 */
    @Test
    void roadmapTaskRangesAreRepairedWhenBackwardsOrShort() {
        ChatLanguageModel model =
                modelReturning(roadmapJson(phase("NOW", 3), phase("SHORT_TERM", 2), phase("MID_TERM", 4)));
        DebateAdviceGeneratorService svc = newService(model);

        var advice = svc.generateForJob(List.of(rowWith(0.5, 4)), context(), SubscriptionPlan.STANDARD, tasks(6));

        assertThat(ranges(advice.roadmapItems()))
                .containsExactly(new int[] {1, 3}, new int[] {4, 4}, new int[] {5, 6});
    }

    /** #141: タスクを使い切ったあとのフェーズと、同じフェーズの2件目は時間割が二重になるため落とす。 */
    @Test
    void roadmapDropsPhasesAfterTasksRunOutAndDuplicatePhases() {
        ChatLanguageModel model = modelReturning(
                roadmapJson(phase("NOW", 1), phase("NOW", 2), phase("SHORT_TERM", 9), phase("MID_TERM", 9)));
        DebateAdviceGeneratorService svc = newService(model);

        var advice = svc.generateForJob(List.of(rowWith(0.5, 4)), context(), SubscriptionPlan.STANDARD, tasks(3));

        assertThat(advice.roadmapItems().stream().map(RoadmapItem::phase).toList())
                .containsExactly(RoadmapPhase.NOW, RoadmapPhase.SHORT_TERM);
        assertThat(ranges(advice.roadmapItems())).containsExactly(new int[] {1, 1}, new int[] {2, 3});
    }

    /** 改善タスクが無い解析では、ロードマップは番号を持たない従来形のまま。 */
    @Test
    void roadmapWithoutTasksHasNoTaskRanges() {
        ChatLanguageModel model = modelReturning(roadmapJson(phase("NOW", 0), phase("SHORT_TERM", 0)));
        DebateAdviceGeneratorService svc = newService(model);

        var advice = svc.generateForJob(List.of(rowWith(0.5, 4)), context(), SubscriptionPlan.STANDARD, List.of());

        assertThat(advice.roadmapItems()).hasSize(2);
        assertThat(advice.roadmapItems()).allSatisfy(i -> {
            assertThat(i.firstTaskNumber()).isNull();
            assertThat(i.lastTaskNumber()).isNull();
        });
    }

    /** #141: DIRECTOR に改善タスクを取り組む順の番号付きで渡し、時間割として組ませる。推奨アクションは求めない。 */
    @Test
    void directorPromptCarriesNumberedTasksAndNoRecommendedActions() {
        ChatLanguageModel model = modelReturning(VALID_JSON);
        DebateAdviceGeneratorService svc = newService(model);
        List<RemediationTask> ordered = List.of(
                task(TaskPriority.S, TaskCategory.SPIKE, "料金の目安を書く"),
                task(TaskPriority.S, TaskCategory.SLAB, "料金ページを作る"));

        svc.generateForJob(List.of(rowWith(0.5, 4)), context(), SubscriptionPlan.STANDARD, ordered);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(model).chat(captor.capture());
        String system = ((SystemMessage) captor.getValue().messages().get(0)).text();
        String user = ((UserMessage) captor.getValue().messages().get(1)).singleText();
        assertThat(user)
                .contains("【改善タスク（全2件。番号は取り組む順）】")
                .contains("1. [効果 大・すぐ直せる] 料金の目安を書く")
                .contains("2. [効果 大・時間がかかる] 料金ページを作る");
        assertThat(system).contains("時間割").contains("last_task_number").doesNotContain("recommended_actions");
    }
}
