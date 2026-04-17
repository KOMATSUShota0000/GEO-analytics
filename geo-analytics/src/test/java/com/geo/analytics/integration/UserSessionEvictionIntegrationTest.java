package com.geo.analytics.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.geo.analytics.GeoAnalyticsApplication;
import com.geo.analytics.application.service.SessionManagementService;
import com.geo.analytics.application.service.SyncVerificationService;
import com.geo.analytics.infrastructure.api.SerpApiAdapter;
import com.geo.analytics.infrastructure.tenant.TenantContext;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import com.github.benmanes.caffeine.cache.Cache;
import java.lang.ScopedValue;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = GeoAnalyticsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("rls-it")
class UserSessionEvictionIntegrationTest extends PostgresTestBase {

    private static final UUID ORG_A = UUID.fromString("11111111-1111-1111-1111-111111111101");
    private static final UUID USER_A_ADMIN = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa01");

    @Autowired
    private SessionManagementService sessionManagementService;

    @Autowired
    @Qualifier("userSessionsCache")
    private Cache<UUID, Boolean> userSessionsCache;

    @MockitoBean
    private SerpApiAdapter serpApiAdapter;

    @MockitoBean
    private SyncVerificationService syncVerificationService;

    @Test
    @Transactional
    void afterCommitAsyncListenerEvictsUserSessionsCache() {
        ScopedValue.where(TenantContextHolder.CONTEXT, new TenantContext(ORG_A, null, null))
                .run(
                        () -> {
                            UUID firstSessionId = sessionManagementService.createNewSession(USER_A_ADMIN);
                            userSessionsCache.put(firstSessionId, Boolean.TRUE);

                            sessionManagementService.createNewSession(USER_A_ADMIN);

                            TestTransaction.flagForCommit();
                            TestTransaction.end();

                            await().atMost(Duration.ofSeconds(10))
                                    .pollInterval(Duration.ofMillis(50))
                                    .untilAsserted(
                                            () -> assertThat(userSessionsCache.getIfPresent(firstSessionId))
                                                    .isNull());
                        });
    }
}
