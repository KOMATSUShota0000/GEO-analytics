package com.geo.analytics.application.service;

import com.geo.analytics.domain.entity.WorkspaceEntity;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.model.QuotaCreditCalculator;
import com.geo.analytics.infrastructure.repository.WorkspaceRepository;
import com.geo.analytics.infrastructure.tenant.TenantContext;
import com.geo.analytics.infrastructure.config.Bucket4jConfiguration;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.caffeine.CaffeineProxyManager;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

@Component
public class PlanBasedQuotaManager {
    private final ProxyManager<String> proxyManager;
    private final CaffeineProxyManager<String> planQuotaCaffeineProxyManager;
    private final WorkspaceRepository workspaceRepository;

    public PlanBasedQuotaManager(
            @Qualifier("planQuotaProxyManager") ProxyManager<String> proxyManager,
            @Qualifier(Bucket4jConfiguration.PLAN_QUOTA_CAFFEINE_PROXY_MANAGER)
                    CaffeineProxyManager<String> planQuotaCaffeineProxyManager,
            WorkspaceRepository workspaceRepository) {
        this.proxyManager = Objects.requireNonNull(proxyManager);
        this.planQuotaCaffeineProxyManager = Objects.requireNonNull(planQuotaCaffeineProxyManager);
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository);
    }

    public void invalidateTenantBucket(UUID workspaceId) {
        planQuotaCaffeineProxyManager.getCache().invalidate(workspaceId.toString());
    }

    public Bucket resolve(UUID workspaceId) {
        var key = workspaceId.toString();
        return proxyManager.builder().build(key, () -> configurationForWorkspace(workspaceId));
    }

    public void addTokens(UUID workspaceId, long tokens) {
        if (tokens <= 0L) {
            return;
        }
        resolve(workspaceId).addTokens(tokens);
    }

    public SubscriptionPlan resolveWorkspacePlan(UUID workspaceId) {
        return TenantContext.executeWithTenant(workspaceId, () -> workspaceRepository.findById(workspaceId)
                .map(WorkspaceEntity::getSubscriptionPlan)
                .filter(Objects::nonNull)
                .orElse(SubscriptionPlan.STANDARD));
    }

    private BucketConfiguration configurationForWorkspace(UUID workspaceId) {
        return TenantContext.executeWithTenant(workspaceId, () -> {
            var plan = workspaceRepository.findById(workspaceId)
                    .map(WorkspaceEntity::getSubscriptionPlan)
                    .filter(Objects::nonNull)
                    .orElse(SubscriptionPlan.STANDARD);
            var daily = plan.getDailyLimit();
            long capacity = (long) daily * QuotaCreditCalculator.DEPOSIT_PER_KEYWORD;
            var bandwidth = Bandwidth.builder()
                    .capacity(capacity)
                    .refillIntervally(capacity, Duration.ofDays(1))
                    .build();
            return BucketConfiguration.builder().addLimit(bandwidth).build();
        });
    }
}

