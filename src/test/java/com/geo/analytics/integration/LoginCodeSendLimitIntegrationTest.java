package com.geo.analytics.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.GeoAnalyticsApplication;
import com.geo.analytics.application.service.SyncVerificationService;
import com.geo.analytics.infrastructure.api.GeoCompetitorSearchAdapter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * ログインコードの送信回数の上限（#147）を、既定の数字のまま HTTP から確かめる。
 *
 * <p>上限は接続元ごとにも数えるため、テストごとに X-Forwarded-For で別の接続元にする
 * （127.0.0.1 は application.yml の internal-proxies に含まれ、転送元の申告が採用される）。
 */
@SpringBootTest(classes = GeoAnalyticsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("rls-it")
class LoginCodeSendLimitIntegrationTest extends PostgresTestBase {

    private static final String ADMIN_EMAIL = "org-a-admin@test.local";
    private static final String MEMBER_EMAIL = "org-a-member@test.local";
    private static final Duration MAIL_TIMEOUT = Duration.ofSeconds(15);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @DynamicPropertySource
    static void mail(DynamicPropertyRegistry registry) {
        MailpitTestSupport.registerMailProperties(registry);
    }

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private GeoCompetitorSearchAdapter geoCompetitorSearchAdapter;

    @MockitoBean
    private SyncVerificationService syncVerificationService;

    @BeforeEach
    void clearMailbox() {
        MailpitTestSupport.deleteAll();
    }

    @Test
    void resendWithinSixtySeconds_isRejectedWith429_andNoSecondMailIsSent() {
        request(ADMIN_EMAIL, "203.0.113.1").expectStatus().isAccepted();

        EntityExchangeResult<byte[]> second = request(ADMIN_EMAIL, "203.0.113.1")
                .expectStatus().isEqualTo(429)
                .expectHeader().exists(HttpHeaders.RETRY_AFTER)
                .expectBody().returnResult();

        JsonNode body = json(second);
        assertThat(body.path("error_code").asText()).isEqualTo("login_code_resend_too_soon");
        assertThat(body.path("details").path("retry_after_seconds").asLong()).isBetween(1L, 60L);
        MailpitTestSupport.awaitMessagesTo(ADMIN_EMAIL, 1, MAIL_TIMEOUT);
        assertThat(MailpitTestSupport.messagesTo(ADMIN_EMAIL)).hasSize(1);
    }

    @Test
    void unregisteredAddress_isLimitedTheSameWay() {
        request("nobody@test.local", "203.0.113.2").expectStatus().isAccepted();

        JsonNode unregistered = json(request("nobody@test.local", "203.0.113.2")
                .expectStatus().isEqualTo(429).expectBody().returnResult());
        request(MEMBER_EMAIL, "203.0.113.2").expectStatus().isAccepted();
        JsonNode registered = json(request(MEMBER_EMAIL, "203.0.113.2")
                .expectStatus().isEqualTo(429).expectBody().returnResult());

        assertThat(unregistered.path("error_code")).isEqualTo(registered.path("error_code"));
        assertThat(unregistered.path("message")).isEqualTo(registered.path("message"));
    }

    @Test
    void twentyFirstRequestFromTheSameClientWithinAnHour_isRejectedAsSendLimit() {
        for (int i = 0; i < 20; i++) {
            request("visitor" + i + "@test.local", "203.0.113.3").expectStatus().isAccepted();
        }

        JsonNode body = json(request("visitor20@test.local", "203.0.113.3")
                .expectStatus().isEqualTo(429).expectBody().returnResult());
        assertThat(body.path("error_code").asText()).isEqualTo("login_code_send_limit");
        request("visitor20@test.local", "203.0.113.4").expectStatus().isAccepted();
    }

    private WebTestClient.ResponseSpec request(String email, String client) {
        return webTestClient
                .post()
                .uri("/api/auth/code")
                .header("X-Forwarded-For", client)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("email", email))
                .exchange();
    }

    private static JsonNode json(EntityExchangeResult<byte[]> result) {
        try {
            return MAPPER.readTree(new String(result.getResponseBody(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
