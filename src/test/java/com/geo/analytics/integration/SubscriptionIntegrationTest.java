package com.geo.analytics.integration;

import com.geo.analytics.GeoAnalyticsApplication;
import com.geo.analytics.domain.model.CompetitorResult;
import com.geo.analytics.application.dto.SgeMentionResult;
import com.geo.analytics.application.dto.SyncVerificationResult;
import com.geo.analytics.application.dto.VerificationRequest;
import com.geo.analytics.application.dto.VerificationResponse;
import com.geo.analytics.application.port.ModelTypedAiVerificationPort;
import com.geo.analytics.application.service.AiVerificationRouter;
import com.geo.analytics.application.service.AiRubricAuditService;
import com.geo.analytics.application.service.JobBenchmarkCaptureService;
import com.geo.analytics.application.service.PlanBasedQuotaManager;
import com.geo.analytics.application.service.ProjectAuditLifecyclePublisher;
import com.geo.analytics.application.service.SubscriptionManagementService;
import com.geo.analytics.application.service.SyncVerificationService;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.enums.ModelType;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.model.QuotaCreditCalculator;
import com.geo.analytics.infrastructure.api.GeoCompetitorSearchAdapter;
import com.geo.analytics.infrastructure.config.Bucket4jConfiguration;
import com.geo.analytics.infrastructure.repository.AuditHistoryRepository;
import com.geo.analytics.infrastructure.repository.JobRepository;
import com.geo.analytics.infrastructure.repository.ProjectKeywordRepository;
import com.geo.analytics.infrastructure.repository.ProjectRepository;
import com.geo.analytics.infrastructure.repository.QueryRepository;
import com.geo.analytics.infrastructure.repository.SgeResultRepository;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import com.geo.analytics.web.dto.JobStatusResponse;
import io.github.bucket4j.caffeine.CaffeineProxyManager;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = GeoAnalyticsApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.task.scheduling.enabled=false")
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.MethodName.class)
class SubscriptionIntegrationTest extends PostgresSuperuserTestBase {
    private static final UUID WID = DefaultTenantIds.WORKSPACE_ID;
    private static final String TENANT_HEADER = "X-Tenant-ID";
    private static final Pattern RECOVERY_PATTERN = Pattern.compile(".*回復まで約\\d+時間\\d+分.*");

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlanBasedQuotaManager planBasedQuotaManager;

    @Autowired
    private SubscriptionManagementService subscriptionManagementService;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private QueryRepository queryRepository;

    @Autowired
    private AuditHistoryRepository auditHistoryRepository;

    @Autowired
    private SgeResultRepository sgeResultRepository;

    @Autowired
    private ProjectKeywordRepository projectKeywordRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    @Qualifier(Bucket4jConfiguration.PLAN_QUOTA_CAFFEINE_PROXY_MANAGER)
    private CaffeineProxyManager<String> planQuotaCaffeineProxyManager;

    @Autowired
    @Qualifier("rateLimitProxyManager")
    private ProxyManager<String> rateLimitProxyManager;

    @MockitoBean
    private SyncVerificationService syncVerificationService;

    @MockitoBean
    private GeoCompetitorSearchAdapter geoCompetitorSearchAdapter;

    @MockitoBean
    private JobBenchmarkCaptureService jobBenchmarkCaptureService;

    @MockitoBean
    private AiRubricAuditService aiRubricAuditService;

    @MockitoBean
    private ProjectAuditLifecyclePublisher projectAuditLifecyclePublisher;

