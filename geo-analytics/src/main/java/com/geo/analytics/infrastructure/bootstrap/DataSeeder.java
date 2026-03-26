package com.geo.analytics.infrastructure.bootstrap;
import com.geo.analytics.domain.entity.UserEntity;
import com.geo.analytics.domain.entity.WorkspaceEntity;
import com.geo.analytics.domain.enums.PricingPlan;
import com.geo.analytics.domain.enums.Role;
import com.geo.analytics.infrastructure.repository.UserRepository;
import com.geo.analytics.infrastructure.repository.WorkspaceRepository;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import com.geo.analytics.infrastructure.tenant.TenantContext;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
@Component
public class DataSeeder implements CommandLineRunner {
    private static final String SEED_USERNAME = "bootstrap";
    private static final String SEED_PASSWORD = "bootstrap";
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    public DataSeeder(WorkspaceRepository workspaceRepository, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }
    @Override
    @Transactional
    public void run(String... args) {
        if (workspaceRepository.count() > 0) {
            return;
        }
        UUID wid = DefaultTenantIds.WORKSPACE_ID;
        TenantContext.setCurrentTenant(wid.toString());
        try {
            WorkspaceEntity w = new WorkspaceEntity();
            w.setId(wid);
            w.setName("Default");
            workspaceRepository.save(w);
            UserEntity u = new UserEntity();
            u.setUsername(SEED_USERNAME);
            u.setPassword(passwordEncoder.encode(SEED_PASSWORD));
            u.setRole(Role.ADMIN);
            u.setPricingPlan(PricingPlan.STANDARD);
            userRepository.save(u);
        } finally {
            TenantContext.clear();
        }
    }
}
