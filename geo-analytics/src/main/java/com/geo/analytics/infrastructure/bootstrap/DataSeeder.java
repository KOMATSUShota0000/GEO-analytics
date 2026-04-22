package com.geo.analytics.infrastructure.bootstrap;

import com.geo.analytics.domain.entity.OrganizationUser;
import com.geo.analytics.domain.entity.WorkspaceEntity;
import com.geo.analytics.domain.enums.OrganizationUserRole;
import com.geo.analytics.infrastructure.repository.OrganizationUserRepository;
import com.geo.analytics.infrastructure.repository.WorkspaceRepository;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import com.geo.analytics.infrastructure.tenant.TenantIdentity;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import com.geo.analytics.infrastructure.tenant.TenantPlanScope;
import java.lang.ScopedValue;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "app.bootstrap", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String SEED_EMAIL = "bootstrap@example.com";
    private static final String SEED_PASSWORD = "bootstrap";

    private final DataSeeder self;
    private final WorkspaceRepository workspaceRepository;
    private final OrganizationUserRepository organizationUserRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(
            @Lazy DataSeeder self,
            WorkspaceRepository workspaceRepository,
            OrganizationUserRepository organizationUserRepository,
            PasswordEncoder passwordEncoder) {
        this.self = self;
        this.workspaceRepository = workspaceRepository;
        this.organizationUserRepository = organizationUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        UUID orgId = DefaultTenantIds.DEFAULT_ORGANIZATION_ID;
        UUID wid = DefaultTenantIds.WORKSPACE_ID;
        ScopedValue.where(TenantContextHolder.CONTEXT, new TenantIdentity(orgId, wid, null))
                .run(() -> TenantPlanScope.executeWithTenant(wid, () -> self.seedData(orgId, wid)));
    }

    @Transactional
    public void seedData(UUID orgId, UUID wid) {
        if (workspaceRepository.count() == 0) {
            WorkspaceEntity w = new WorkspaceEntity();
            w.setId(wid);
            w.setOrganizationId(orgId);
            w.setName("Default");
            workspaceRepository.save(w);
            if (organizationUserRepository.findByEmailAndDeletedAtIsNull(SEED_EMAIL).isEmpty()) {
                OrganizationUser u = new OrganizationUser();
                u.setOrganizationId(orgId);
                u.setEmail(SEED_EMAIL);
                u.setPasswordHash(passwordEncoder.encode(SEED_PASSWORD));
                u.setRole(OrganizationUserRole.ADMIN);
                organizationUserRepository.save(u);
            }
        }
        log.info("\n=========================================\n"
                + "[DEV] 初期ユーザーでのログイン情報:\n"
                + "Email: {}\n"
                + "Password: {}\n"
                + "=========================================", SEED_EMAIL, SEED_PASSWORD);
    }
}
