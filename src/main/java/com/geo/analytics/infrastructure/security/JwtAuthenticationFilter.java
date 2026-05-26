package com.geo.analytics.infrastructure.security;

import com.geo.analytics.application.service.SessionManagementService;
import com.geo.analytics.domain.exception.SessionRevokedException;
import com.geo.analytics.domain.exception.TokenExpiredException;
import com.geo.analytics.domain.exception.UnauthenticatedApiException;
import com.geo.analytics.infrastructure.tenant.TenantIdentity;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ScopedValue;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();
    private static final List<RequestMatcher> SKIP_MATCHERS =
            List.of(
                    PATHS.matcher(HttpMethod.GET, "/api/csrf"),
                    PATHS.matcher(HttpMethod.POST, "/api/login"),
                    PATHS.matcher(HttpMethod.POST, "/api/auth/refresh"),
                    PATHS.matcher(HttpMethod.OPTIONS, "/**"),
                    PATHS.matcher(HttpMethod.GET, "/api/v1/jobs/*/stream"),
                    PATHS.matcher("/api/public/**"),
                    PATHS.matcher("/login"),
                    PATHS.matcher("/error"));

    private final TokenService tokenService;
    private final SessionManagementService sessionManagementService;
    private final Cache<UUID, Boolean> userSessionsCache;
    private final SecurityExceptionResponseHandler securityExceptionResponseHandler;

    public JwtAuthenticationFilter(
            TokenService tokenService,
            SessionManagementService sessionManagementService,
            @Qualifier("userSessionsCache") Cache<UUID, Boolean> userSessionsCache,
            SecurityExceptionResponseHandler securityExceptionResponseHandler) {
        this.tokenService = tokenService;
        this.sessionManagementService = sessionManagementService;
        this.userSessionsCache = userSessionsCache;
        this.securityExceptionResponseHandler = securityExceptionResponseHandler;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SKIP_MATCHERS.stream().anyMatch(m -> m.matches(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = header.substring(7).trim();
        if (token.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        final TokenService.ParsedAccessToken parsed;
        try {
            parsed = tokenService.parseAccessToken(token);
        } catch (JwtTokenException e) {
            securityExceptionResponseHandler.handle(request, response, mapJwtFailure(e));
            return;
        }
        // TenantContextFilter / TenantPlanScope が束ねた workspace を維持する（ScopedValue の内側束縛で消さない）
        UUID mergedWorkspaceId =
                TenantContextHolder.current().map(TenantIdentity::tenantId).orElse(null);
        TenantIdentity mergedContext =
                new TenantIdentity(parsed.organizationId(), mergedWorkspaceId, parsed.userId());
        try {
            ScopedValue.where(TenantContextHolder.CONTEXT, mergedContext)
                    .run(
                            () -> {
                                Boolean cachedValid = userSessionsCache.getIfPresent(parsed.sessionId());
                                if (!Boolean.TRUE.equals(cachedValid)) {
                                    if (sessionManagementService
                                            .findActiveSessionBySessionId(parsed.sessionId())
                                            .filter(s -> s.getUserId().equals(parsed.userId()))
                                            .isEmpty()) {
                                        rejectSessionRevoked(request, response);
                                        return;
                                    }
                                    userSessionsCache.put(parsed.sessionId(), Boolean.TRUE);
                                }

                                UsernamePasswordAuthenticationToken authentication =
                                        new UsernamePasswordAuthenticationToken(
                                                parsed.userId().toString(),
                                                null,
                                                List.of(new SimpleGrantedAuthority("ROLE_" + parsed.role())));
                                SecurityContextHolder.getContext().setAuthentication(authentication);
                                try {
                                    filterChain.doFilter(request, response);
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                } catch (ServletException e) {
                                    throw new IllegalStateException(e);
                                }
                            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        } catch (IllegalStateException e) {
            if (e.getCause() instanceof ServletException se) {
                throw se;
            }
            throw e;
        }
    }

    private static Exception mapJwtFailure(JwtTokenException e) {
        if (e instanceof ExpiredJwtTokenException) {
            return new TokenExpiredException("アクセストークンの有効期限が切れています。", e);
        }
        return new UnauthenticatedApiException("アクセストークンが無効です。", e);
    }

    private void rejectSessionRevoked(HttpServletRequest request, HttpServletResponse response) {
        try {
            securityExceptionResponseHandler.handle(request, response, new SessionRevokedException());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
