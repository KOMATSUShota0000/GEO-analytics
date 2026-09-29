package com.geo.analytics.application.billing;

import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.exception.CheckoutUnavailableException;
import com.geo.analytics.infrastructure.config.StripeProperties;
import com.stripe.Stripe;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.RateLimitException;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
        if (!properties.getSecretKey().isBlank()) {
            Stripe.apiKey = properties.getSecretKey();
        }
        List<String> missing = Arrays.stream(SubscriptionPlan.values())
                .flatMap(plan -> missingSettingsFor(plan).stream())
                .distinct()
                .toList();
        if (!missing.isEmpty()) {
            LOG.warn("Stripe の設定が足りません（{}）。設定されるまで決済ページは開けません", String.join(", ", missing));
        }
        if (properties.getWebhookSecret().isBlank()) {
            LOG.warn("STRIPE_WEBHOOK_SECRET 未設定: 支払いが終わってもプランは切り替わりません（stripe listen が表示する whsec_... を設定する）");
        }
    }

    public String createCheckoutUrl(UUID workspaceId, SubscriptionPlan plan) {
        Objects.requireNonNull(workspaceId);
        Objects.requireNonNull(plan);
        List<String> missing = missingSettingsFor(plan);
        if (!missing.isEmpty()) {
            LOG.warn("Stripe の設定が足りないため決済ページを作れません（{}） workspace={}", String.join(", ", missing), workspaceId);
            throw new CheckoutUnavailableException(CheckoutUnavailableException.Kind.NOT_CONFIGURED);
        }
        String priceId = planCatalog.priceIdFor(plan);
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
        try {
            return Session.create(params).getUrl();
        } catch (StripeException e) {
            CheckoutUnavailableException.Kind kind = classify(e);
            LOG.warn("Stripe checkout session creation failed for workspace {} (kind={}, code={}, status={})",
                    workspaceId, kind, e.getCode(), e.getStatusCode(), e);
            throw new CheckoutUnavailableException(kind, e);
        }
    }

    List<String> missingSettingsFor(SubscriptionPlan plan) {
        List<String> missing = new ArrayList<>(2);
        if (properties.getSecretKey().isBlank()) {
            missing.add("STRIPE_SECRET_KEY");
        }
        if (planCatalog.priceIdFor(plan).isBlank()) {
            missing.add("STRIPE_PRICE_" + plan.name());
        }
        return missing;
    }

    // Why: 鍵や料金IDの誤りで Stripe に断られた失敗は、時間をおいても通らないので「設定の問題」に寄せる。
    static CheckoutUnavailableException.Kind classify(StripeException e) {
        if (e instanceof RateLimitException) {
            return CheckoutUnavailableException.Kind.TEMPORARY;
        }
        if (e instanceof AuthenticationException || e instanceof InvalidRequestException) {
            return CheckoutUnavailableException.Kind.NOT_CONFIGURED;
        }
        return CheckoutUnavailableException.Kind.TEMPORARY;
    }
}
