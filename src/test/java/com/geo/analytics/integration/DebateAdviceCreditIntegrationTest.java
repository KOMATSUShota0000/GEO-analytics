package com.geo.analytics.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.geo.analytics.GeoAnalyticsApplication;
import com.geo.analytics.application.dto.ProjectAdviceContext;
import com.geo.analytics.application.dto.StrategyInsight;
import com.geo.analytics.application.service.BatchPersistenceService;
import com.geo.analytics.application.service.DebateAdviceGeneratorService;
import com.geo.analytics.domain.entity.AuditHistoryEntity;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Sprint 2 のチケット消費（0.2 チケット = {@value DebateAdviceGeneratorService#DEBATE_CREDIT} 単位）を
 * 実 DB で検証する integration test（仕様書 N-2）。
 *
 * <p>あわせて {@link BatchPersistenceService#findProjectAdviceContext} の projects⋈workspaces
 * （tenant_id::uuid リンク）SQL が実スキーマで正しく解決できることを保証する。
 *
 * <p>LLM 呼び出しは 4 ペルソナ ChatLanguageModel ビーンをモック化して実 API を叩かない。
 */
@SpringBootTest(
        classes = GeoAnalyticsApplication.class,
        properties = "spring.task.scheduling.enabled=false")
@ActiveProfiles("test")
class DebateAdviceCreditIntegrationTest extends PostgresSuperuserTestBase {

    private static final UUID ORG_ID = DefaultTenantIds.DEFAULT_ORGANIZATION_ID;
    private static final long INITIAL_CREDIT = 1_000L;

    @Autowired private DebateAdviceGeneratorService debateAdviceGeneratorService;
    @Autowired private BatchPersistenceService batchPersistenceService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean(name = "geminiDebateDirector")
    private ChatLanguageModel directorModel;

    @MockitoBean(name = "geminiDebateAnalyst")
    private ChatLanguageModel analystModel;

    @MockitoBean(name = "geminiDebateInnovator")
    private ChatLanguageModel innovatorModel;

    @MockitoBean(name = "geminiDebateSkeptic")
    private ChatLanguageModel skepticModel;

    private UUID workspaceId;
    private UUID projectId;

    @BeforeEach
    void seed() {
        // ペルソナは議論テキスト、DIRECTOR は最終アドバイス JSON を返す
        stubModel(analystModel, "アナリストの分析");
        stubModel(innovatorModel, "イノベーターの提案");
        stubModel(skepticModel, "スケプティックの反証");
        stubModel(
                directorModel,
                "{\"diagnostic_message\":\"B2B向け固有アドバイス\","
                        + "\"recommended_actions\":[\"事例公開\",\"統計提供\",\"PR強化\"]}");

        workspaceId = UUID.randomUUID();
        projectId = UUID.randomUUID();

        jdbcTemplate.update(
                "UPDATE organizations SET credit_balance = ? WHERE id = ?", INITIAL_CREDIT, ORG_ID);
        jdbcTemplate.update(
                "INSERT INTO workspaces (id, name, subscription_plan, organization_id, created_at, updated_at)"
                        + " VALUES (?, 'IT WS', 'PRO', ?, now(), now()) ON CONFLICT (id) DO NOTHING",
                workspaceId,
                ORG_ID);
        jdbcTemplate.update(
                "INSERT INTO projects (id, tenant_id, name, target_url, brand_color, created_at, updated_at,"
                        + " auto_audit_enabled, industry_type, minority_reports, competitor_profiles,"
                        + " target_audience, extracted_strengths)"
                        + " VALUES (?, ?, 'IT Project', 'https://example.test', '#000000', now(), now(),"
                        + " false, 'B2B', '[]'::jsonb, '[]'::jsonb, '中小企業のマーケ責任者', '独自データセット')",
                projectId,
                workspaceId.toString());
    }

    @Test
    void proPlanDebateConsumesDebateCreditAndResolvesContextFromRealSchema() {
        // 1) 修正後 SQL（tenant_id::uuid リンク）が実スキーマで識別子を解決できること
        ProjectAdviceContext context =
                batchPersistenceService.findProjectAdviceContext(projectId).orElseThrow();
        assertThat(context.projectId()).isEqualTo(projectId);
        assertThat(context.workspaceId()).isEqualTo(workspaceId);
        assertThat(context.organizationId()).isEqualTo(ORG_ID);
        assertThat(context.hasBillingIdentity()).isTrue();

        long balanceBefore = creditBalance();

        // 2) Pro プランで議論駆動アドバイス生成 → 0.2 チケット消費
        StrategyInsight result =
                debateAdviceGeneratorService.generateForJob(
                        List.of(row(0.5, 4), row(-0.3, 7)), context, SubscriptionPlan.PRO);

        assertThat(result.diagnosticMessage()).contains("B2B");
        assertThat(result.recommendedActions()).hasSize(3);

        // 3) 残高が DEBATE_CREDIT 分だけ減っていること（reserve→settle で消費確定）
        long balanceAfter = creditBalance();
        assertThat(balanceBefore - balanceAfter)
                .isEqualTo(DebateAdviceGeneratorService.DEBATE_CREDIT);

        // 4) SETTLE のウォレット取引が記録されていること（返金されていない）
        Long settleCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM wallet_transactions"
                                + " WHERE project_id = ? AND transaction_type = 'SETTLE' AND amount = ?",
                        Long.class,
                        projectId,
                        DebateAdviceGeneratorService.DEBATE_CREDIT);
        assertThat(settleCount).isEqualTo(1L);

        Long refundCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM wallet_transactions"
                                + " WHERE project_id = ? AND transaction_type = 'REFUND'",
                        Long.class,
                        projectId);
        assertThat(refundCount).isEqualTo(0L);
    }

    @Test
    void proPlanDebateFailureRefundsCreditAndFallsBackToSingleShot() {
        ProjectAdviceContext context =
                batchPersistenceService.findProjectAdviceContext(projectId).orElseThrow();
        // 議論ペルソナ（アナリスト）を失敗させる。DIRECTOR は Free フォールバックで成功する。
        when(analystModel.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("debate down"));

        long balanceBefore = creditBalance();

        StrategyInsight result =
                debateAdviceGeneratorService.generateForJob(
                        List.of(row(0.5, 4), row(-0.3, 7)), context, SubscriptionPlan.PRO);

        // Free パス（単発 DIRECTOR）で結果は返る
        assertThat(result.diagnosticMessage()).contains("B2B");

        // 全額返金されたため残高は不変（オーナー決定 2026-05-30）
        assertThat(creditBalance()).isEqualTo(balanceBefore);

        Long refundCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM wallet_transactions"
                                + " WHERE project_id = ? AND transaction_type = 'REFUND'",
                        Long.class,
                        projectId);
        assertThat(refundCount).isEqualTo(1L);
    }

    private long creditBalance() {
        Long v =
                jdbcTemplate.queryForObject(
                        "SELECT credit_balance FROM organizations WHERE id = ?", Long.class, ORG_ID);
        return v == null ? 0L : v;
    }

    private static void stubModel(ChatLanguageModel model, String text) {
        ChatResponse response = ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
        when(model.chat(any(ChatRequest.class))).thenReturn(response);
    }

    private static AuditHistoryEntity row(double z, int stage) {
        AuditHistoryEntity a = new AuditHistoryEntity();
        a.setModifiedZScore(z);
        a.setVisibilityStage(stage);
        return a;
    }
}
