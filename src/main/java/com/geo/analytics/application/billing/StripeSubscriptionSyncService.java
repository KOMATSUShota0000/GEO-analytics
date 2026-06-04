package com.geo.analytics.application.billing;

import com.geo.analytics.application.service.PlanBasedQuotaManager;
import com.geo.analytics.domain.entity.ProcessedStripeEventEntity;
import com.geo.analytics.domain.entity.WorkspaceEntity;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.infrastructure.repository.ProcessedStripeEventRepository;
import com.geo.analytics.infrastructure.repository.WorkspaceRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Stripe Webhook 由来のサブスク状態をワークスペースへ反映する。
 *
 * <p>呼び出し側（{@link StripeWebhookService}）が {@code TenantPlanScope.executeWithTenant(workspaceId, ...)}
 * でテナント文脈を確立した内側で実行されることを前提とする。RLS インターセプタは workspaceId から
 * organization_id を解決し {@code app.current_org_id} を設定するため、テナント隔離は維持される。
 */
@Service
public class StripeSubscriptionSyncService {
    private static final Logger LOG = LoggerFactory.getLogger(StripeSubscriptionSyncService.class);

    private final WorkspaceRepository workspaceRepository;
    private final ProcessedStripeEventRepository processedStripeEventRepository;
    private final PlanBasedQuotaManager planBasedQuotaManager;

    public StripeSubscriptionSyncService(
            WorkspaceRepository workspaceRepository,
            ProcessedStripeEventRepository processedStripeEventRepository,
            PlanBasedQuotaManager planBasedQuotaManager) {
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository);
        this.processedStripeEventRepository = Objects.requireNonNull(processedStripeEventRepository);
        this.planBasedQuotaManager = Objects.requireNonNull(planBasedQuotaManager);
    }

    @Transactional
    public void applyPlanChange(
            UUID workspaceId,
            SubscriptionPlan newPlan,
            String eventId,
            String eventType,
            String stripeCustomerId,
            String stripeSubscriptionId) {
        Objects.requireNonNull(workspaceId);
        Objects.requireNonNull(newPlan);
        Objects.requireNonNull(eventId);

        // 冪等性: 同一イベントの重複配信では二重に適用しない。
        if (processedStripeEventRepository.existsByEventId(eventId)) {
            LOG.info("Stripe event {} already processed; skipping (idempotent)", eventId);
            return;
        }

        WorkspaceEntity workspace = workspaceRepository
                .findById(workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("workspace not found: " + workspaceId));
        workspace.setSubscriptionPlan(newPlan);
        if (stripeCustomerId != null && !stripeCustomerId.isBlank()) {
            workspace.setStripeCustomerId(stripeCustomerId);
        }
        if (stripeSubscriptionId != null && !stripeSubscriptionId.isBlank()) {
            workspace.setStripeSubscriptionId(stripeSubscriptionId);
        }
        workspaceRepository.save(workspace);

        processedStripeEventRepository.save(new ProcessedStripeEventEntity(
                UUID.randomUUID(), workspace.getOrganizationId(), eventId, eventType));

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                planBasedQuotaManager.invalidateTenantBucket(workspaceId);
            }
        });
        LOG.info("Applied Stripe event {} ({}) -> workspace {} plan {}", eventId, eventType, workspaceId, newPlan);
    }
}
