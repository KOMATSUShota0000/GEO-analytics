package com.geo.analytics.infrastructure.bootstrap;

import com.geo.analytics.domain.entity.OrganizationUser;
import com.geo.analytics.domain.entity.WorkspaceEntity;
import com.geo.analytics.domain.enums.OrganizationUserRole;
import com.geo.analytics.infrastructure.config.AppProperties;
import com.geo.analytics.infrastructure.repository.OrganizationRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "app.bootstrap", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    static final long DEV_CREDIT_TOPUP = 1_000_000L;

    private final DataSeeder self;
    private final WorkspaceRepository workspaceRepository;
    private final OrganizationUserRepository organizationUserRepository;
    private final OrganizationRepository organizationRepository;
    private final String seedEmail;

    public DataSeeder(
            @Lazy DataSeeder self,
            WorkspaceRepository workspaceRepository,
            OrganizationUserRepository organizationUserRepository,
            OrganizationRepository organizationRepository,
            AppProperties appProperties) {
        this.self = self;
        this.workspaceRepository = workspaceRepository;
        this.organizationUserRepository = organizationUserRepository;
        this.organizationRepository = organizationRepository;
        this.seedEmail = appProperties.getBootstrap().getEmail();
    }

    @Override
    public void run(String... args) {
        UUID orgId = DefaultTenantIds.DEFAULT_ORGANIZATION_ID;
        UUID wid = DefaultTenantIds.WORKSPACE_ID;
        ScopedValue.where(TenantContextHolder.CONTEXT, new TenantIdentity(orgId, wid, null))
                .run(() -> TenantPlanScope.executeWithTenant(wid, () -> {
                    self.seedData(orgId, wid);
                    // Why: 同じアドレスのユーザーが削除済み・別組織にいると RLS で見えず一意制約に当たる。
                    //      クレジット補充と別トランザクションにし、作れなくても起動は続ける。
                    try {
                        self.ensureSeedUser(orgId);
                    } catch (DataIntegrityViolationException e) {
                        log.warn("[DEV] 初期ユーザー {} を作成できませんでした（同じアドレスのユーザーが削除済み、または別の組織に存在します）", seedEmail);
                    }
                }));
    }

    @Transactional
    public void seedData(UUID orgId, UUID wid) {
        organizationRepository.findByIdForUpdate(orgId).ifPresent(org -> {
            if (org.getCreditBalance() == 0) {
                org.setCreditBalance(DEV_CREDIT_TOPUP);
                log.info("[DEV] Topped up credit balance for default organization: {} credits", DEV_CREDIT_TOPUP);
            }
        });
        if (workspaceRepository.count() == 0) {
            WorkspaceEntity w = new WorkspaceEntity();
            w.setId(wid);
            w.setOrganizationId(orgId);
            w.setName("Default");
            workspaceRepository.save(w);
        }
    }

    // Why: 作成をワークスペース0件（初回起動）に限ると、既存の開発DBで .env のアドレスを変えても反映されない。
    @Transactional
    public void ensureSeedUser(UUID orgId) {
        if (organizationUserRepository.findByEmailAndDeletedAtIsNull(seedEmail).isEmpty()) {
            OrganizationUser u = new OrganizationUser();
            u.setOrganizationId(orgId);
            u.setEmail(seedEmail);
            u.setRole(OrganizationUserRole.ADMIN);
            organizationUserRepository.saveAndFlush(u);
            log.info("[DEV] 初期ユーザーを作成しました: {}", seedEmail);
        }
        log.info("\n=========================================\n"
                + "[DEV] 初期ユーザー: {}\n"
                + "ログインはメールに届くコードで行う（開発では Mailpit http://localhost:8025）\n"
                + "=========================================", seedEmail);
    }
}
