package com.geo.analytics.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.GeoAnalyticsApplication;
import com.geo.analytics.application.service.AsyncBatchService;
import com.geo.analytics.application.service.SyncVerificationService;
import com.geo.analytics.infrastructure.ai.GeminiBatchClient;
import com.geo.analytics.infrastructure.api.GeoCompetitorSearchAdapter;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import com.geo.analytics.infrastructure.tenant.TenantContextFilter;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(classes = GeoAnalyticsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthJwtIntegrationTest extends PostgresSuperuserTestBase {

    private static final String JWT_SECRET =
            "9f2e7c4a8b1d6e3f0a5c9b2d7e1f4a8c0b3d6e9f2a5c8b1d4e7f0a3c6b9d2e5f8a1c4b7d0e3f6a9c2b5d8e1f4a7c0b3d6e9f2a5c8b1d4e7f0a3c6b9d2e5f8a1c4b7d0e3f6a9c2b5d8e1f4a7c0b3d6e9f2a5c8";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GeoCompetitorSearchAdapter geoCompetitorSearchAdapter;

    @MockitoBean
    private SyncVerificationService syncVerificationService;

    @MockitoBean
    private AsyncBatchService asyncBatchService;

    @MockitoBean
    private GeminiBatchClient geminiBatchClient;

    @Test
    void accessTokenAllowsAuthenticatedBatchEndpoint() throws Exception {
        String access = loginAccessToken();
        webTestClient
                .get()
                .uri("/api/test/batch/run")
                .header(TenantContextFilter.TENANT_HEADER, DefaultTenantIds.WORKSPACE_ID.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void accessTokenRejectedAfterSessionPhysicallyDeleted() throws Exception {
        String access = loginAccessToken();
        Claims claims = parseClaims(access);
        UUID sid = UUID.fromString(claims.get("sid", String.class));
        jdbcTemplate.update("DELETE FROM user_sessions WHERE session_id = ?", sid);
        webTestClient
                .get()
                .uri("/api/test/batch/run")
                .header(TenantContextFilter.TENANT_HEADER, DefaultTenantIds.WORKSPACE_ID.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void expiredAccessTokenReturnsUnauthorized() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        String access = loginAccessToken();
        Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(access).getPayload();
        String expired = Jwts.builder()
                .subject(c.getSubject())
                .claim("role", c.get("role", String.class))
                .claim("sid", c.get("sid", String.class))
                .claim("org", c.get("org", String.class))
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(3600)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        webTestClient
                .get()
                .uri("/api/test/batch/run")
                .header(TenantContextFilter.TENANT_HEADER, DefaultTenantIds.WORKSPACE_ID.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired)
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    private String loginAccessToken() throws Exception {
        String body = webTestClient
                .post()
                .uri("/api/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("email", "bootstrap@example.com", "password", "bootstrap"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private Claims parseClaims(String access) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(access).getPayload();
    }
}
