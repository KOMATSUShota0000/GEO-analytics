package com.geo.analytics.infrastructure.tenant;

import com.geo.analytics.application.service.WorkspacePlanResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class TenantContextFilter extends OncePerRequestFilter {
    public static final String TENANT_HEADER = "X-Tenant-ID";

    private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();
    private static final List<RequestMatcher> SKIP_TENANT_HEADER =
            List.of(
                    PATHS.matcher(HttpMethod.GET, "/api/csrf"),
                    PATHS.matcher(HttpMethod.POST, "/api/login"),
                    PATHS.matcher(HttpMethod.POST, "/api/auth/refresh"));

    private final WorkspacePlanResolver workspacePlanResolver;

    public TenantContextFilter(WorkspacePlanResolver workspacePlanResolver) {
        this.workspacePlanResolver = workspacePlanResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String p = stripContextPath(request);
        if (!p.startsWith("/api/")) {
            return true;
        }
        return SKIP_TENANT_HEADER.stream().anyMatch(m -> m.matches(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String raw = request.getHeader(TENANT_HEADER);
        if (raw == null || raw.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing " + TENANT_HEADER);
            return;
        }
        UUID tenantUuid;
        try {
            tenantUuid = UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid " + TENANT_HEADER);
            return;
        }
        var plan = workspacePlanResolver.resolvePlan(tenantUuid);
        try {
            TenantContext.executeWithTenantAndPlan(tenantUuid, plan, () -> {
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

    private static String stripContextPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
            return uri.substring(ctx.length());
        }
        return uri;
    }
}
