package com.geo.analytics.infrastructure.bootstrap;

import com.geo.analytics.domain.entity.OrganizationUser;
import com.geo.analytics.domain.entity.WorkspaceEntity;
import com.geo.analytics.domain.enums.OrganizationUserRole;
import com.geo.analytics.infrastructure.repository.OrganizationUserRepository;
import com.geo.analytics.infrastructure.repository.WorkspaceRepository;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import com.geo.analytics.infrastructure.tenant.TenantContext;
import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "app.bootstrap", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements CommandLineRunner {

    private static final String SEED_EMAIL = "bootstrap@example.com";
    private static final String SEED_PASSWORD = "bootstrap";

    private final WorkspaceRepository workspaceRepository;
    private final OrganizationUserRepository organizationUserRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(
            WorkspaceRepository workspaceRepository,
            OrganizationUserRepository organizationUserRepository,
            PasswordEncoder passwordEncoder) {
        this.workspaceRepository = workspaceRepository;
        this.organizationUserRepository = organizationUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (workspaceRepository.count() > 0) {
            return;
        }
        UUID wid = DefaultTenantIds.WORKSPACE_ID;
        TenantContext.executeWithTenant(wid, () -> {
            WorkspaceEntity w = new WorkspaceEntity();
            w.setId(wid);
            w.setOrganizationId(DefaultTenantIds.DEFAULT_ORGANIZATION_ID);
            w.setName("Default");
            workspaceRepository.save(w);
            if (organizationUserRepository.findByEmailAndDeletedAtIsNull(SEED_EMAIL).isEmpty()) {
                OrganizationUser u = new OrganizationUser();
                u.setOrganizationId(DefaultTenantIds.DEFAULT_ORGANIZATION_ID);
                u.setEmail(SEED_EMAIL);
                u.setPasswordHash(passwordEncoder.encode(SEED_PASSWORD));
                u.setRole(OrganizationUserRole.ADMIN);
                organizationUserRepository.save(u);
            }
        });
    }
}
