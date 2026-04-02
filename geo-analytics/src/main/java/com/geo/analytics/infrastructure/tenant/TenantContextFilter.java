package com.geo.analytics.infrastructure.tenant;

import com.geo.analytics.application.service.WorkspacePlanResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class TenantContextFilter extends OncePerRequestFilter {
    public static final String TENANT_HEADER = "X-Tenant-ID";

    private final WorkspacePlanResolver workspacePlanResolver;

    public TenantContextFilter(WorkspacePlanResolver workspacePlanResolver) {
        this.workspacePlanResolver = workspacePlanResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String p = stripContextPath(request);
        return !p.startsWith("/api/");
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
