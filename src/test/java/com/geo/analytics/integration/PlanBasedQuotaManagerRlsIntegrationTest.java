package com.geo.analytics.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.GeoAnalyticsApplication;
import com.geo.analytics.application.service.PlanBasedQuotaManager;
import com.geo.analytics.application.service.SyncVerificationService;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.model.QuotaCreditCalculator;
import com.geo.analytics.infrastructure.api.GeoCompetitorSearchAdapter;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import com.geo.analytics.infrastructure.tenant.TenantIdentity;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 解析枠がワークスペースの本当のプランの大きさで作られることを、RLS が効く接続（api_worker）で確かめる（#193）。
 * 以前はプランを @Transactional の外から読んでいたため組織IDが渡らず、どのプランでも STANDARD の枠になっていた。
 * SubscriptionIntegrationTest は管理者接続（RLS が効かない）なので、この不具合を検出できない。
 */
@SpringBootTest(classes = GeoAnalyticsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("rls-it")
class PlanBasedQuotaManagerRlsIntegrationTest extends PostgresTestBase {

    private static final UUID ORG_A = UUID.fromString("11111111-1111-1111-1111-111111111101");
    private static final UUID PRO_WORKSPACE = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0193");
    private static final UUID EXPERT_WORKSPACE = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaab0193");

    @Autowired
    private PlanBasedQuotaManager planBasedQuotaManager;

    @Autowired
    @Qualifier("batchJdbcTemplate")
    private JdbcTemplate batchJdbcTemplate;

    @MockitoBean
    private GeoCompetitorSearchAdapter geoCompetitorSearchAdapter;

    @MockitoBean
    private SyncVerificationService syncVerificationService;

    @BeforeEach
    void seed() {
        seedWorkspace(PRO_WORKSPACE, SubscriptionPlan.PRO);
        seedWorkspace(EXPERT_WORKSPACE, SubscriptionPlan.EXPERT);
    }

    @Test
    void proWorkspaceGetsProSizedQuota() {
        long available = asOrgA(PRO_WORKSPACE, () -> planBasedQuotaManager.resolve(PRO_WORKSPACE).getAvailableTokens());

        assertThat(available).isEqualTo(capacityOf(SubscriptionPlan.PRO));
    }

    @Test
    void expertWorkspaceCanRunOneAnalysis() {
        long deposit = (long) SubscriptionPlan.EXPERT.defaultQueryCount() * QuotaCreditCalculator.DEPOSIT_PER_KEYWORD;

        boolean consumed = asOrgA(EXPERT_WORKSPACE, () -> planBasedQuotaManager.resolve(EXPERT_WORKSPACE).tryConsume(deposit));

        assertThat(consumed).isTrue();
    }

    @Test
    void rateLimitResponseReportsActualPlan() {
        SubscriptionPlan plan = asOrgA(PRO_WORKSPACE, () -> planBasedQuotaManager.resolveWorkspacePlan(PRO_WORKSPACE));

        assertThat(plan).isEqualTo(SubscriptionPlan.PRO);
    }

    private void seedWorkspace(UUID workspaceId, SubscriptionPlan plan) {
        // Why: batch_worker は BYPASSRLS のため、テナント文脈なしで行を用意できる
        batchJdbcTemplate.update("""
                INSERT INTO workspaces (id, name, subscription_plan, organization_id, created_at, updated_at)
                VALUES (?, 'Workspace 193', ?, ?, now(), now())
                ON CONFLICT (id) DO UPDATE SET subscription_plan = EXCLUDED.subscription_plan
                """, workspaceId, plan.name(), ORG_A);
        // Why: 枠はアプリ全体で共有するメモリ上のキャッシュにあり、テスト間で残りが持ち越されるため作り直させる
        planBasedQuotaManager.invalidateTenantBucket(workspaceId);
    }

    private static long capacityOf(SubscriptionPlan plan) {
        return (long) plan.getDailyLimit() * QuotaCreditCalculator.DEPOSIT_PER_KEYWORD;
    }

    private static <T> T asOrgA(UUID workspaceId, Supplier<T> action) {
        return ScopedValue.where(TenantContextHolder.CONTEXT, new TenantIdentity(ORG_A, workspaceId, null)).call(action::get);
    }
}
