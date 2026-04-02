package com.geo.analytics.application.service;
import com.geo.analytics.domain.enums.PricingPlan;
import com.geo.analytics.infrastructure.tenant.TenantContext;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.Objects;
@Service
public class RateLimiterService {
    private final ProxyManager<String> proxyManager;
    public RateLimiterService(@Qualifier("rateLimitProxyManager") ProxyManager<String> rateLimitProxyManager) {
        this.proxyManager = Objects.requireNonNull(rateLimitProxyManager);
    }
    public boolean tryAcquire(PricingPlan plan) {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            return false;
        }
        BucketConfiguration configuration = bucketConfiguration(plan);
        return proxyManager.builder().build(tenantId, () -> configuration).tryConsume(1L);
    }
    private static BucketConfiguration bucketConfiguration(PricingPlan plan) {
        Bandwidth burst = Bandwidth.builder()
                .capacity(plan.burstCapacity())
                .refillGreedy(plan.burstCapacity(), Duration.ofSeconds(1))
                .build();
        Bandwidth sustained = Bandwidth.builder()
                .capacity(plan.sustainedCapacity())
                .refillIntervally(plan.sustainedCapacity(), Duration.ofMinutes(1))
                .build();
        return BucketConfiguration.builder().addLimit(burst).addLimit(sustained).build();
    }
}
