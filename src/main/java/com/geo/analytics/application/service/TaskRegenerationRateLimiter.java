package com.geo.analytics.application.service;

import com.geo.analytics.domain.exception.TaskRegenerationTooManyRequestsException;
import com.geo.analytics.infrastructure.tenant.TenantPlanScope;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import java.time.Duration;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class TaskRegenerationRateLimiter {

    private static final String PREFIX = "task-regen:";
    private final ProxyManager<String> proxyManager;

    public TaskRegenerationRateLimiter(@Qualifier("rateLimitProxyManager") ProxyManager<String> proxyManager) {
        this.proxyManager = Objects.requireNonNull(proxyManager);
    }

    public void acquireOrThrow() {
        String tenantId = TenantPlanScope.currentTenantIdString()
                .filter(t -> !t.isBlank())
                .orElseThrow(() -> new IllegalStateException("tenant"));
        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(3)
                        .refillIntervally(3, Duration.ofMinutes(1))
                        .build())
                .build();
        String key = PREFIX + tenantId;
        boolean ok =
                proxyManager.builder().build(key, () -> configuration).tryConsume(1L);
        if (!ok) {
            throw new TaskRegenerationTooManyRequestsException();
        }
    }
}
