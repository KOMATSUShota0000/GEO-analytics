package com.geo.analytics.application.service;

import com.geo.analytics.application.port.PdfBrowserAuthHeaders;
import com.geo.analytics.domain.entity.OrganizationUser;
import com.geo.analytics.infrastructure.security.TokenService;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import com.geo.analytics.infrastructure.tenant.TenantIdentity;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PdfBrowserTokenIssuer {

    private static final Logger log = LoggerFactory.getLogger(PdfBrowserTokenIssuer.class);
    private static final int STACK_TRACE_LIMIT = 20_000;

    private final TokenService tokenService;
    private final SessionManagementService sessionManagementService;
    private final BatchPersistenceService batchPersistenceService;

    public PdfBrowserTokenIssuer(
            TokenService tokenService,
            SessionManagementService sessionManagementService,
            BatchPersistenceService batchPersistenceService) {
        this.tokenService = tokenService;
        this.sessionManagementService = sessionManagementService;
        this.batchPersistenceService = batchPersistenceService;
    }

    public PdfBrowserAuthHeaders issueForCurrentTenant() {
        TenantIdentity identity = TenantContextHolder.requireContext();
        UUID workspaceId = identity.tenantId();
        UUID orgId = identity.organizationId();
        if (workspaceId == null || orgId == null) {
            return PdfBrowserAuthHeaders.NONE;
        }
        try {
            BatchPersistenceService.OrgUserInfo userInfo = batchPersistenceService
                    .findFirstActiveOrgUser(orgId)
                    .orElseThrow(() -> new IllegalStateException("pdf_browser_token_user_missing orgId=" + orgId));
            OrganizationUser user = new OrganizationUser();
            user.setId(userInfo.id());
            user.setEmail(userInfo.email());
            user.setPasswordHash(userInfo.passwordHash());
            user.setOrganizationId(orgId);
            UUID sessionId = sessionManagementService.appendRenderingSession(user.getId());
            String jwt = tokenService.generateAccessToken(user, sessionId);
            return new PdfBrowserAuthHeaders("Bearer " + jwt, workspaceId.toString());
        } catch (RuntimeException runtimeException) {
            log.error(
                    "pdf_browser_token_issue_failed orgId={} workspaceId={} trace={}",
                    orgId,
                    workspaceId,
                    truncateStackTrace(runtimeException));
            return PdfBrowserAuthHeaders.NONE;
        }
    }

    public TenantIdentity buildTenantIdentityForWorkspace(UUID workspaceId) {
        if (workspaceId == null) {
            throw new IllegalArgumentException("workspaceId");
        }
        UUID orgId = resolveOrganizationIdForWorkspace(workspaceId);
        return new TenantIdentity(orgId, workspaceId, null);
    }

    private UUID resolveOrganizationIdForWorkspace(UUID workspaceId) {
        if (DefaultTenantIds.WORKSPACE_ID.equals(workspaceId)) {
            return DefaultTenantIds.DEFAULT_ORGANIZATION_ID;
        }
        return batchPersistenceService
                .findWorkspaceOrganizationId(workspaceId)
                .orElseThrow(() -> new IllegalStateException("workspace not found: " + workspaceId));
    }

    private static String truncateStackTrace(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        String full = stringWriter.toString();
        if (full.length() <= STACK_TRACE_LIMIT) {
            return full;
        }
        return full.substring(0, STACK_TRACE_LIMIT);
    }
}
