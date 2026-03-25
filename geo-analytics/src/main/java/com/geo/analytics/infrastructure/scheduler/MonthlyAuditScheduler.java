package com.geo.analytics.infrastructure.scheduler;

import com.geo.analytics.application.service.MonthlyAuditJobLauncher;
import com.geo.analytics.domain.entity.ProjectEntity;
import com.geo.analytics.infrastructure.repository.ProjectRepository;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import com.geo.analytics.infrastructure.tenant.TenantContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class MonthlyAuditScheduler {
    private final ProjectRepository projectRepository;
    private final MonthlyAuditJobLauncher monthlyAuditJobLauncher;

    public MonthlyAuditScheduler(ProjectRepository projectRepository, MonthlyAuditJobLauncher monthlyAuditJobLauncher) {
        this.projectRepository = projectRepository;
        this.monthlyAuditJobLauncher = monthlyAuditJobLauncher;
    }

    @Scheduled(cron = "0 0 2 1 * *", zone = "Asia/Tokyo")
    public void triggerMonthlyAudits() {
        List<ProjectEntity> projectEntities = TenantContext.executeWithTenant(
            DefaultTenantIds.WORKSPACE_ID,
            () -> projectRepository.findByAutoAuditEnabledIsTrue());
        ThreadLocalRandom threadLocalRandom = ThreadLocalRandom.current();
        for (ProjectEntity projectEntity : projectEntities) {
            monthlyAuditJobLauncher.fire(projectEntity.getId());
            try {
                Thread.sleep(5000L + threadLocalRandom.nextLong(5000L));
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
