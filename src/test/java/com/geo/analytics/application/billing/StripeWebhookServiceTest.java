package com.geo.analytics.application.billing;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.infrastructure.config.StripeProperties;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Why: お知らせは Stripe アカウントの既定の版で届く。ライブラリと版の系統（acacia・dahlia など）が違うと中身を読めず、
//      [200] を返したままプランが切り替わらない（#167 の実機確認で発覚）。オーナーの Stripe が送る版で組み立てて確かめる。
class StripeWebhookServiceTest {

    private static final String SECRET = "whsec_test_dummy";
    private static final String ACCOUNT_API_VERSION = "2026-05-27.dahlia";
    private static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000167");

    private StripeSubscriptionSyncService syncService;
    private StripeWebhookService service;

    @BeforeEach
    void setUp() {
        StripeProperties properties = new StripeProperties();
        properties.setWebhookSecret(SECRET);
        properties.getPrices().setPro("price_pro_dummy");
        properties.getPrices().setExpert("price_expert_dummy");
        syncService = mock(StripeSubscriptionSyncService.class);
        service = new StripeWebhookService(properties, new StripePlanCatalog(properties), syncService);
    }

    @Test
    void checkoutCompleted_appliesThePlanChosenOnTheCheckoutPage() throws Exception {
        String payload = event("evt_checkout", "checkout.session.completed", """
                {"id":"cs_test_1","object":"checkout.session","customer":"cus_1","subscription":"sub_1",
                 "client_reference_id":"%1$s","metadata":{"workspaceId":"%1$s","plan":"EXPERT"}}
                """.formatted(WORKSPACE_ID));

        service.handle(payload, sign(payload, SECRET));

        verify(syncService).applyPlanChange(
                WORKSPACE_ID, SubscriptionPlan.EXPERT, "evt_checkout", "checkout.session.completed", "cus_1", "sub_1");
    }

    @Test
    void subscriptionUpdated_appliesThePlanOfTheCurrentPrice() throws Exception {
        String payload = event("evt_updated", "customer.subscription.updated", subscription("price_pro_dummy"));

        service.handle(payload, sign(payload, SECRET));

        verify(syncService).applySubscriptionUpdate(
                WORKSPACE_ID, SubscriptionPlan.PRO, "evt_updated", "customer.subscription.updated", "cus_1", "sub_1");
    }

    @Test
    void subscriptionDeleted_isPassedOnWithTheEndedSubscription() throws Exception {
        String payload = event("evt_deleted", "customer.subscription.deleted", subscription("price_expert_dummy"));

        service.handle(payload, sign(payload, SECRET));

        verify(syncService).applySubscriptionDeleted(
                WORKSPACE_ID, "evt_deleted", "customer.subscription.deleted", "cus_1", "sub_1");
    }

    @Test
    void signedWithAnotherSecret_isRejectedWithoutChangingThePlan() throws Exception {
        String payload = event("evt_forged", "customer.subscription.updated", subscription("price_expert_dummy"));

        assertThatThrownBy(() -> service.handle(payload, sign(payload, "whsec_someone_else")))
                .isInstanceOf(SignatureVerificationException.class);
        verifyNoInteractions(syncService);
    }

    private static String subscription(String priceId) {
        return """
                {"id":"sub_1","object":"subscription","customer":"cus_1","status":"active",
                 "metadata":{"workspaceId":"%s","plan":"EXPERT"},
                 "items":{"object":"list","data":[{"id":"si_1","object":"subscription_item",
                   "price":{"id":"%s","object":"price"}}]}}
                """.formatted(WORKSPACE_ID, priceId);
    }

    private static String event(String id, String type, String dataObject) {
        return """
                {"id":"%s","object":"event","api_version":"%s","created":1790000000,"livemode":false,
                 "type":"%s","data":{"object":%s}}
                """.formatted(id, ACCOUNT_API_VERSION, type, dataObject);
    }

    private static String sign(String payload, String secret) throws Exception {
        long timestamp = Webhook.Util.getTimeNow();
        return "t=" + timestamp + ",v1=" + Webhook.Util.computeHmacSha256(secret, timestamp + "." + payload);
    }
}
