package com.geo.analytics.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.geo.analytics.GeoAnalyticsApplication;
import com.geo.analytics.application.billing.StripeCheckoutService;
import com.geo.analytics.application.billing.StripePlanCatalog;
import com.geo.analytics.application.billing.StripeSubscriptionSyncService;
import com.geo.analytics.application.billing.StripeWebhookService;
import com.geo.analytics.application.service.SyncVerificationService;
import com.geo.analytics.application.service.WorkspacePlanResolver;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.exception.CheckoutUnavailableException;
import com.geo.analytics.domain.exception.CheckoutUnavailableException.Kind;
import com.geo.analytics.infrastructure.api.GeoCompetitorSearchAdapter;
import com.geo.analytics.infrastructure.config.StripeProperties;
import com.stripe.net.Webhook;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Stripe からのお知らせが、今の契約のものだけプランと契約IDを変えることを、RLS が効く接続（api_worker）で確かめる（#168）。
 * 署名付きのお知らせを受け口に渡し、テナント文脈を張るところから DB の書き換えまでを通す。
 */
@SpringBootTest(classes = GeoAnalyticsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("rls-it")
class StripeSubscriptionRlsIntegrationTest extends PostgresTestBase {

    private static final UUID ORG_A = UUID.fromString("11111111-1111-1111-1111-111111111101");
    private static final UUID WORKSPACE = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0168");
    private static final String SECRET = "whsec_it_168";
    private static final String CURRENT = "sub_current_168";
    private static final String OLD = "sub_old_168";

    @Autowired
    private StripeSubscriptionSyncService syncService;

    @Autowired
    private WorkspacePlanResolver workspacePlanResolver;

    @Autowired
    @Qualifier("batchJdbcTemplate")
    private JdbcTemplate batchJdbcTemplate;

    @MockitoBean
    private GeoCompetitorSearchAdapter geoCompetitorSearchAdapter;

    @MockitoBean
    private SyncVerificationService syncVerificationService;

    private StripeWebhookService webhookService;
    private StripeCheckoutService checkoutService;

    @BeforeEach
    void setUp() {
        StripeProperties properties = new StripeProperties();
        properties.setWebhookSecret(SECRET);
        properties.getPrices().setPro("price_it_pro");
        properties.getPrices().setExpert("price_it_expert");
        StripePlanCatalog catalog = new StripePlanCatalog(properties);
        webhookService = new StripeWebhookService(properties, catalog, syncService);
        checkoutService = new StripeCheckoutService(properties, catalog, workspacePlanResolver);
        seedWorkspace(CURRENT);
    }

    @Test
    void endingTheCurrentSubscription_returnsToStandard_andAllowsSubscribingAgain() throws Exception {
        assertThat(checkoutRefusal()).isEqualTo(Kind.ALREADY_SUBSCRIBED);

        receive("customer.subscription.deleted", subscription(CURRENT, "price_it_expert"));

        assertThat(workspace()).containsEntry("subscription_plan", "STANDARD").containsEntry("stripe_subscription_id", null);
        // Why: 鍵を入れていないので「設定の問題」で止まる。支払い中を理由に断られなければ、もう一度申し込める
        assertThat(checkoutRefusal()).isEqualTo(Kind.NOT_CONFIGURED);
    }

    @Test
    void endingAnOldSubscription_keepsThePlanAndTheCurrentSubscription() throws Exception {
        String eventId = receive("customer.subscription.deleted", subscription(OLD, "price_it_expert"));

        assertThat(workspace()).containsEntry("subscription_plan", "EXPERT").containsEntry("stripe_subscription_id", CURRENT);
        assertThat(processedCount(eventId)).isEqualTo(1);
    }

    @Test
    void updateOfAnOldSubscription_doesNotReplaceTheCurrentOne() throws Exception {
        String eventId = receive("customer.subscription.updated", subscription(OLD, "price_it_pro"));

        assertThat(workspace()).containsEntry("subscription_plan", "EXPERT").containsEntry("stripe_subscription_id", CURRENT);
        assertThat(processedCount(eventId)).isEqualTo(1);
    }

    @Test
    void updateOfTheCurrentSubscription_changesThePlan() throws Exception {
        receive("customer.subscription.updated", subscription(CURRENT, "price_it_pro"));

        assertThat(workspace()).containsEntry("subscription_plan", "PRO").containsEntry("stripe_subscription_id", CURRENT);
    }

    @Test
    void updateArrivingBeforeAnySubscriptionIsRecorded_isApplied() throws Exception {
        seedWorkspace(null);

        receive("customer.subscription.updated", subscription("sub_new_168", "price_it_expert"));

        assertThat(workspace()).containsEntry("subscription_plan", "EXPERT").containsEntry("stripe_subscription_id", "sub_new_168");
    }

    private void seedWorkspace(String subscriptionId) {
        // Why: batch_worker は BYPASSRLS のため、テナント文脈なしで行を用意できる
        batchJdbcTemplate.update("""
                INSERT INTO workspaces (id, name, subscription_plan, organization_id, stripe_customer_id, stripe_subscription_id, created_at, updated_at)
                VALUES (?, 'Workspace 168', 'EXPERT', ?, 'cus_it_168', ?, now(), now())
                ON CONFLICT (id) DO UPDATE SET subscription_plan = 'EXPERT', stripe_subscription_id = EXCLUDED.stripe_subscription_id
                """, WORKSPACE, ORG_A, subscriptionId);
    }

    private Map<String, Object> workspace() {
        return batchJdbcTemplate.queryForMap(
                "SELECT subscription_plan::text AS subscription_plan, stripe_subscription_id FROM workspaces WHERE id = ?", WORKSPACE);
    }

    private Integer processedCount(String eventId) {
        return batchJdbcTemplate.queryForObject(
                "SELECT count(*) FROM processed_stripe_events WHERE event_id = ?", Integer.class, eventId);
    }

    private Kind checkoutRefusal() {
        Throwable thrown = catchThrowable(
                () -> checkoutService.createCheckoutUrl(WORKSPACE, SubscriptionPlan.PRO));
        assertThat(thrown).isInstanceOf(CheckoutUnavailableException.class);
        return ((CheckoutUnavailableException) thrown).getKind();
    }

    private String receive(String type, String dataObject) throws Exception {
        String eventId = "evt_it_" + UUID.randomUUID().toString().replace("-", "");
        String payload = """
                {"id":"%s","object":"event","api_version":"2026-05-27.dahlia","created":1790000000,"livemode":false,
                 "type":"%s","data":{"object":%s}}
                """.formatted(eventId, type, dataObject);
        long timestamp = Webhook.Util.getTimeNow();
        String signature = "t=" + timestamp + ",v1=" + Webhook.Util.computeHmacSha256(SECRET, timestamp + "." + payload);
        webhookService.handle(payload, signature);
        return eventId;
    }

    private static String subscription(String subscriptionId, String priceId) {
        return """
                {"id":"%s","object":"subscription","customer":"cus_it_168","status":"active",
                 "metadata":{"workspaceId":"%s","plan":"EXPERT"},
                 "items":{"object":"list","data":[{"id":"si_it","object":"subscription_item",
                   "price":{"id":"%s","object":"price"}}]}}
                """.formatted(subscriptionId, WORKSPACE, priceId);
    }
}
