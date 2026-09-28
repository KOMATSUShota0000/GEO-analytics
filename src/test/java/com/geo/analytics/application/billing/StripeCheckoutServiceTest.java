package com.geo.analytics.application.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.exception.CheckoutUnavailableException;
import com.geo.analytics.domain.exception.CheckoutUnavailableException.Kind;
import com.geo.analytics.infrastructure.config.StripeProperties;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.ApiException;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.PermissionException;
import com.stripe.exception.RateLimitException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// init() は Stripe.apiKey（全体で共有される静的変数）を書き換えるため、ここでは呼ばない。
class StripeCheckoutServiceTest {

    private static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000167");

    private static StripeCheckoutService serviceWith(String secretKey, String proPrice) {
        StripeProperties properties = new StripeProperties();
        properties.setSecretKey(secretKey);
        properties.getPrices().setPro(proPrice);
        return new StripeCheckoutService(properties, new StripePlanCatalog(properties));
    }

    @Test
    void missingSettingsFor_namesEachMissingSettingForThePlan() {
        assertThat(serviceWith("", "").missingSettingsFor(SubscriptionPlan.PRO))
                .containsExactly("STRIPE_SECRET_KEY", "STRIPE_PRICE_PRO");
        assertThat(serviceWith("sk_test_dummy", "").missingSettingsFor(SubscriptionPlan.PRO))
                .containsExactly("STRIPE_PRICE_PRO");
        assertThat(serviceWith("sk_test_dummy", "price_pro_dummy").missingSettingsFor(SubscriptionPlan.PRO))
                .isEmpty();
        assertThat(serviceWith("sk_test_dummy", "price_pro_dummy").missingSettingsFor(SubscriptionPlan.EXPERT))
                .containsExactly("STRIPE_PRICE_EXPERT");
    }

    @Test
    void createCheckoutUrl_withoutSecretKey_isNotConfigured_beforeCallingStripe() {
        assertThatThrownBy(() -> serviceWith("", "price_pro_dummy").createCheckoutUrl(WORKSPACE_ID, SubscriptionPlan.PRO))
                .isInstanceOfSatisfying(CheckoutUnavailableException.class,
                        e -> assertThat(e.getKind()).isEqualTo(Kind.NOT_CONFIGURED));
    }

    @Test
    void createCheckoutUrl_withoutPriceForThePlan_isNotConfigured_beforeCallingStripe() {
        assertThatThrownBy(() -> serviceWith("sk_test_dummy", "").createCheckoutUrl(WORKSPACE_ID, SubscriptionPlan.PRO))
                .isInstanceOfSatisfying(CheckoutUnavailableException.class,
                        e -> assertThat(e.getKind()).isEqualTo(Kind.NOT_CONFIGURED));
    }

    @Test
    void classify_rejectionsCausedBySettings_areNotConfigured() {
        assertThat(StripeCheckoutService.classify(
                new AuthenticationException("Invalid API Key provided", "req_1", null, 401)))
                .isEqualTo(Kind.NOT_CONFIGURED);
        assertThat(StripeCheckoutService.classify(
                new PermissionException("The provided key does not have access", "req_2", null, 403)))
                .isEqualTo(Kind.NOT_CONFIGURED);
        assertThat(StripeCheckoutService.classify(
                new InvalidRequestException("No such price: 'price_x'", "line_items[0][price]", "req_3",
                        "resource_missing", 400, null)))
                .isEqualTo(Kind.NOT_CONFIGURED);
    }

    @Test
    void classify_failuresThatMayPassOnRetry_areTemporary() {
        assertThat(StripeCheckoutService.classify(
                new RateLimitException("Too many requests", null, "req_4", "rate_limit", 429, null)))
                .isEqualTo(Kind.TEMPORARY);
        assertThat(StripeCheckoutService.classify(new ApiConnectionException("Could not connect to Stripe")))
                .isEqualTo(Kind.TEMPORARY);
        assertThat(StripeCheckoutService.classify(
                new ApiException("An error occurred with our API", "req_5", null, 500, null)))
                .isEqualTo(Kind.TEMPORARY);
    }
}