    @BeforeEach
    void stubSync() {
        invalidateRateLimitCaches();
        planQuotaCaffeineProxyManager.getCache().invalidateAll();
        webTestClient = webTestClient.mutate().responseTimeout(Duration.ofSeconds(120)).build();
        lenient().doNothing().when(jobBenchmarkCaptureService).capture(any(UUID.class));
        lenient().doNothing().when(jobBenchmarkCaptureService).capture(any(UUID.class), any());
        // Why: 監査は自社分の成果物を返すようになった（#72）。テストでは使い回す材料が無い null を返す。
        lenient()
                .when(aiRubricAuditService.runMultiDomainAuditForCompletedJob(any(UUID.class)))
                .thenReturn(null);
        lenient().doNothing().when(projectAuditLifecyclePublisher).publishAuditCompleted(any(JobEntity.class));
        lenient()
                .when(geoCompetitorSearchAdapter.checkSgeMention(anyString(), anyString()))
                .thenReturn(new SgeMentionResult(false, 0, "{}", ""));
        // Why: target_url を持つジョブの実処理は verifyWithAiOverview を通る（#92 で材料を実測 AI Overview へ
        //      切り替えた）。ここを未スタブにすると mock が null を返して非同期処理が例外になり、catch 節の
        //      addTokens によるクォータ返却が他テストのバケットへ不定のタイミングで流れ込む。レート制限系の
        //      検証が状態依存になる原因だったため、スタブして副作用を断つ。
        lenient()
                .when(syncVerificationService.verifyWithAiOverview(
                        anyString(),
                        anyString(),
                        nullable(String.class),
                        nullable(String.class),
                        any(SubscriptionPlan.class),
                        any(UUID.class),
                        any(UUID.class),
                        anyString()))
                .thenReturn(stubbedVerificationResult());
        when(syncVerificationService.verify(
                anyString(),
                anyString(),
                any(SubscriptionPlan.class),
                any(UUID.class),
                any(UUID.class),
                anyString())).thenReturn(stubbedVerificationResult());
    }

    private static SyncVerificationResult stubbedVerificationResult() {
        return new SyncVerificationResult(
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
                6,
                0.0,
                "V11_GEO_PURE",
                "{}",
                50.0,
                0,
                List.of());
    }

    @AfterEach
    void tearDownState() {
        reset(
                syncVerificationService,
                geoCompetitorSearchAdapter,
                jobBenchmarkCaptureService,
                aiRubricAuditService,
                projectAuditLifecyclePublisher);
        retryOnPgDeadlock(
                () -> {
                    auditHistoryRepository.deleteAllInBatch();
                    queryRepository.deleteAllInBatch();
                    sgeResultRepository.deleteAllInBatch();
                    jobRepository.deleteAllInBatch();
                    projectKeywordRepository.deleteAllInBatch();
                    projectRepository.deleteAllInBatch();
                    jdbcTemplate.update("UPDATE workspaces SET subscription_plan='STANDARD' WHERE id=?", WID);
                });
        invalidateRateLimitCaches();
        planQuotaCaffeineProxyManager.getCache().invalidateAll();
    }

