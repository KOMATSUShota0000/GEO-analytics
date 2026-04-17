package com.geo.analytics.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geo.analytics.domain.entity.UserSession;
import com.geo.analytics.infrastructure.repository.UserSessionRepository;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ORG_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID SESSION_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final String TOKEN = "signed-jwt";
    private static final TokenService.ParsedAccessToken PARSED =
            new TokenService.ParsedAccessToken(USER_ID, ORG_ID, "USER", SESSION_ID);

    @Mock
    private TokenService tokenService;

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private Cache<UUID, UUID> userSessionsCache;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(tokenService, userSessionRepository, userSessionsCache);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest bearerRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/api/protected/resource");
        request.setServletPath("/api/protected/resource");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);
        return request;
    }

    @Test
    void cacheHitDoesNotQueryDatabase() throws ServletException, IOException {
        when(tokenService.parseAccessToken(TOKEN)).thenReturn(PARSED);
        when(userSessionsCache.getIfPresent(USER_ID)).thenReturn(SESSION_ID);

        MockHttpServletRequest request = bearerRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(userSessionRepository, never()).findBySessionId(any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void cacheMissLoadsFromDatabaseAndPopulatesCache() throws ServletException, IOException {
        when(tokenService.parseAccessToken(TOKEN)).thenReturn(PARSED);
        when(userSessionsCache.getIfPresent(USER_ID)).thenReturn(null);

        UserSession session = new UserSession();
        session.setUserId(USER_ID);
        session.setSessionId(SESSION_ID);
        session.setDeletedAt(null);
        when(userSessionRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(session));

        MockHttpServletRequest request = bearerRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(userSessionRepository).findBySessionId(SESSION_ID);
        verify(userSessionsCache).put(eq(USER_ID), eq(SESSION_ID));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void tenantContextClearedAfterFilterChainThrowsServletException() throws Exception {
        when(tokenService.parseAccessToken(TOKEN)).thenReturn(PARSED);
        when(userSessionsCache.getIfPresent(USER_ID)).thenReturn(SESSION_ID);
        doThrow(new ServletException("boom")).when(filterChain).doFilter(any(), any());

        MockHttpServletRequest request = bearerRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        ServletException thrown =
                assertThrows(ServletException.class, () -> filter.doFilter(request, response, filterChain));
        assertThat(thrown).hasMessageContaining("boom");

        assertThat(TenantContextHolder.getOrganizationId()).isEmpty();
    }

    @Test
    void tenantContextClearedAfterFilterChainThrowsRuntimeException() throws Exception {
        when(tokenService.parseAccessToken(TOKEN)).thenReturn(PARSED);
        when(userSessionsCache.getIfPresent(USER_ID)).thenReturn(SESSION_ID);
        doThrow(new IllegalStateException("boom")).when(filterChain).doFilter(any(), any());

        MockHttpServletRequest request = bearerRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        IllegalStateException thrown =
                assertThrows(IllegalStateException.class, () -> filter.doFilter(request, response, filterChain));
        assertThat(thrown).hasMessageContaining("boom");

        assertThat(TenantContextHolder.getOrganizationId()).isEmpty();
    }
}
