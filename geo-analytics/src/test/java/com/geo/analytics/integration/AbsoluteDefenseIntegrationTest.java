package com.geo.analytics.integration;

import com.geo.analytics.GeoAnalyticsApplication;
import com.geo.analytics.application.dto.CompetitorResult;
import com.geo.analytics.application.dto.SyncVerificationResult;
import com.geo.analytics.application.dto.VerificationRequest;
import com.geo.analytics.application.dto.VerificationResponse;
import com.geo.analytics.application.service.PlanBasedQuotaManager;
import com.geo.analytics.application.service.SyncVerificationService;
import com.geo.analytics.domain.enums.MatchStatus;
import com.geo.analytics.domain.enums.ModelType;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.service.InformationTheoryBasedAggregator;
import com.geo.analytics.infrastructure.config.Bucket4jConfiguration;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import io.github.bucket4j.caffeine.CaffeineProxyManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = GeoAnalyticsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AbsoluteDefenseIntegrationTest {
    private static final UUID WID = DefaultTenantIds.WORKSPACE_ID;
    private static final String TENANT_HEADER = "X-Tenant-ID";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlanBasedQuotaManager planBasedQuotaManager;

    @Autowired
    @Qualifier(Bucket4jConfiguration.PLAN_QUOTA_CAFFEINE_PROXY_MANAGER)
    private CaffeineProxyManager<String> planQuotaCaffeineProxyManager;

    @Autowired
    private InformationTheoryBasedAggregator informationTheoryBasedAggregator;

    @MockBean
    private SyncVerificationService syncVerificationService;

    @AfterEach
    void tearDownDefenseState() {
        reset(syncVerificationService);
        purgeEnversTables();
        jdbcTemplate.update("DELETE FROM job_competitor_scores");
        jdbcTemplate.update("DELETE FROM audit_histories");
        jdbcTemplate.update("DELETE FROM job_queries");
        jdbcTemplate.update("DELETE FROM sge_results");
        jdbcTemplate.update("DELETE FROM jobs");
        jdbcTemplate.update("UPDATE workspaces SET subscription_plan = 'STANDARD' WHERE id = ?", WID);
        planQuotaCaffeineProxyManager.getCache().invalidateAll();
    }

    @Test
    void scenarioA_quotaBlocksEleventhKeywordOnStandard() {
        var eleven = IntStream.rangeClosed(1, 11).mapToObj(i -> "k" + i).toList();
        var jobId = createJob("DefenseBrandA");
        webTestClient
                .post()
                .uri("/api/v1/jobs/{jobId}/queries", jobId)
                .header(TENANT_HEADER, WID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("queries", eleven, "plan", "STANDARD"))
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody()
                .jsonPath("$.error_code")
                .isEqualTo("INSUFFICIENT_QUOTA");
    }

    @Test
    void scenarioB_rateLimitReturns429WithRecoveryDurationInMessage() {
        jdbcTemplate.update("UPDATE workspaces SET subscription_plan = 'PRO' WHERE id = ?", WID);
        planQuotaCaffeineProxyManager.getCache().invalidateAll();
        for (var i = 0; i < 100; i++) {
            var probe = planBasedQuotaManager.resolve(WID).tryConsumeAndReturnRemaining(1);
            assertThat(probe.isConsumed()).as("exhaust slot %d", i).isTrue();
        }
        var jobId = createJob("DefenseBrandB");
        webTestClient
                .post()
                .uri("/api/v1/jobs/{jobId}/queries", jobId)
                .header(TENANT_HEADER, WID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("queries", List.of("q1"), "plan", "PRO"))
                .exchange()
                .expectStatus()
                .isEqualTo(429)
                .expectBody()
                .jsonPath("$.error_code")
                .isEqualTo("RATE_LIMIT_EXCEEDED")
                .jsonPath("$.message")
                .value(m -> {
                    var msg = (String) m;
                    assertThat(msg).contains("時間");
                    assertThat(msg).contains("分");
                });
    }

    @Test
    void scenarioC_planUpgradeRebuildsBucketWithoutRestart() {
        jdbcTemplate.update("UPDATE workspaces SET subscription_plan = 'PRO' WHERE id = ?", WID);
        planQuotaCaffeineProxyManager.getCache().invalidateAll();
        for (var i = 0; i < 100; i++) {
            assertThat(planBasedQuotaManager.resolve(WID).tryConsumeAndReturnRemaining(1).isConsumed()).isTrue();
        }
        webTestClient
                .patch()
                .uri("/api/v1/workspaces/{workspaceId}/subscription", WID)
                .header(TENANT_HEADER, WID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("plan", "EXPERT"))
                .exchange()
                .expectStatus()
                .isNoContent();
        when(syncVerificationService.verify(
                        anyString(),
                        anyString(),
                        any(SubscriptionPlan.class),
                        any(UUID.class),
                        any(UUID.class),
                        anyString(),
                        anyList()))
                .thenReturn(
                        new SyncVerificationResult(
                                "{}",
                                50.0,
                                true,
                                1,
                                80,
                                100,
                                1,
                                0.5,
                                "",
                                "Brand",
                                1,
                                1.0,
                                "stub",
                                List.of(),
                                "{}"));
        var jobId = createJob("DefenseBrandC");
        webTestClient
                .post()
                .uri("/api/v1/jobs/{jobId}/queries", jobId)
                .header(TENANT_HEADER, WID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("queries", List.of("q1"), "plan", "EXPERT"))
                .exchange()
                .expectStatus()
                .isNoContent();
    }

    @Test
    void scenarioD_entityResolutionAutoMatchAndLowScoreExclusion() {
        var insights = new LinkedHashMap<ModelType, String>();
        insights.put(ModelType.GEMINI, "{}");
        var applePick = new CompetitorResult("Apple", 88.0, 1, 2, MatchStatus.NO_MATCH);
        var noisePick = new CompetitorResult("ZyzzyvaTotallyUnrelatedNoise", 99.0, 1, 2, MatchStatus.NO_MATCH);
        var modelResponse = new VerificationResponse(
                ModelType.GEMINI,
                "{}",
                90.0,
                true,
                1,
                80,
                100,
                1,
                0.5,
                "Apple",
                2,
                1.0,
                "pre",
                List.of(applePick, noisePick),
                insights);
        var request = new VerificationRequest(
                "B",
                "Q",
                null,
                null,
                null,
                SubscriptionPlan.EXPERT,
                null,
                null,
                null,
                List.of("AppleInc."),
                null);
        var aggregated = informationTheoryBasedAggregator.aggregate(List.of(modelResponse), request);
        assertThat(aggregated.calculationVersion()).isEqualTo("SUBSCRIPTION_INTEGRATION_V4.13");
        var row = aggregated.competitorResults().stream()
                .filter(r -> "AppleInc.".equals(r.competitorLabel()))
                .findFirst()
                .orElseThrow();
        assertThat(row.matchStatus()).isEqualTo(MatchStatus.AUTO_MATCH);
        assertThat(row.somScore()).isCloseTo(88.0, org.assertj.core.data.Offset.offset(0.05));
    }

    private UUID createJob(String brandName) {
        @SuppressWarnings("unchecked")
        var body = webTestClient
                .post()
                .uri("/api/v1/jobs")
                .header(TENANT_HEADER, WID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("brandName", brandName))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        assertThat(body).isNotNull();
        var raw = body.get("jobId");
        return raw instanceof UUID u ? u : UUID.fromString(String.valueOf(raw));
    }

    private void purgeEnversTables() {
        var audTables = jdbcTemplate.query(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME LIKE ? ESCAPE '\\'",
                (rs, rowNum) -> rs.getString(1),
                "%\\_AUD");
        for (var t : audTables) {
            jdbcTemplate.execute("DELETE FROM " + t);
        }
        jdbcTemplate.execute("DELETE FROM revinfo");
    }
}
