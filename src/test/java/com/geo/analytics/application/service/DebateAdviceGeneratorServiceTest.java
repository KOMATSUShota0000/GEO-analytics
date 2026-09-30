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
import com.geo.analytics.application.dto.DebateJobFacts;
import com.geo.analytics.application.dto.ProjectAdviceContext;
import com.geo.analytics.application.dto.StrategyInsight;
import com.geo.analytics.domain.ai.DebatePersona;
import com.geo.analytics.domain.entity.AuditHistoryEntity;
import com.geo.analytics.domain.enums.DebateEvidenceKind;
import com.geo.analytics.domain.enums.DebateStance;
import com.geo.analytics.domain.enums.IndustryType;
import com.geo.analytics.domain.enums.RoadmapPhase;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.enums.TaskCategory;
import com.geo.analytics.domain.enums.TaskPriority;
import com.geo.analytics.domain.model.DebateUtterance;
import com.geo.analytics.domain.model.RemediationTask;
import com.geo.analytics.domain.model.RoadmapItem;
import com.geo.analytics.domain.prompt.DebatePersonaSystemPrompts;
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
                svc.generateForJob(List.of(rowWith(0.5, 4)), billingContext(), SubscriptionPlan.PRO, List.of(), null).insight();

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
                svc.generateForJob(List.of(rowWith(0.5, 4)), billingContext(), SubscriptionPlan.PRO, List.of(), null).insight();

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
                svc.generateForJob(List.of(rowWith(0.5, 4)), context(), SubscriptionPlan.PRO, List.of(), null).insight();

        assertThat(result.diagnosticMessage()).contains("B2B");
        verify(persona, never()).chat(any(ChatRequest.class));
        verify(credit, never()).reserve(any(), eq(DebateAdviceGeneratorService.DEBATE_CREDIT));
    }

    @Test
    void emptyRowsReturnsEmptyInsightWithoutCallingLlm() {
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        DebateAdviceGeneratorService svc = newService(model);

        StrategyInsight result = svc.generateForJob(List.of(), context(), SubscriptionPlan.STANDARD, List.of(), null).insight();

        assertThat(result.diagnosticMessage()).isNull();
        assertThat(result.recommendedActions()).isEmpty();
        verify(model, never()).chat(any(ChatRequest.class));
    }

    @Test
    void successfulLlmReturnsParsedInsight() {
        ChatLanguageModel model = modelReturning(VALID_JSON);
        DebateAdviceGeneratorService svc = newService(model);

        StrategyInsight result =
                svc.generateForJob(List.of(rowWith(0.5, 4)), context(), SubscriptionPlan.STANDARD, List.of(), null).insight();

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
                svc.generateForJob(List.of(rowWith(0.5, 4)), context(), SubscriptionPlan.STANDARD, List.of(), null).insight();

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

        var advice = svc.generateForJob(List.of(rowWith(0.5, 4)), context(), SubscriptionPlan.STANDARD, List.of(), null);

        assertThat(advice.minorityReports()).hasSize(2);
        assertThat(advice.minorityReports().getFirst().insight()).isEqualTo("業界統計を自社で作る");
        assertThat(advice.minorityReports().getFirst().conflictReason()).isEqualTo("工数が読めない");
        assertThat(advice.minorityReports().getLast().insight()).isEqualTo("二件目");
    }

    @Test
    void missingMinorityReportsFieldYieldsEmptyList() {
        ChatLanguageModel model = modelReturning(VALID_JSON);
        DebateAdviceGeneratorService svc = newService(model);

        var advice = svc.generateForJob(List.of(rowWith(0.5, 4)), context(), SubscriptionPlan.STANDARD, List.of(), null);

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

        var advice = svc.generateForJob(List.of(rowWith(0.5, 4)), context(), SubscriptionPlan.STANDARD, List.of(), null);

        assertThat(advice.roadmapItems()).hasSize(3);
        assertThat(advice.roadmapItems().stream().map(i -> i.phase().name()).toList())
                .containsExactly("NOW", "SHORT_TERM", "MID_TERM");
        assertThat(advice.roadmapItems().getFirst().title()).isEqualTo("構造化データの整備");
    }

    @Test
    void missingRoadmapItemsFieldYieldsEmptyList() {
        ChatLanguageModel model = modelReturning(VALID_JSON);
        DebateAdviceGeneratorService svc = newService(model);

        var advice = svc.generateForJob(List.of(rowWith(0.5, 4)), context(), SubscriptionPlan.STANDARD, List.of(), null);

        assertThat(advice.roadmapItems()).isEmpty();
    }

    @Test
    void codeFencedJsonIsStripped() {
        String json =
                "```json\n{\"diagnostic_message\":\"テスト診断\",\"recommended_actions\":[\"a\",\"b\",\"c\"]}\n```";
        ChatLanguageModel model = modelReturning(json);
        DebateAdviceGeneratorService svc = newService(model);

        StrategyInsight result =
                svc.generateForJob(List.of(rowWith(0.0, 6)), context(), SubscriptionPlan.STANDARD, List.of(), null).insight();

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
                                        SubscriptionPlan.STANDARD, List.of(), null))
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
                                        SubscriptionPlan.STANDARD, List.of(), null))
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
                                        SubscriptionPlan.STANDARD, List.of(), null))
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
                svc.generateForJob(List.of(rowWith(0.0, 5)), context(), SubscriptionPlan.STANDARD, List.of(), null).insight();

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
                                        SubscriptionPlan.STANDARD, List.of(), null))
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

        var advice = svc.generateForJob(List.of(rowWith(0.5, 4)), context(), SubscriptionPlan.STANDARD, tasks(5), null);

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

        var advice = svc.generateForJob(List.of(rowWith(0.5, 4)), context(), SubscriptionPlan.STANDARD, tasks(6), null);

        assertThat(ranges(advice.roadmapItems()))
                .containsExactly(new int[] {1, 3}, new int[] {4, 4}, new int[] {5, 6});
    }

    /** #141: タスクを使い切ったあとのフェーズと、同じフェーズの2件目は時間割が二重になるため落とす。 */
    @Test
    void roadmapDropsPhasesAfterTasksRunOutAndDuplicatePhases() {
        ChatLanguageModel model = modelReturning(
                roadmapJson(phase("NOW", 1), phase("NOW", 2), phase("SHORT_TERM", 9), phase("MID_TERM", 9)));
        DebateAdviceGeneratorService svc = newService(model);

        var advice = svc.generateForJob(List.of(rowWith(0.5, 4)), context(), SubscriptionPlan.STANDARD, tasks(3), null);

        assertThat(advice.roadmapItems().stream().map(RoadmapItem::phase).toList())
                .containsExactly(RoadmapPhase.NOW, RoadmapPhase.SHORT_TERM);
        assertThat(ranges(advice.roadmapItems())).containsExactly(new int[] {1, 1}, new int[] {2, 3});
    }

    /** 改善タスクが無い解析では、ロードマップは番号を持たない従来形のまま。 */
    @Test
    void roadmapWithoutTasksHasNoTaskRanges() {
        ChatLanguageModel model = modelReturning(roadmapJson(phase("NOW", 0), phase("SHORT_TERM", 0)));
        DebateAdviceGeneratorService svc = newService(model);

        var advice = svc.generateForJob(List.of(rowWith(0.5, 4)), context(), SubscriptionPlan.STANDARD, List.of(), null);

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

        svc.generateForJob(List.of(rowWith(0.5, 4)), context(), SubscriptionPlan.STANDARD, ordered, null);

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

    /** #195: 議論の前提には指標名（改Z'・Visibility Stage）ではなく、1問ごとの測定の事実を載せる。 */
    @Test
    void debateContextCarriesMeasurementFactsInsteadOfMetricNames() {
        AuditHistoryEntity row = rowWith(0.5, 4);
        row.setQuery("地元 工務店");
        row.setBrandMentioned(true);
        row.setMentionRank(2);

        String debateContext = DebateAdviceGeneratorService.buildDebateContext(
                List.of(row), context(), List.of(), new DebateJobFacts("注文住宅", null, null, null));

        assertThat(debateContext)
                .contains("【測定の事実】")
                .contains("1. 「地元 工務店」→ 社名が出た（回答の中で2番目）")
                .contains("事業の概要: 注文住宅")
                .doesNotContain("改Z'")
                .doesNotContain("Visibility Stage");
    }

    /** #195: 短縮版議論のアナリストは、測定の事実を前提に発言する。 */
    @Test
    void shortDebatePersonasReceiveMeasurementFacts() {
        ChatLanguageModel director = modelReturning(VALID_JSON);
        ChatLanguageModel persona = modelReturning("ペルソナの主張テキスト");
        CreditVaultService credit = mock(CreditVaultService.class);
        when(credit.reserve(any(), eq(DebateAdviceGeneratorService.DEBATE_CREDIT))).thenReturn(UUID.randomUUID());
        DebateAdviceGeneratorService svc = newService(director, persona, credit);
        AuditHistoryEntity row = rowWith(0.5, 4);
        row.setQuery("地元 工務店");

        svc.generateForJob(List.of(row), billingContext(), SubscriptionPlan.PRO, List.of(), DebateJobFacts.empty());

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(persona, times(DebateAdviceGeneratorService.SHORT_DEBATE_TURNS * 3)).chat(captor.capture());
        String analystFirstTurn = ((UserMessage) captor.getAllValues().get(0).messages().get(1)).singleText();
        assertThat(analystFirstTurn).contains("【測定の事実】").contains("1. 「地元 工務店」→ 社名は出なかった");
    }

    private static String turn(
            String discussion, String summary, String replyTo, String stance, String kind, int taskNumber,
            String detail) {
        return "{\"discussion\":\"" + discussion + "\",\"screen_summary\":\"" + summary
                + "\",\"reply_to\":\"" + replyTo + "\",\"stance\":\"" + stance
                + "\",\"evidence_kind\":\"" + kind + "\",\"evidence_task_number\":" + taskNumber
                + ",\"evidence_detail\":\"" + detail + "\"}";
    }

    private static ChatLanguageModel modelReturningInOrder(String first, String... rest) {
        ChatLanguageModel m = mock(ChatLanguageModel.class);
        ChatResponse[] others = new ChatResponse[rest.length];
        for (int i = 0; i < rest.length; i++) {
            others[i] = ChatResponse.builder().aiMessage(AiMessage.from(rest[i])).build();
        }
        when(m.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(first)).build(), others);
        return m;
    }

    private static final String DIRECTOR_WITH_SUMMARY_JSON =
            "{\"diagnostic_message\":\"B2B向けに独自データセットを活かすべき。\","
                    + "\"debate_summary\":\"まとめます。今すぐ改善タスク1から進めます。\"}";

    /** 見本の画面（#199）に近い2ラウンド。返事の相手と根拠には、捨てるべき項目も混ぜてある。 */
    private static ChatLanguageModel scenarioPersonas() {
        return modelReturningInOrder(
                turn("分析本文1", "社名が出たのは2問だけでした。", "SKEPTIC", "REBUT", "QUERY_MENTIONS", 0,
                        "社名が出た質問 2問 / 10問"),
                turn("提案本文1", "本命は改善タスク2です。", "NONE", "NONE", "REMEDIATION_TASK", 2, ""),
                turn("批判本文1", "3か月は長すぎます。", "INNOVATOR", "REBUT", "REMEDIATION_TASK", 9, ""),
                turn("分析本文2", "確認しました。", "ANALYST", "CONFIRM", "SITE_DIAGNOSIS", 0, ""),
                turn("提案本文2", "案を直します。", "SKEPTIC", "RESPOND", "NONE", 0, ""),
                turn("批判本文2", "その割り振りなら賛成です。", "INNOVATOR", "CONDITIONAL_AGREE", "COMPETITORS", 0,
                        "競合A 5問"));
    }

    private DebateAdviceGeneratorService.JobAdvice runScenario(ChatLanguageModel director, ChatLanguageModel persona) {
        CreditVaultService credit = mock(CreditVaultService.class);
        when(credit.reserve(any(), eq(DebateAdviceGeneratorService.DEBATE_CREDIT))).thenReturn(UUID.randomUUID());
        return newService(director, persona, credit)
                .generateForJob(List.of(rowWith(0.5, 4)), billingContext(), SubscriptionPlan.PRO, tasks(3), null);
    }

    /** #196: 画面に出す発言は、話した順の6件と、まとめ役の一言。 */
    @Test
    void shortDebateReturnsScreenUtterancesInOrderEndingWithDirectorSummary() {
        var advice = runScenario(modelReturning(DIRECTOR_WITH_SUMMARY_JSON), scenarioPersonas());

        assertThat(advice.utterances()).extracting(DebateUtterance::speaker).containsExactly(
                DebatePersona.ANALYST, DebatePersona.INNOVATOR, DebatePersona.SKEPTIC,
                DebatePersona.ANALYST, DebatePersona.INNOVATOR, DebatePersona.SKEPTIC,
                DebatePersona.DIRECTOR);
        assertThat(advice.utterances()).extracting(DebateUtterance::round)
                .containsExactly(1, 1, 1, 2, 2, 2, null);
        assertThat(advice.utterances().get(1).summary()).isEqualTo("本命は改善タスク2です。");
        assertThat(advice.utterances().getLast().summary()).isEqualTo("まとめます。今すぐ改善タスク1から進めます。");
    }

    /** #196: 返事の相手は、その発言者が読んだ人だけ。1ラウンド目のアナリストと、自分自身への返事は捨てる。 */
    @Test
    void replyTargetsTheSpeakerHasNotReadAreDropped() {
        List<DebateUtterance> u = runScenario(modelReturning(DIRECTOR_WITH_SUMMARY_JSON), scenarioPersonas())
                .utterances();

        assertThat(u.get(0).replyTo()).isNull();
        assertThat(u.get(0).stance()).isNull();
        assertThat(u.get(2).replyTo()).isEqualTo(DebatePersona.INNOVATOR);
        assertThat(u.get(2).stance()).isEqualTo(DebateStance.REBUT);
        assertThat(u.get(3).replyTo()).isNull();
        assertThat(u.get(4).replyTo()).isEqualTo(DebatePersona.SKEPTIC);
        assertThat(u.get(4).stance()).isEqualTo(DebateStance.RESPOND);
        assertThat(u.get(5).stance()).isEqualTo(DebateStance.CONDITIONAL_AGREE);
    }

    /** #196: 根拠は、存在しない改善タスクの番号と、中身の無い測定結果を捨てる。 */
    @Test
    void evidenceWithUnknownTaskNumberOrEmptyDetailIsDropped() {
        List<DebateUtterance> u = runScenario(modelReturning(DIRECTOR_WITH_SUMMARY_JSON), scenarioPersonas())
                .utterances();

        assertThat(u.get(0).evidenceKind()).isEqualTo(DebateEvidenceKind.QUERY_MENTIONS);
        assertThat(u.get(0).evidenceDetail()).isEqualTo("社名が出た質問 2問 / 10問");
        assertThat(u.get(0).evidenceTaskNumber()).isNull();
        assertThat(u.get(1).evidenceKind()).isEqualTo(DebateEvidenceKind.REMEDIATION_TASK);
        assertThat(u.get(1).evidenceTaskNumber()).isEqualTo(2);
        assertThat(u.get(2).evidenceKind()).isNull();
        assertThat(u.get(2).evidenceTaskNumber()).isNull();
        assertThat(u.get(3).evidenceKind()).isNull();
        assertThat(u.get(5).evidenceKind()).isEqualTo(DebateEvidenceKind.COMPETITORS);
    }

    /**
     * #196: 書式は呼び出しごとに渡し、共用のシステムプロンプトの後ろに画面用の書き方を足す。
     * 次の発言者とまとめ役には、画面用の要約ではなく議論用の本文を渡す。
     */
    @Test
    void personaCallsCarryTurnFormatAndPassOnlyDiscussionOn() {
        ChatLanguageModel director = modelReturning(DIRECTOR_WITH_SUMMARY_JSON);
        ChatLanguageModel persona = scenarioPersonas();

        runScenario(director, persona);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(persona, times(DebateAdviceGeneratorService.SHORT_DEBATE_TURNS * 3)).chat(captor.capture());
        ChatRequest analystFirst = captor.getAllValues().get(0);
        assertThat(analystFirst.responseFormat().jsonSchema().name()).isEqualTo("debate_job_turn");
        assertThat(((SystemMessage) analystFirst.messages().get(0)).text())
                .startsWith(DebatePersonaSystemPrompts.forPersona(DebatePersona.ANALYST, IndustryType.B2B))
                .contains("screen_summary");
        String skepticFirstInput = ((UserMessage) captor.getAllValues().get(2).messages().get(1)).singleText();
        assertThat(skepticFirstInput).contains("分析本文1").contains("提案本文1").doesNotContain("本命は改善タスク2です。");

        ArgumentCaptor<ChatRequest> directorCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(director).chat(directorCaptor.capture());
        String directorInput = ((UserMessage) directorCaptor.getValue().messages().get(1)).singleText();
        assertThat(directorInput).contains("批判本文2").doesNotContain("その割り振りなら賛成です。");
        assertThat(((SystemMessage) directorCaptor.getValue().messages().get(0)).text())
                .contains("debate_summary は、議論の最後に画面へ出す");
    }

    /** #196: 形の崩れた応答でも議論は止めない。全文を本文として次へ渡し、画面用の要約は空にする。 */
    @Test
    void brokenTurnOutputKeepsDebateGoingWithRawTextAndEmptySummary() {
        ChatLanguageModel director = modelReturning(DIRECTOR_WITH_SUMMARY_JSON);
        CreditVaultService credit = mock(CreditVaultService.class);
        UUID reservationId = UUID.randomUUID();
        when(credit.reserve(any(), eq(DebateAdviceGeneratorService.DEBATE_CREDIT))).thenReturn(reservationId);

        var advice = newService(director, modelReturning("ペルソナの主張テキスト"), credit)
                .generateForJob(List.of(rowWith(0.5, 4)), billingContext(), SubscriptionPlan.PRO, List.of(), null);

        assertThat(advice.utterances()).hasSize(DebateAdviceGeneratorService.SHORT_DEBATE_TURNS * 3 + 1);
        assertThat(advice.utterances().subList(0, 6)).allSatisfy(u -> {
            assertThat(u.summary()).isEmpty();
            assertThat(u.replyTo()).isNull();
            assertThat(u.evidenceKind()).isNull();
        });
        ArgumentCaptor<ChatRequest> directorCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(director).chat(directorCaptor.capture());
        assertThat(((UserMessage) directorCaptor.getValue().messages().get(1)).singleText())
                .contains("ペルソナの主張テキスト");
        verify(credit).settle(eq(reservationId), eq(DebateAdviceGeneratorService.DEBATE_CREDIT), any());
    }

    /** #196: 議論が走らない回は、画面に出す発言が無く、まとめ役にも一言を求めない。 */
    @Test
    void singleShotWithoutDebateHasNoUtterances() {
        ChatLanguageModel director = modelReturning(DIRECTOR_WITH_SUMMARY_JSON);

        var advice = newService(director)
                .generateForJob(List.of(rowWith(0.5, 4)), context(), SubscriptionPlan.PRO, List.of(), null);

        assertThat(advice.utterances()).isEmpty();
        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(director).chat(captor.capture());
        assertThat(((SystemMessage) captor.getValue().messages().get(0)).text())
                .contains("debate_summary は空文字にすること");
    }

    /** #196: 長すぎる要約は、上限の中の最後の「。」まで残す。「。」が無ければ上限で切って「…」を付ける。 */
    @Test
    void longSummaryIsCutAtLastSentenceEnd() {
        String firstSentence = "あ".repeat(50) + "。";
        int max = DebateAdviceGeneratorService.SCREEN_SUMMARY_MAX_CHARS;

        assertThat(DebateAdviceGeneratorService.clampSummary(firstSentence + "い".repeat(max)))
                .isEqualTo(firstSentence);
        assertThat(DebateAdviceGeneratorService.clampSummary("う".repeat(max + 10)))
                .hasSize(max)
                .endsWith("…");
        assertThat(DebateAdviceGeneratorService.clampSummary(" 一文目です。\n二文目です。 ")).isEqualTo("一文目です。二文目です。");
    }
}
