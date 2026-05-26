package com.geo.analytics.application.service;

import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.infrastructure.repository.WorkspaceRepository;
import com.geo.analytics.infrastructure.tenant.TenantPlanScope;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class SubscriptionManagementService {
    private final WorkspaceRepository workspaceRepository;
    private final PlanBasedQuotaManager planBasedQuotaManager;

    public SubscriptionManagementService(
            WorkspaceRepository workspaceRepository,
            PlanBasedQuotaManager planBasedQuotaManager) {
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository);
        this.planBasedQuotaManager = Objects.requireNonNull(planBasedQuotaManager);
    }

    @Transactional
    public void changePlan(UUID tenantId, SubscriptionPlan newPlan) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(newPlan);
        TenantPlanScope.executeWithTenant(tenantId, () -> {
            var workspace = workspaceRepository.findById(tenantId)
                    .orElseThrow(() -> new EntityNotFoundException("workspace not found"));
            workspace.setSubscriptionPlan(newPlan);
            workspaceRepository.save(workspace);
        });
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                planBasedQuotaManager.invalidateTenantBucket(tenantId);
            }
        });
    }
}
