package com.geo.analytics.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.geo.analytics.domain.entity.OrganizationEntity;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.infrastructure.config.AppProperties;
import com.geo.analytics.infrastructure.repository.OrganizationRepository;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import com.geo.analytics.infrastructure.tenant.TenantIdentity;
import com.geo.analytics.web.dto.WorkspaceBrandingResponse;
import java.io.FileNotFoundException;
import java.lang.ScopedValue;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BrandingServicePlanGateTest {

    private static final UUID ORG_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID WORKSPACE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final String TOOL_NAME = "AcmeGEO";
    private static final String BRAND_COLOR = "#112233";
    private static final String LOGO_URL = "/api/v1/workspaces/current/branding/logo";

    @Mock private OrganizationRepository organizationRepository;
    @Mock private AppProperties appProperties;
    @Mock private WorkspacePlanResolver workspacePlanResolver;

    private BrandingService service() {
        return new BrandingService(organizationRepository, appProperties, workspacePlanResolver);
    }

    private OrganizationEntity orgMock() {
        OrganizationEntity org = mock(OrganizationEntity.class);
        lenient().when(org.getToolName()).thenReturn(TOOL_NAME);
        lenient().when(org.getBrandColor()).thenReturn(BRAND_COLOR);
        return org;
    }

    private void runWithTenant(UUID workspaceId, Runnable body) {
        ScopedValue.where(TenantContextHolder.CONTEXT, new TenantIdentity(ORG_ID, workspaceId, null))
                .run(body);
    }

    private WorkspaceBrandingResponse getBranding(UUID workspaceId) {
        AtomicReference<WorkspaceBrandingResponse> ref = new AtomicReference<>();
        runWithTenant(workspaceId, () -> ref.set(service().getBranding()));
        return ref.get();
    }

    private void stubOrgLookup() {
        OrganizationEntity org = orgMock();
        lenient().when(organizationRepository.findByIdAndDeletedAtIsNull(ORG_ID)).thenReturn(Optional.of(org));
    }

    @Test
    void getBranding_standard_returnsEmptyLogoUrl_andKeepsToolNameAndColor() {
        stubOrgLookup();
        when(workspacePlanResolver.resolvePlan(WORKSPACE_ID)).thenReturn(SubscriptionPlan.STANDARD);

        WorkspaceBrandingResponse res = getBranding(WORKSPACE_ID);

        assertThat(res.toolName()).isEqualTo(TOOL_NAME);
        assertThat(res.brandColor()).isEqualTo(BRAND_COLOR);
        assertThat(res.logoUrl()).isEmpty();
    }

    @Test
    void getBranding_pro_returnsLogoUrl() {
        stubOrgLookup();
        when(workspacePlanResolver.resolvePlan(WORKSPACE_ID)).thenReturn(SubscriptionPlan.PRO);

        WorkspaceBrandingResponse res = getBranding(WORKSPACE_ID);

        assertThat(res.toolName()).isEqualTo(TOOL_NAME);
        assertThat(res.brandColor()).isEqualTo(BRAND_COLOR);
        assertThat(res.logoUrl()).isEqualTo(LOGO_URL);
    }

    @Test
    void getBranding_expert_returnsLogoUrl() {
        stubOrgLookup();
        when(workspacePlanResolver.resolvePlan(WORKSPACE_ID)).thenReturn(SubscriptionPlan.EXPERT);

        WorkspaceBrandingResponse res = getBranding(WORKSPACE_ID);

        assertThat(res.logoUrl()).isEqualTo(LOGO_URL);
    }

    @Test
    void getBranding_noTenant_treatedAsStandard_emptyLogoUrl() {
        stubOrgLookup();

        WorkspaceBrandingResponse res = getBranding(null);

        assertThat(res.logoUrl()).isEmpty();
        assertThat(res.toolName()).isEqualTo(TOOL_NAME);
        assertThat(res.brandColor()).isEqualTo(BRAND_COLOR);
    }

    @Test
    void loadLogoResource_standard_throwsFileNotFound_withoutHittingRepository() {
        when(workspacePlanResolver.resolvePlan(WORKSPACE_ID)).thenReturn(SubscriptionPlan.STANDARD);

        AtomicReference<Throwable> err = new AtomicReference<>();
        runWithTenant(WORKSPACE_ID, () -> {
            try {
                service().loadLogoResource();
            } catch (Throwable t) {
                err.set(t);
            }
        });

        assertThat(err.get())
                .isInstanceOf(FileNotFoundException.class)
                .hasMessageContaining("logo");
    }

    @Test
    void loadLogoResource_noTenant_throwsFileNotFound() {
        AtomicReference<Throwable> err = new AtomicReference<>();
        runWithTenant(null, () -> {
            try {
                service().loadLogoResource();
            } catch (Throwable t) {
                err.set(t);
            }
        });

        assertThat(err.get()).isInstanceOf(FileNotFoundException.class);
    }

    @Test
    void loadLogoResource_pro_passesPlanGate_andProceedsToOrgLookup() {
        OrganizationEntity withoutLogo = orgMock();
        lenient().when(withoutLogo.getLogoFilePath()).thenReturn(null);
        when(workspacePlanResolver.resolvePlan(WORKSPACE_ID)).thenReturn(SubscriptionPlan.PRO);
        when(organizationRepository.findByIdAndDeletedAtIsNull(ORG_ID)).thenReturn(Optional.of(withoutLogo));

        AtomicReference<Throwable> err = new AtomicReference<>();
        runWithTenant(WORKSPACE_ID, () -> {
            try {
                service().loadLogoResource();
            } catch (Throwable t) {
                err.set(t);
            }
        });

        assertThat(err.get()).isInstanceOf(FileNotFoundException.class);
    }

}
