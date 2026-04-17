package com.geo.analytics.infrastructure.ratelimit;

import com.geo.analytics.application.service.RateLimiterService;
import com.geo.analytics.domain.enums.PricingPlan;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 35)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();
    private static final List<RequestMatcher> SKIP_RATE_LIMIT =
            List.of(
                    PATHS.matcher(HttpMethod.GET, "/api/csrf"),
                    PATHS.matcher(HttpMethod.POST, "/api/login"),
                    PATHS.matcher(HttpMethod.POST, "/api/auth/refresh"),
                    PATHS.matcher("/api/public/**"));

    private final RateLimiterService rateLimiterService;

    public RateLimitFilter(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String p = stripContextPath(request);
        if (!p.startsWith("/api/")) {
            return true;
        }
        return SKIP_RATE_LIMIT.stream().anyMatch(m -> m.matches(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        PricingPlan plan = PricingPlan.STANDARD;
        if (!rateLimiterService.tryAcquire(plan)) {
            response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "Rate limit exceeded");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static String stripContextPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
            return uri.substring(ctx.length());
        }
        return uri;
    }
}
