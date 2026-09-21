package com.geo.analytics.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.GeoAnalyticsApplication;
import com.geo.analytics.application.service.SyncVerificationService;
import com.geo.analytics.infrastructure.api.GeoCompetitorSearchAdapter;
import com.geo.analytics.infrastructure.security.TenantAccessEvaluator;
import com.geo.analytics.infrastructure.tenant.OrgTenantKey;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import com.geo.analytics.infrastructure.tenant.TenantIdentity;
import com.github.benmanes.caffeine.cache.Cache;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * {@link TenantAccessEvaluator} は @PreAuthorize から呼ばれ、呼び出し元にトランザクションが無い。
 * api_worker（RLS 強制）接続で所属判定が通ることを担保する回帰テスト（#131）。
 */
@SpringBootTest(classes = GeoAnalyticsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("rls-it")
class TenantAccessEvaluatorRlsIntegrationTest extends PostgresTestBase {

    private static final UUID ORG_A = UUID.fromString("11111111-1111-1111-1111-111111111101");
    private static final UUID ORG_B = UUID.fromString("22222222-2222-2222-2222-222222222202");
    private static final UUID WORKSPACE_A = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0131");
    private static final UUID WORKSPACE_B = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0131");

    @Autowired
    private TenantAccessEvaluator tenantAccessEvaluator;

    @Autowired
    @Qualifier("batchJdbcTemplate")
    private JdbcTemplate batchJdbcTemplate;

    @Autowired
    @Qualifier("orgTenantAffiliationCache")
    private Cache<OrgTenantKey, Boolean> orgTenantAffiliationCache;

    @MockitoBean
    private GeoCompetitorSearchAdapter geoCompetitorSearchAdapter;

    @MockitoBean
    private SyncVerificationService syncVerificationService;

    @BeforeEach
    void seedWorkspaces() {
        // Why: batch_worker は BYPASSRLS のため、テナント文脈なしで両組織のワークスペースを投入できる
        batchJdbcTemplate.update("""
                INSERT INTO workspaces (id, name, subscription_plan, organization_id, created_at, updated_at)
                VALUES (?, 'Workspace A', 'STANDARD', ?, now(), now()), (?, 'Workspace B', 'STANDARD', ?, now(), now())
                ON CONFLICT (id) DO NOTHING
                """, WORKSPACE_A, ORG_A, WORKSPACE_B, ORG_B);
        orgTenantAffiliationCache.invalidateAll();
    }

    @Test
    void adminCanAccessOwnWorkspace() {
        assertThat(asOrgA(() -> tenantAccessEvaluator.canAccessTenant(admin(), WORKSPACE_A))).isTrue();
    }

    @Test
    void adminCannotAccessOtherOrganizationsWorkspace() {
        assertThat(asOrgA(() -> tenantAccessEvaluator.canAccessTenant(admin(), WORKSPACE_B))).isFalse();
    }

    @Test
    void adminCanAccessCurrentWorkspace() {
        assertThat(asOrgA(() -> tenantAccessEvaluator.canAccessCurrentTenant(admin()))).isTrue();
    }

    @Test
    void memberCanReadOwnWorkspaceBranding() {
        assertThat(asOrgA(() -> tenantAccessEvaluator.canReadWorkspaceBranding(withRole("ROLE_MEMBER")))).isTrue();
    }

    private static boolean asOrgA(Supplier<Boolean> check) {
        return ScopedValue.where(TenantContextHolder.CONTEXT, new TenantIdentity(ORG_A, WORKSPACE_A, null))
                .call(check::get);
    }

    private static Authentication admin() {
        return withRole("ROLE_ADMIN");
    }

    private static Authentication withRole(String role) {
        return new UsernamePasswordAuthenticationToken("user", null, List.of(new SimpleGrantedAuthority(role)));
    }
}
