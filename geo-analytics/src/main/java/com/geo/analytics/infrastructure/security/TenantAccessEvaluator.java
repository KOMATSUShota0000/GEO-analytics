package com.geo.analytics.infrastructure.security;

import com.geo.analytics.domain.enums.OrganizationUserRole;
import com.geo.analytics.infrastructure.repository.WorkspaceRepository;
import com.geo.analytics.infrastructure.tenant.OrgTenantKey;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import com.github.benmanes.caffeine.cache.Cache;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

@Component("tenantAccessEvaluator")
public class TenantAccessEvaluator {

    private static final String ROLE_ADMIN = "ROLE_" + OrganizationUserRole.ADMIN.name();

    private final Cache<OrgTenantKey, Boolean> orgTenantAffiliationCache;
    private final WorkspaceRepository workspaceRepository;

    public TenantAccessEvaluator(
            @Qualifier("orgTenantAffiliationCache") Cache<OrgTenantKey, Boolean> orgTenantAffiliationCache,
            WorkspaceRepository workspaceRepository) {
        this.orgTenantAffiliationCache = orgTenantAffiliationCache;
        this.workspaceRepository = workspaceRepository;
    }

    public boolean canAccessTenant(Authentication authentication, UUID tenantId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (!hasAdminRole(authentication)) {
            // TODO(MEMBER/VIEWER): tenant-scoped rules without org-wide cache path
            return false;
        }
        Optional<UUID> orgId = TenantContextHolder.getOrganizationId();
        if (orgId.isEmpty()) {
            return false;
        }
        OrgTenantKey key = new OrgTenantKey(orgId.get(), tenantId);
        Boolean cached = orgTenantAffiliationCache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        boolean allowed = workspaceRepository.existsByIdAndOrganizationId(tenantId, orgId.get());
        orgTenantAffiliationCache.put(key, allowed);
        return allowed;
    }

    private static boolean hasAdminRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLE_ADMIN::equals);
    }
}
