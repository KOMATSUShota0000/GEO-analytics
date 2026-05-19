package com.geo.analytics.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.GeoAnalyticsApplication;
import com.geo.analytics.application.service.SyncVerificationService;
import com.geo.analytics.infrastructure.api.GeoCompetitorSearchAdapter;
import com.geo.analytics.infrastructure.tenant.TenantIdentity;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import com.geo.analytics.integration.support.RlsTenantQueryFacade;
import java.lang.ScopedValue;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(classes = GeoAnalyticsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("rls-it")
class RlsIntegrationTest extends PostgresTestBase {

    private static final UUID ORG_A = UUID.fromString("11111111-1111-1111-1111-111111111101");
    private static final UUID ORG_B = UUID.fromString("22222222-2222-2222-2222-222222222202");
    private static final UUID TENANT_A = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1");
    private static final UUID TENANT_B = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1");

    @Autowired
    private RlsTenantQueryFacade rlsTenantQueryFacade;

    @MockitoBean
    private GeoCompetitorSearchAdapter geoCompetitorSearchAdapter;

    @MockitoBean
    private SyncVerificationService syncVerificationService;

    @Test
    void whenNoTenantContext_thenRlsHidesAllTenantRows() {
        List<Object[]> rows = rlsTenantQueryFacade.selectAllTenants();
        assertThat(rows).isEmpty();
    }

    @Test
    void whenOrgAContext_thenOnlyOrgATenantsVisible() {
        ScopedValue.where(TenantContextHolder.CONTEXT, new TenantIdentity(ORG_A, null, null))
                .run(
                        () -> {
                            List<Object[]> rows = rlsTenantQueryFacade.selectAllTenants();
                            assertThat(rows).hasSize(1);
                            assertThat(toUuid(rows.get(0)[0])).isEqualTo(TENANT_A);
                        });
    }

    @Test
    void whenOrgBContext_thenOnlyOrgBTenantsVisible() {
        ScopedValue.where(TenantContextHolder.CONTEXT, new TenantIdentity(ORG_B, null, null))
                .run(
                        () -> {
                            List<Object[]> rows = rlsTenantQueryFacade.selectAllTenants();
                            assertThat(rows).hasSize(1);
                            assertThat(toUuid(rows.get(0)[0])).isEqualTo(TENANT_B);
                        });
    }

    private static UUID toUuid(Object value) {
        if (value instanceof UUID u) {
            return u;
        }
        return UUID.fromString(Objects.toString(value));
    }
}
