package com.geo.analytics.infrastructure.security;

import com.geo.analytics.infrastructure.repository.UserSessionRepository;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
                    PATHS.matcher("/webauthn/**"),
                    PATHS.matcher("/login/webauthn/**"),
                    PATHS.matcher("/login"),
                    PATHS.matcher("/error"),
                    PATHS.matcher("/ws/**"));

    private final TokenService tokenService;
    private final UserSessionRepository userSessionRepository;
    private final Cache<UUID, UUID> userSessionsCache;

    public JwtAuthenticationFilter(
            TokenService tokenService,
            UserSessionRepository userSessionRepository,
            @Qualifier("userSessionsCache") Cache<UUID, UUID> userSessionsCache) {
        this.tokenService = tokenService;
        this.userSessionRepository = userSessionRepository;
        this.userSessionsCache = userSessionsCache;
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
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        try {
            TenantContextHolder.set(parsed.organizationId(), null);

            UUID cachedSessionId = userSessionsCache.getIfPresent(parsed.userId());
            if (cachedSessionId != null) {
                if (!cachedSessionId.equals(parsed.sessionId())) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
            } else {
                if (userSessionRepository
                        .findBySessionId(parsed.sessionId())
                        .filter(s -> s.getDeletedAt() == null)
                        .filter(s -> s.getUserId().equals(parsed.userId()))
                        .isEmpty()) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
                userSessionsCache.put(parsed.userId(), parsed.sessionId());
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            parsed.userId().toString(),
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + parsed.role())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }
}
