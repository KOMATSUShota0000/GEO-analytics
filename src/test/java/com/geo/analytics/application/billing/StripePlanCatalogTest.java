package com.geo.analytics.application.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.infrastructure.config.StripeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StripePlanCatalogTest {

    private static final String STANDARD_PRICE = "price_standard_test";
    private static final String PRO_PRICE = "price_pro_test";
    private static final String EXPERT_PRICE = "price_expert_test";

    private StripePlanCatalog catalog;

    @BeforeEach
    void setUp() {
        StripeProperties properties = new StripeProperties();
        properties.getPrices().setStandard(STANDARD_PRICE);
        properties.getPrices().setPro(PRO_PRICE);
        properties.getPrices().setExpert(EXPERT_PRICE);
        catalog = new StripePlanCatalog(properties);
    }

    @Test
    void priceIdFor_mapsEachPlanToConfiguredPrice() {
        assertThat(catalog.priceIdFor(SubscriptionPlan.STANDARD)).isEqualTo(STANDARD_PRICE);
        assertThat(catalog.priceIdFor(SubscriptionPlan.PRO)).isEqualTo(PRO_PRICE);
        assertThat(catalog.priceIdFor(SubscriptionPlan.EXPERT)).isEqualTo(EXPERT_PRICE);
    }

    @Test
    void planForPrice_roundTripsKnownPrices() {
        assertThat(catalog.planForPrice(STANDARD_PRICE)).contains(SubscriptionPlan.STANDARD);
        assertThat(catalog.planForPrice(PRO_PRICE)).contains(SubscriptionPlan.PRO);
        assertThat(catalog.planForPrice(EXPERT_PRICE)).contains(SubscriptionPlan.EXPERT);
    }

    @Test
    void planForPrice_returnsEmptyForUnknownOrBlank() {
        assertThat(catalog.planForPrice("price_unknown")).isEmpty();
        assertThat(catalog.planForPrice("")).isEmpty();
        assertThat(catalog.planForPrice(null)).isEmpty();
    }
}
