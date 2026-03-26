package com.geo.analytics.infrastructure.ratelimit;
import com.geo.analytics.application.service.RateLimiterService;
import com.geo.analytics.domain.enums.PricingPlan;
import com.geo.analytics.infrastructure.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 35)
public class RateLimitFilter extends OncePerRequestFilter {
    private final RateLimiterService rateLimiterService;
    private final UserRepository userRepository;
    public RateLimitFilter(RateLimiterService rateLimiterService, UserRepository userRepository) {
        this.rateLimiterService = rateLimiterService;
        this.userRepository = userRepository;
    }
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String p = stripContextPath(request);
        return !p.startsWith("/api/");
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String name = auth != null && auth.isAuthenticated() && auth.getName() != null ? auth.getName() : "";
        PricingPlan plan = userRepository.findByUsername(name).map(u -> u.getPricingPlan()).orElse(PricingPlan.STANDARD);
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
