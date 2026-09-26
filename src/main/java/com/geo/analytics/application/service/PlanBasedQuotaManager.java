package com.geo.analytics.application.service;

import com.geo.analytics.domain.entity.WorkspaceEntity;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.model.QuotaCreditCalculator;
import com.geo.analytics.infrastructure.repository.WorkspaceRepository;
import com.geo.analytics.infrastructure.tenant.TenantPlanScope;
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
        //caffeineはjavaのdbみたいな感じ、それに対してProxyManagerはDBMSみたいな通信・操作役。
        //Bucket4j はレート制限のトークンバケットアルゴリズムを実装したライブラリ（つまりBucket4j＝アルゴリズム、Caffeine＝そのアルゴリズムが状態を置く場所）
        //渡したkeyがキャッシュにあればそれを返して、
        // なければ新規作成（Bucket4j（レート制限ライブラリ）のバケット（＝ワークスペースごとの残クォータを表すオブジェクト）を）
        return proxyManager.builder().build(key, () -> configurationForWorkspace(workspaceId));
    }

    public void addTokens(UUID workspaceId, long tokens) {
        if (tokens <= 0L) {
            return;
        }
        resolve(workspaceId).addTokens(tokens);
    }
    //このワークスペースがどの契約プランに入っているかDBに問い合わせて、契約プランを返す。
    public SubscriptionPlan resolveWorkspacePlan(UUID workspaceId) {
        return TenantPlanScope.executeWithTenant(workspaceId, () -> workspaceRepository.findById(workspaceId)
                //メソッド参照は、WorkspaceEntity型のgetSubscriptionPlanメソッドを呼び出すことを意味している。
                .map(WorkspaceEntity::getSubscriptionPlan)
                .filter(Objects::nonNull)
                .orElse(SubscriptionPlan.STANDARD));
    }

    private BucketConfiguration configurationForWorkspace(UUID workspaceId) {
        //executeWithTenantはテナント情報をsetするメソッドなのでRLSを突破するためにここで実行してる
        //つまり、dbに触る前はこのメソッドを見ることが多いと思う。（上流でまとめてセットしてるからあんま見ないはず、そのルートを通らんやつとかに個々でじっこうしとるんや）
        return TenantPlanScope.executeWithTenant(workspaceId, () -> {
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

