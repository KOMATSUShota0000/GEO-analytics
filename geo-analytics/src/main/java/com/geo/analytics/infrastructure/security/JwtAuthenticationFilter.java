package com.geo.analytics.infrastructure.security;

import com.geo.analytics.infrastructure.repository.UserSessionRepository;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final List<RequestMatcher> SKIP_MATCHERS =
            List.of(
                    AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/csrf"),
                    AntPathRequestMatcher.antMatcher(HttpMethod.POST, "/api/login"),
                    AntPathRequestMatcher.antMatcher(HttpMethod.POST, "/api/auth/refresh"),
                    AntPathRequestMatcher.antMatcher(HttpMethod.OPTIONS, "/**"),
                    new AntPathRequestMatcher("/webauthn/**"),
                    new AntPathRequestMatcher("/login/webauthn/**"),
                    new AntPathRequestMatcher("/login"),
                    new AntPathRequestMatcher("/error"),
                    new AntPathRequestMatcher("/ws/**"));

    private final TokenService tokenService;
    private final UserSessionRepository userSessionRepository;

    public JwtAuthenticationFilter(TokenService tokenService, UserSessionRepository userSessionRepository) {
        this.tokenService = tokenService;
        this.userSessionRepository = userSessionRepository;
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
        TenantContextHolder.set(parsed.organizationId(), null);
        try {
            if (userSessionRepository
                    .findBySessionId(parsed.sessionId())
                    .filter(s -> s.getDeletedAt() == null)
                    .isEmpty()) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
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
