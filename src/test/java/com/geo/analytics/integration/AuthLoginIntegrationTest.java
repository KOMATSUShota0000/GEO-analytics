package com.geo.analytics.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.GeoAnalyticsApplication;
import com.geo.analytics.application.service.SyncVerificationService;
import com.geo.analytics.infrastructure.api.GeoCompetitorSearchAdapter;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(classes = GeoAnalyticsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthLoginIntegrationTest extends PostgresSuperuserTestBase {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private GeoCompetitorSearchAdapter geoCompetitorSearchAdapter;

    @MockitoBean
    private SyncVerificationService syncVerificationService;

    @Test
    void loginWithValidUserReturns200AccessTokenAndRefreshCookie() {
        webTestClient
                .post()
                .uri("/api/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("email", "bootstrap@example.com", "password", "bootstrap"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .value(HttpHeaders.SET_COOKIE, v -> assertThat(v).containsIgnoringCase("refresh_token"))
                .expectBody()
                .jsonPath("$.accessToken")
                .exists();
    }

    @Test
    void loginWithInvalidPasswordReturns401() {
        webTestClient
                .post()
                .uri("/api/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("email", "bootstrap@example.com", "password", "wrong-password"))
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }
}
