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

    // Why: 契約が2つあるとき、古いほうの更新のお知らせで「今の契約」が書き換わると、その後の解約の判定が逆になる（#168）。
    //      申し込み直後で契約IDをまだ記録していないときは、どの契約のお知らせでも反映する。
    @Transactional
    public void applySubscriptionUpdate(
            UUID workspaceId,
            SubscriptionPlan newPlan,
            String eventId,
            String eventType,
            String stripeCustomerId,
            String stripeSubscriptionId) {
        Objects.requireNonNull(eventId);
        if (processedStripeEventRepository.existsByEventId(eventId)) {
            LOG.info("Stripe event {} already processed; skipping (idempotent)", eventId);
            return;
        }
        WorkspaceEntity workspace = findWorkspace(workspaceId);
        String current = workspace.getStripeSubscriptionId();
        if (current != null && !current.equals(stripeSubscriptionId)) {
            recordWithoutChange(workspace, eventId, eventType, stripeSubscriptionId);
            return;
        }
        applyPlanChange(workspaceId, newPlan, eventId, eventType, stripeCustomerId, stripeSubscriptionId);
    }

    // Why: 契約IDを残すと「支払い中」のままになり、解約した人がもう一度申し込めない。
    //      古い契約の解約で Standard に戻すと、今の契約を払っているのに Standard に落ちる（#168）。
    @Transactional
    public void applySubscriptionDeleted(
            UUID workspaceId,
            String eventId,
            String eventType,
            String stripeCustomerId,
            String stripeSubscriptionId) {
        Objects.requireNonNull(eventId);
        if (processedStripeEventRepository.existsByEventId(eventId)) {
            LOG.info("Stripe event {} already processed; skipping (idempotent)", eventId);
            return;
        }
        WorkspaceEntity workspace = findWorkspace(workspaceId);
        if (stripeSubscriptionId == null || !stripeSubscriptionId.equals(workspace.getStripeSubscriptionId())) {
            recordWithoutChange(workspace, eventId, eventType, stripeSubscriptionId);
            return;
        }
        workspace.setStripeSubscriptionId(null);
        applyPlanChange(workspaceId, SubscriptionPlan.STANDARD, eventId, eventType, stripeCustomerId, null);
    }

    private WorkspaceEntity findWorkspace(UUID workspaceId) {
        Objects.requireNonNull(workspaceId);
        return workspaceRepository
                .findById(workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("workspace not found: " + workspaceId));
    }

    private void recordWithoutChange(
            WorkspaceEntity workspace, String eventId, String eventType, String stripeSubscriptionId) {
        processedStripeEventRepository.save(new ProcessedStripeEventEntity(
                UUID.randomUUID(), workspace.getOrganizationId(), eventId, eventType));
        LOG.info("Stripe event {} ({}) is for subscription {}, not the current one {} of workspace {}; recorded without changing the plan",
                eventId, eventType, stripeSubscriptionId, workspace.getStripeSubscriptionId(), workspace.getId());
    }
}
