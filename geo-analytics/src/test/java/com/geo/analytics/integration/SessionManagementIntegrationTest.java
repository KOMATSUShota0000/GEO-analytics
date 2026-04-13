package com.geo.analytics.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.GeoAnalyticsApplication;
import com.geo.analytics.application.service.SessionManagementService;
import com.geo.analytics.application.service.SyncVerificationService;
import com.geo.analytics.infrastructure.api.SerpApiAdapter;
import com.geo.analytics.infrastructure.repository.UserSessionRepository;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(classes = GeoAnalyticsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("rls-it")
class SessionManagementIntegrationTest extends PostgresTestBase {

    private static final UUID ORG_A = UUID.fromString("11111111-1111-1111-1111-111111111101");
    private static final UUID USER_A_ADMIN = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa01");

    @Autowired
    private SessionManagementService sessionManagementService;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @MockitoBean
    private SerpApiAdapter serpApiAdapter;

    @MockitoBean
    private SyncVerificationService syncVerificationService;

    @AfterEach
    void tearDownTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void whenCreateNewSessionTwice_thenOnlyLatestSessionRemains() {
        TenantContextHolder.set(ORG_A, null);
        UUID first = sessionManagementService.createNewSession(USER_A_ADMIN);
        UUID second = sessionManagementService.createNewSession(USER_A_ADMIN);
        assertThat(userSessionRepository.countByUserId(USER_A_ADMIN)).isEqualTo(1);
        assertThat(userSessionRepository.findBySessionId(first)).isEmpty();
        assertThat(userSessionRepository.findBySessionId(second)).isPresent();
        assertThat(userSessionRepository.findBySessionId(second).orElseThrow().getSessionId()).isEqualTo(second);
    }
}
