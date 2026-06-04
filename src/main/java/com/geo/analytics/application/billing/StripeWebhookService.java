package com.geo.analytics.application.billing;

import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.infrastructure.config.StripeProperties;
import com.geo.analytics.infrastructure.tenant.TenantPlanScope;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Stripe Webhook を検証・ディスパッチする。
 * 署名検証 → イベント種別ごとに workspaceId・plan を抽出 →
 * {@code TenantPlanScope.executeWithTenant} でテナント文脈を確立して同期サービスを呼ぶ。
 */
@Service
public class StripeWebhookService {
    private static final Logger LOG = LoggerFactory.getLogger(StripeWebhookService.class);

    private static final String EVENT_CHECKOUT_COMPLETED = "checkout.session.completed";
    private static final String EVENT_SUBSCRIPTION_UPDATED = "customer.subscription.updated";
    private static final String EVENT_SUBSCRIPTION_DELETED = "customer.subscription.deleted";
    private static final String META_WORKSPACE_ID = "workspaceId";
    private static final String META_PLAN = "plan";

    private final StripeProperties properties;
    private final StripePlanCatalog planCatalog;
    private final StripeSubscriptionSyncService syncService;

    public StripeWebhookService(
            StripeProperties properties,
            StripePlanCatalog planCatalog,
            StripeSubscriptionSyncService syncService) {
        this.properties = Objects.requireNonNull(properties);
        this.planCatalog = Objects.requireNonNull(planCatalog);
        this.syncService = Objects.requireNonNull(syncService);
    }

    /** 署名検証に失敗した場合は {@link SignatureVerificationException} を投げる（呼び出し側で 400 を返す）。 */
    public void handle(String payload, String signatureHeader) throws SignatureVerificationException {
        Event event = Webhook.constructEvent(payload, signatureHeader, properties.getWebhookSecret());
        switch (event.getType()) {
            case EVENT_CHECKOUT_COMPLETED -> handleCheckoutCompleted(event);
            case EVENT_SUBSCRIPTION_UPDATED -> handleSubscriptionUpdated(event);
            case EVENT_SUBSCRIPTION_DELETED -> handleSubscriptionDeleted(event);
            default -> LOG.debug("Ignoring unhandled Stripe event type {}", event.getType());
        }
    }

    private void handleCheckoutCompleted(Event event) {
        StripeObject obj = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(obj instanceof Session session)) {
            LOG.warn("checkout.session.completed without deserializable Session ({})", event.getId());
            return;
        }
        Map<String, String> metadata = session.getMetadata();
        Optional<UUID> workspaceId = workspaceIdFrom(metadata);
        Optional<SubscriptionPlan> plan = planFromMetadata(metadata);
        if (workspaceId.isEmpty() || plan.isEmpty()) {
            LOG.warn("checkout.session.completed missing workspaceId/plan metadata ({})", event.getId());
            return;
        }
        apply(workspaceId.get(), plan.get(), event, session.getCustomer(), session.getSubscription());
    }

    private void handleSubscriptionUpdated(Event event) {
        Subscription subscription = subscriptionFrom(event);
        if (subscription == null) {
            return;
        }
        Optional<UUID> workspaceId = workspaceIdFrom(subscription.getMetadata());
        if (workspaceId.isEmpty()) {
            LOG.warn("customer.subscription.updated missing workspaceId metadata ({})", event.getId());
            return;
        }
        Optional<SubscriptionPlan> plan = planFromSubscriptionPrice(subscription);
        if (plan.isEmpty()) {
            LOG.warn("customer.subscription.updated price not mapped to a plan ({})", event.getId());
            return;
        }
        apply(workspaceId.get(), plan.get(), event, subscription.getCustomer(), subscription.getId());
    }

    private void handleSubscriptionDeleted(Event event) {
        Subscription subscription = subscriptionFrom(event);
        if (subscription == null) {
            return;
        }
        Optional<UUID> workspaceId = workspaceIdFrom(subscription.getMetadata());
        if (workspaceId.isEmpty()) {
            LOG.warn("customer.subscription.deleted missing workspaceId metadata ({})", event.getId());
            return;
        }
        // 解約 → STANDARD へダウングレード。
        apply(workspaceId.get(), SubscriptionPlan.STANDARD, event, subscription.getCustomer(), subscription.getId());
    }

    private void apply(
            UUID workspaceId, SubscriptionPlan plan, Event event, String customerId, String subscriptionId) {
        TenantPlanScope.executeWithTenant(workspaceId, () -> syncService.applyPlanChange(
                workspaceId, plan, event.getId(), event.getType(), customerId, subscriptionId));
    }

    private Subscription subscriptionFrom(Event event) {
        StripeObject obj = event.getDataObjectDeserializer().getObject().orElse(null);
        if (obj instanceof Subscription subscription) {
            return subscription;
        }
        LOG.warn("{} without deserializable Subscription ({})", event.getType(), event.getId());
        return null;
    }

    private Optional<UUID> workspaceIdFrom(Map<String, String> metadata) {
        if (metadata == null) {
            return Optional.empty();
        }
        String raw = metadata.get(META_WORKSPACE_ID);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Optional<SubscriptionPlan> planFromMetadata(Map<String, String> metadata) {
        if (metadata == null) {
            return Optional.empty();
        }
        String raw = metadata.get(META_PLAN);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(SubscriptionPlan.valueOf(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Optional<SubscriptionPlan> planFromSubscriptionPrice(Subscription subscription) {
        if (subscription.getItems() == null
                || subscription.getItems().getData() == null
                || subscription.getItems().getData().isEmpty()) {
            return Optional.empty();
        }
        var item = subscription.getItems().getData().get(0);
        if (item.getPrice() == null) {
            return Optional.empty();
        }
        return planCatalog.planForPrice(item.getPrice().getId());
    }
}
