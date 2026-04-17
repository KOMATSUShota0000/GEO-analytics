package com.geo.analytics.infrastructure.security;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.geo.analytics.GeoAnalyticsApplication;
import com.geo.analytics.application.event.UserSessionEvictedEvent;
import com.geo.analytics.application.service.SessionManagementService;
import com.geo.analytics.application.service.SyncVerificationService;
import com.geo.analytics.infrastructure.api.SerpApiAdapter;
import com.geo.analytics.domain.entity.OrganizationUser;
import com.geo.analytics.domain.entity.UserSession;
import com.geo.analytics.domain.enums.OrganizationUserRole;
import com.geo.analytics.infrastructure.repository.OrganizationUserRepository;
import com.geo.analytics.infrastructure.repository.UserSessionRepository;
import com.geo.analytics.infrastructure.repository.WorkspaceRepository;
import com.geo.analytics.infrastructure.tenant.OrgTenantKey;
import com.geo.analytics.infrastructure.tenant.TenantContext;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.lang.ScopedValue;
import java.time.Duration;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
        classes = GeoAnalyticsApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.task.scheduling.enabled=false")
@ActiveProfiles("test")
class SecurityConcurrencyTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID TENANT_ID = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID SESSION_INITIAL = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
    private static final String SEED_EMAIL = "security-concurrency@example.invalid";

    private static final FilterChain OK_CHAIN = (req, res) -> {};

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private SessionManagementService sessionManagementService;

    @Autowired
    private OrganizationUserRepository organizationUserRepository;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @MockitoBean
    private WorkspaceRepository workspaceRepository;

    @Autowired
    @Qualifier("userSessionsCache")
    private Cache<UUID, UUID> userSessionsCache;

    @Autowired
    @Qualifier("orgTenantAffiliationCache")
    private Cache<OrgTenantKey, Boolean> orgTenantAffiliationCache;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    private TenantAccessEvaluator tenantAccessEvaluator;

    @Autowired
    private PlatformTransactionManager platformTransactionManager;

    @MockitoBean
    private SerpApiAdapter serpApiAdapter;

    @MockitoBean
    private SyncVerificationService syncVerificationService;

    private TransactionTemplate transactionTemplate;

    private UUID userId;
    private OrganizationUser organizationUser;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(platformTransactionManager);
        SecurityContextHolder.clearContext();
        userSessionsCache.invalidateAll();
        orgTenantAffiliationCache.invalidateAll();

        transactionTemplate.executeWithoutResult(status -> organizationUserRepository
                .findByEmailAndDeletedAtIsNull(SEED_EMAIL)
                .ifPresent(u -> {
                    userSessionRepository.deleteAllByUserId(u.getId());
                    organizationUserRepository.delete(u);
                }));

        when(workspaceRepository.existsByIdAndOrganizationId(eq(TENANT_ID), eq(ORG_ID))).thenReturn(true);

        organizationUser = new OrganizationUser();
        organizationUser.setOrganizationId(ORG_ID);
        organizationUser.setEmail(SEED_EMAIL);
        organizationUser.setPasswordHash("{noop}x");
        organizationUser.setRole(OrganizationUserRole.ADMIN);
        organizationUser = organizationUserRepository.save(organizationUser);
        userId = organizationUser.getId();

        UserSession session = new UserSession();
        session.setOrganizationId(ORG_ID);
        session.setUserId(userId);
        session.setSessionId(SESSION_INITIAL);
        session.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        userSessionRepository.save(session);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void concurrentJwtValidationAfterSessionRotationDoesNotResurrectStaleSessionInCache()
            throws Exception {
        String initialJwt = tokenService.generateAccessToken(organizationUser, SESSION_INITIAL);
        assertThat(runFilterWithBearer(initialJwt)).isEqualTo(200);
        assertThat(userSessionsCache.getIfPresent(userId)).isEqualTo(SESSION_INITIAL);

        UUID newSessionId =
                transactionTemplate.execute(status -> sessionManagementService.createNewSession(userId));
        assertThat(newSessionId).isNotNull().isNotEqualTo(SESSION_INITIAL);

        await().atMost(15, SECONDS)
                .pollInterval(Duration.ofMillis(50))
                .until(() -> {
                    UUID cached = userSessionsCache.getIfPresent(userId);
                    return cached == null || cached.equals(newSessionId);
                });

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 400; i++) {
                futures.add(executor.submit(() -> runFilterWithBearer(initialJwt)));
            }
            for (Future<Integer> f : futures) {
                assertThat(f.get()).isEqualTo(401);
            }
        }

        assertThat(userSessionsCache.getIfPresent(userId))
                .isNotEqualTo(SESSION_INITIAL);

        String newJwt = tokenService.generateAccessToken(organizationUser, newSessionId);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                futures.add(executor.submit(() -> runFilterWithBearer(newJwt)));
            }
            for (Future<Integer> f : futures) {
                assertThat(f.get()).isEqualTo(200);
            }
        }

        assertThat(userSessionsCache.getIfPresent(userId)).isEqualTo(newSessionId);
    }

    @Test
    void userSessionEvictedEventRemovesOrgTenantKeysUnderConcurrentReads() {
        OrgTenantKey k1 = new OrgTenantKey(ORG_ID, TENANT_ID);
        OrgTenantKey k2 = new OrgTenantKey(ORG_ID, UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd"));
        orgTenantAffiliationCache.put(k1, true);
        orgTenantAffiliationCache.put(k2, true);

        AtomicInteger readCount = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

        Thread stress =
                new Thread(
                        () -> {
                            while (!Thread.currentThread().isInterrupted()) {
                                try {
                                    orgTenantAffiliationCache.getIfPresent(k1);
                                    orgTenantAffiliationCache.getIfPresent(k2);
                                    readCount.incrementAndGet();
                                } catch (Throwable t) {
                                    errors.add(t);
                                    return;
                                }
                            }
                        },
                        "org-cache-stress");
        stress.setDaemon(true);
        stress.start();

        try {
            transactionTemplate.executeWithoutResult(
                    status -> applicationEventPublisher.publishEvent(new UserSessionEvictedEvent(userId, ORG_ID)));

            await().atMost(15, SECONDS)
                    .pollInterval(Duration.ofMillis(25))
                    .untilAsserted(() -> assertThat(orgTenantAffiliationCache.asMap().keySet().stream()
                                    .noneMatch(k -> k.orgId().equals(ORG_ID)))
                            .isTrue());

            assertThat(errors).isEmpty();
            assertThat(readCount.get()).isPositive();
        } finally {
            stress.interrupt();
        }
    }

    @Test
    void tenantContextHolderDoesNotLeakAcrossVirtualThreads() throws Exception {
        int n = 500;
        ConcurrentLinkedQueue<String> violations = new ConcurrentLinkedQueue<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                final UUID expectedOrg = UUID.randomUUID();
                futures.add(
                        executor.submit(
                                () ->
                                        ScopedValue.where(
                                                        TenantContextHolder.CONTEXT,
                                                        new TenantContext(expectedOrg, null, null))
                                                .run(
                                                        () -> {
                                                            if (TenantContextHolder.getOrganizationId().isEmpty()
                                                                    || !expectedOrg.equals(
                                                                            TenantContextHolder
                                                                                    .getOrganizationId()
                                                                                    .get())) {
                                                                violations.add("mismatch");
                                                            }
                                                            Thread.yield();
                                                            if (TenantContextHolder.getOrganizationId().isEmpty()
                                                                    || !expectedOrg.equals(
                                                                            TenantContextHolder
                                                                                    .getOrganizationId()
                                                                                    .get())) {
                                                                violations.add("mismatch-after-yield");
                                                            }
                                                        })));
            }
            for (Future<?> f : futures) {
                f.get();
            }
        }
        assertThat(TenantContextHolder.getOrganizationId()).isEmpty();
        assertThat(violations).isEmpty();
    }

    @Test
    void tenantAccessEvaluatorCacheCooperatesWithOrgContextUnderVirtualThreads() throws Exception {
        var auth =
                new UsernamePasswordAuthenticationToken(
                        userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        int n = 200;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                futures.add(
                        executor.submit(
                                () ->
                                        ScopedValue.where(
                                                        TenantContextHolder.CONTEXT,
                                                        new TenantContext(ORG_ID, null, null))
                                                .call(
                                                        () ->
                                                                tenantAccessEvaluator.canAccessTenant(
                                                                        auth, TENANT_ID))));
            }
            for (Future<Boolean> f : futures) {
                assertThat(f.get()).isTrue();
            }
        }
        assertThat(TenantContextHolder.getOrganizationId()).isEmpty();
        assertThat(orgTenantAffiliationCache.getIfPresent(new OrgTenantKey(ORG_ID, TENANT_ID))).isTrue();
    }

    private int runFilterWithBearer(String jwt) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/api/security-concurrency/probe");
        request.setServletPath("/api/security-concurrency/probe");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwt);
        MockHttpServletResponse response = new MockHttpServletResponse();
        jwtAuthenticationFilter.doFilter(request, response, OK_CHAIN);
        return response.getStatus();
    }
}
