package com.geo.analytics.application.billing;

import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.infrastructure.config.StripeProperties;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Stripe Checkout（リダイレクト方式）のセッションを生成する。
 * mode=subscription で月額サブスクを開始し、session/subscription の双方に workspaceId・plan を
 * メタデータとして埋め込む（Webhook 側でテナントとプランを解決できるようにするため）。
 */
@Service
public class StripeCheckoutService {
    private static final Logger LOG = LoggerFactory.getLogger(StripeCheckoutService.class);

    private final StripeProperties properties;
    private final StripePlanCatalog planCatalog;

    public StripeCheckoutService(StripeProperties properties, StripePlanCatalog planCatalog) {
        this.properties = Objects.requireNonNull(properties);
        this.planCatalog = Objects.requireNonNull(planCatalog);
    }

    @PostConstruct
    void init() {
        if (properties.getSecretKey() != null && !properties.getSecretKey().isBlank()) {
            Stripe.apiKey = properties.getSecretKey();
        } else {
            LOG.warn("STRIPE_SECRET_KEY 未設定: Checkout は鍵が注入されるまで利用できません");
        }
    }

    public String createCheckoutUrl(UUID workspaceId, SubscriptionPlan plan) throws StripeException {
        Objects.requireNonNull(workspaceId);
        Objects.requireNonNull(plan);
        String priceId = planCatalog.priceIdFor(plan);
        if (priceId == null || priceId.isBlank()) {
            throw new IllegalStateException("Stripe price id is not configured for plan " + plan);
        }
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl(properties.getSuccessUrl())
                .setCancelUrl(properties.getCancelUrl())
                .setClientReferenceId(workspaceId.toString())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPrice(priceId)
                        .setQuantity(1L)
                        .build())
                .putMetadata("workspaceId", workspaceId.toString())
                .putMetadata("plan", plan.name())
                .setSubscriptionData(SessionCreateParams.SubscriptionData.builder()
                        .putMetadata("workspaceId", workspaceId.toString())
                        .putMetadata("plan", plan.name())
                        .build())
                .build();
        Session session = Session.create(params);
        return session.getUrl();
    }
}