    private static void retryOnPgDeadlock(Runnable runnable) {
        for (int attempt = 1; attempt <= 20; attempt++) {
            try {
                runnable.run();
                return;
            } catch (DataAccessException exception) {
                if (attempt >= 20 || !isPostgresDeadlock(exception)) {
                    throw exception;
                }
                try {
                    Thread.sleep(50L * attempt);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interruptedException);
                }
            }
        }
    }

    private static boolean isPostgresDeadlock(Throwable throwable) {
        for (Throwable c = throwable; c != null; c = c.getCause()) {
            String m = c.getMessage();
            if (m != null && m.contains("deadlock detected")) {
                return true;
            }
        }
        return false;
    }

    private void invalidateRateLimitCaches() {
        if (rateLimitProxyManager instanceof CaffeineProxyManager<String> cpm) {
            cpm.getCache().invalidateAll();
        }
    }

    @Test
    void scenarioA_quotaBlocksEleventhOnStandard() {
        var jobId = createJob("SubscriptionA");
        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(50))
                .untilAsserted(
                        () -> {
                            String status = jdbcTemplate.queryForObject(
                                    "SELECT job_status FROM jobs WHERE id = ?", String.class, jobId);
                            String err = jdbcTemplate.queryForObject(
                                    "SELECT error_message FROM jobs WHERE id = ?", String.class, jobId);
                            long qc =
                                    jdbcTemplate.queryForObject(
                                            "SELECT COUNT(*) FROM job_queries WHERE job_id = ?",
                                            Long.class,
                                            jobId);
                            assertThat(qc)
                                    .withFailMessage(
                                            "expected >=1 queries; status=%s err=%s", status, err)
                                    .isGreaterThanOrEqualTo(1);
                        });
        var nineMore = IntStream.rangeClosed(1, 9).mapToObj(i -> "k" + i).toList();
        webTestClient.post()
                .uri("/api/v1/jobs/{jobId}/queries", jobId)
                .header(TENANT_HEADER, WID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("queries", nineMore, "plan", "STANDARD"))
                .exchange()
                .expectStatus().isNoContent();
        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(50))
                .untilAsserted(
                        () -> {
                            String status = jdbcTemplate.queryForObject(
                                    "SELECT job_status FROM jobs WHERE id = ?", String.class, jobId);
                            long qc =
                                    jdbcTemplate.queryForObject(
                                            "SELECT COUNT(*) FROM job_queries WHERE job_id = ?",
                                            Long.class,
                                            jobId);
                            assertThat(qc)
                                    .withFailMessage("expected >=10 queries; status=%s", status)
                                    .isGreaterThanOrEqualTo(10);
                        });
        webTestClient.post()
                .uri("/api/v1/jobs/{jobId}/queries", jobId)
                .header(TENANT_HEADER, WID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("queries", List.of("k11"), "plan", "STANDARD"))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.error_code").isEqualTo("insufficient_quota")
                .jsonPath("$.details.current_limit").isEqualTo(10)
                .jsonPath("$.details.plan_name").isEqualTo("STANDARD");
    }

    @Test
    void scenarioB_rateLimit429ContainsRecoveryTime() {
        jdbcTemplate.update("UPDATE workspaces SET subscription_plan='PRO' WHERE id=?", WID);
        planQuotaCaffeineProxyManager.getCache().invalidateAll();
        var limit = SubscriptionPlan.PRO.getDailyLimit();
        var jobId = createJob("SubscriptionB");
        // Why: 「容量 - 1キーワード分」を先に消費する旧実装は、残量が同一クラス内の先行テストの
        //      消費量に依存し、単独実行では 429 にならなかった。検証したいのは「バケット枯渇時に
        //      429 と復旧時刻を返すこと」なので、ジョブ作成を終えてから残量を 0 にして決定的にする。
        //
        //      さらに、先行テストが起動した非同期処理が失敗して枠を払い戻すと、空にした直後に残量が
        //      復活して 204 が返っていた（全体実行のときだけ落ちる原因。#108）。処理中のジョブが
        //      無くなるのを待ってから空にする。
        awaitNoJobsInFlight();
        planBasedQuotaManager.resolve(WID).tryConsumeAsMuchAsPossible();
        webTestClient.post()
                .uri("/api/v1/jobs/{jobId}/queries", jobId)
                .header(TENANT_HEADER, WID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("queries", List.of("one"), "plan", "PRO"))
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectBody()
                .jsonPath("$.error_code").isEqualTo("rate_limit_exceeded")
                .jsonPath("$.message").value(m -> assertThat((String) m).matches(RECOVERY_PATTERN))
                .jsonPath("$.details.current_limit").isEqualTo(limit)
                .jsonPath("$.details.plan_name").isEqualTo("PRO");
    }

    /** 非同期処理が枠を払い戻し切るまで待つ。払い戻しはジョブが終端状態になった後には発生しない。 */
    private void awaitNoJobsInFlight() {
        for (int attempt = 0; attempt < 300; attempt++) {
            Integer inFlight = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM jobs WHERE job_status IN ('REALTIME_PROCESSING','RUNNING','SUBMITTED')",
                    Integer.class);
            if (inFlight != null && inFlight == 0) {
                return;
            }
            try {
                Thread.sleep(100L);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Test
    void scenarioC_upgradeReflectsImmediatelyWithoutRestart() {
        // Why: 先行テストの非同期処理が枠を消費・払い戻しする間にバケツを作り直すと、満量を前提にした
        //      この検証が揺れる（#108 と同じ原因）。処理中のジョブが無くなってから作り直す。
        awaitNoJobsInFlight();
        jdbcTemplate.update("UPDATE workspaces SET subscription_plan='PRO' WHERE id=?", WID);
        planQuotaCaffeineProxyManager.getCache().invalidateAll();
        var limit = SubscriptionPlan.PRO.getDailyLimit();
        assertThat(planBasedQuotaManager.resolve(WID).tryConsumeAndReturnRemaining(
                (long) limit * QuotaCreditCalculator.DEPOSIT_PER_KEYWORD).isConsumed()).isTrue();
        subscriptionManagementService.changePlan(WID, SubscriptionPlan.EXPERT);
        var jobId = createJob("SubscriptionC");
        webTestClient.post()
                .uri("/api/v1/jobs/{jobId}/queries", jobId)
                .header(TENANT_HEADER, WID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("queries", List.of("q1"), "plan", "EXPERT"))
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void scenarioE_virtualThreadParallelHealthAndSemaphoreBound() {
        var active = new AtomicInteger(0);
        var maxActive = new AtomicInteger(0);
        record Adapter(ModelType modelType, AtomicInteger active, AtomicInteger maxActive) implements ModelTypedAiVerificationPort {
            @Override
            public VerificationResponse verify(VerificationRequest verificationRequest) {
                var now = active.incrementAndGet();
                maxActive.accumulateAndGet(now, Integer::max);
                try {
                    Thread.sleep(40L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                } finally {
                    active.decrementAndGet();
                }
                var insights = new LinkedHashMap<ModelType, String>();
                insights.put(modelType, "{}");
                return new VerificationResponse(
                        modelType,
                        "{}",
                        50.0,
                        true,
                        1,
                        80,
                        100,
                        1,
                        1.0,
                        "Brand",
                        6,
                        0.0,
                        "V11_GEO_PURE",
                        List.of(),
                        insights,
                        50.0);
            }
        }
        var router = new AiVerificationRouter(new Adapter(ModelType.GEMINI, active, maxActive), new Semaphore(2));
        var req = new VerificationRequest(
                "Brand",
                "Q",
                null,
                SubscriptionPlan.EXPERT,
                null,
                null,
                null,
                null);
        try (StructuredTaskScope<Void, Void> scope = StructuredTaskScope.open(
                StructuredTaskScope.Joiner.<Void>awaitAll(),
                cf -> cf.withTimeout(Duration.ofSeconds(10))
                        .withThreadFactory(Thread.ofVirtual().name("subscription-parallel-", 0).factory()))) {
            for (var i = 0; i < 12; i++) {
                scope.fork((Runnable) () -> {
                    var res = router.verify(req);
                    assertThat(res).isNotNull();
                });
            }
            try {
                scope.join();
            } catch (StructuredTaskScope.TimeoutException e) {
                fail("parallel verification timeout");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("parallel verification interrupted");
            }
        }
        assertThat(maxActive.get()).isLessThanOrEqualTo(2);
    }

    private UUID createJob(String brandName) {
        String json;
        try {
            json = objectMapper.writeValueAsString(
                    Map.of("brandName", brandName, "targetUrl", "https://example.test/jobs/" + brandName));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        MultipartBodyBuilder multipart = new MultipartBodyBuilder();
        multipart.part("request", json).contentType(MediaType.APPLICATION_JSON);
        var response =
                webTestClient
                        .post()
                        .uri("/api/v1/jobs")
                        .header(TENANT_HEADER, WID.toString())
                        .body(BodyInserters.fromMultipartData(multipart.build()))
                        .exchange()
                        .expectStatus()
                        .isCreated()
                        .expectBody(JobStatusResponse.class)
                        .returnResult()
                        .getResponseBody();
        assertThat(response).isNotNull();
        assertThat(response.jobId()).isNotNull();
        return response.jobId();
    }
}