package com.geo.analytics.infrastructure.tenant;

import com.geo.analytics.domain.enums.SubscriptionPlan;
import java.lang.ScopedValue;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Captures {@link TenantContextHolder}, {@link TenantPlanScope} bindings, {@link SecurityContextHolder}, and
 * {@link MDC} on the calling thread and re-establishes them for {@link java.util.concurrent.CompletableFuture},
 * {@link org.springframework.scheduling.annotation.Async}, etc.
 */
public final class ContextPropagator {

    private ContextPropagator() {}

    /**
     * Returns a {@link Supplier} that runs {@code supplier} with the same tenant / plan {@link ScopedValue} bindings,
     * {@link SecurityContext}, and {@link MDC} as captured at wrap time.
     *
     * <p>子スレッド終了時は {@link MDC#getCopyOfContextMap()} で保存した「実行直前」の状態へ復元し、プール再利用時の TraceID 混線を防ぐ。
     */
    public static <T> Supplier<T> wrap(Supplier<T> supplier) {
        Optional<TenantContext> ctx = TenantContextHolder.current();
        Optional<String> tenantIdStr = TenantPlanScope.currentTenantIdString();
        Optional<SubscriptionPlan> plan = TenantPlanScope.currentSubscriptionPlan();
        SecurityContext captured = copySecurityContext(SecurityContextHolder.getContext());
        Map<String, String> parentMdc = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> priorOnChild = MDC.getCopyOfContextMap();
            SecurityContext threadPrevious = SecurityContextHolder.getContext();
            try {
                if (parentMdc != null) {
                    MDC.setContextMap(new HashMap<>(parentMdc));
                }
                SecurityContextHolder.setContext(captured);
                return runWithCapturedScopes(ctx, tenantIdStr, plan, supplier);
            } finally {
                if (priorOnChild == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(new HashMap<>(priorOnChild));
                }
                restoreSecurityContext(threadPrevious);
            }
        };
    }

    public static Runnable wrapRunnable(Runnable runnable) {
        Supplier<Void> wrapped =
                wrap(() -> {
                    runnable.run();
                    return null;
                });
        return () -> wrapped.get();
    }

    private static <T> T runWithCapturedScopes(
            Optional<TenantContext> ctx,
            Optional<String> tenantIdStr,
            Optional<SubscriptionPlan> plan,
            Supplier<T> supplier) {
        if (ctx.isEmpty() && tenantIdStr.isEmpty() && plan.isEmpty()) {
            return supplier.get();
        }
        Supplier<T> work = supplier;
        if (plan.isPresent()) {
            SubscriptionPlan p = plan.get();
            Supplier<T> inner = work;
            work = () -> ScopedValue.where(TenantPlanScope.TENANT_PLAN, p).call(inner::get);
        }
        if (tenantIdStr.isPresent()) {
            String t = tenantIdStr.get();
            Supplier<T> inner = work;
            work = () -> ScopedValue.where(TenantPlanScope.TENANT_ID, t).call(inner::get);
        }
        if (ctx.isPresent()) {
            TenantContext c = ctx.get();
            Supplier<T> inner = work;
            work = () -> ScopedValue.where(TenantContextHolder.CONTEXT, c).call(inner::get);
        }
        return work.get();
    }

    private static SecurityContext copySecurityContext(SecurityContext source) {
        SecurityContext copy = SecurityContextHolder.createEmptyContext();
        if (source != null && source.getAuthentication() != null) {
            copy.setAuthentication(source.getAuthentication());
        }
        return copy;
    }

    private static void restoreSecurityContext(SecurityContext previous) {
        if (previous == null || previous.getAuthentication() == null) {
            SecurityContextHolder.clearContext();
        } else {
            SecurityContextHolder.setContext(previous);
        }
    }
}
