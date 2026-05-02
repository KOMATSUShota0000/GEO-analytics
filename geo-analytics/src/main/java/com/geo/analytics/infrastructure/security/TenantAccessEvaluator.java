package com.geo.analytics.infrastructure.security;

import com.geo.analytics.domain.enums.OrganizationUserRole;
import com.geo.analytics.infrastructure.repository.WorkspaceRepository;
import com.geo.analytics.infrastructure.tenant.OrgTenantKey;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import com.github.benmanes.caffeine.cache.Cache;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

@Component("tenantAccessEvaluator")
public class TenantAccessEvaluator {

    private static final String ROLE_ADMIN = "ROLE_" + OrganizationUserRole.ADMIN.name();
    private static final Set<String> READ_ROLES = EnumSet.allOf(OrganizationUserRole.class).stream()
            .map(role -> "ROLE_" + role.name())
            .collect(Collectors.toUnmodifiableSet());

    private final Cache<OrgTenantKey, Boolean> orgTenantAffiliationCache;
    private final WorkspaceRepository workspaceRepository;
    private final JdbcTemplate jdbcTemplate;

    public TenantAccessEvaluator(
            @Qualifier("orgTenantAffiliationCache") Cache<OrgTenantKey, Boolean> orgTenantAffiliationCache,
            WorkspaceRepository workspaceRepository,
            JdbcTemplate jdbcTemplate) {
        this.orgTenantAffiliationCache = orgTenantAffiliationCache;
        this.workspaceRepository = workspaceRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean canAccessTenant(Authentication authentication, UUID tenantId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (!hasAdminRole(authentication)) {
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

    public boolean canAccessCurrentTenant(Authentication authentication) {
        Optional<UUID> tenantId = TenantContextHolder.getTenantId();
        if (tenantId.isEmpty()) {
            return false;
        }
        return canAccessTenant(authentication, tenantId.get());
    }

    public boolean canReadProjectAssetSnapshots(Authentication authentication, UUID projectId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (!hasReadableProjectRole(authentication)) {
            return false;
        }
        Optional<UUID> orgId = TenantContextHolder.getOrganizationId();
        Optional<UUID> contextTenantId = TenantContextHolder.getTenantId();
        if (orgId.isEmpty() || contextTenantId.isEmpty()) {
            return false;
        }
        Optional<UUID> projectWorkspaceId = resolveWorkspaceIdForProject(projectId);
        if (projectWorkspaceId.isEmpty()) {
            return false;
        }
        if (!projectWorkspaceId.get().equals(contextTenantId.get())) {
            return false;
        }
        return workspaceRepository.existsByIdAndOrganizationId(projectWorkspaceId.get(), orgId.get());
    }

    public boolean canReadWorkspaceBranding(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (!hasReadableProjectRole(authentication)) {
            return false;
        }
        Optional<UUID> organizationId = TenantContextHolder.getOrganizationId();
        Optional<UUID> tenantId = TenantContextHolder.getTenantId();
        if (organizationId.isEmpty() || tenantId.isEmpty()) {
            return false;
        }
        return workspaceRepository.existsByIdAndOrganizationId(tenantId.get(), organizationId.get());
    }

    private Optional<UUID> resolveWorkspaceIdForProject(UUID projectId) {
        List<String> rows = jdbcTemplate.query(
                "SELECT tenant_id FROM projects WHERE id = ?",
                ps -> ps.setObject(1, projectId),
                (rs, rowNum) -> rs.getString(1));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        String tenantId = rows.get(0);
        if (tenantId == null || tenantId.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(tenantId));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static boolean hasReadableProjectRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(READ_ROLES::contains);
    }

    private static boolean hasAdminRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLE_ADMIN::equals);
    }
}
