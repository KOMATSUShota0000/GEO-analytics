package com.geo.analytics.application.billing;

import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.infrastructure.config.StripeProperties;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * SubscriptionPlan と Stripe 価格ID（price_...）の相互変換。
 * Checkout 作成時は plan→price、Webhook 受信時は price→plan に使う。
 */
@Component
public class StripePlanCatalog {
    private final StripeProperties properties;

    public StripePlanCatalog(StripeProperties properties) {
        this.properties = Objects.requireNonNull(properties);
    }

    public String priceIdFor(SubscriptionPlan plan) {
        StripeProperties.Prices prices = properties.getPrices();
        return switch (plan) {
            case STANDARD -> prices.getStandard();
            case PRO -> prices.getPro();
            case EXPERT -> prices.getExpert();
        };
    }

    public Optional<SubscriptionPlan> planForPrice(String priceId) {
        if (priceId == null || priceId.isBlank()) {
            return Optional.empty();
        }
        StripeProperties.Prices prices = properties.getPrices();
        if (priceId.equals(prices.getStandard())) {
            return Optional.of(SubscriptionPlan.STANDARD);
        }
        if (priceId.equals(prices.getPro())) {
            return Optional.of(SubscriptionPlan.PRO);
        }
        if (priceId.equals(prices.getExpert())) {
            return Optional.of(SubscriptionPlan.EXPERT);
        }
        return Optional.empty();
    }
}
