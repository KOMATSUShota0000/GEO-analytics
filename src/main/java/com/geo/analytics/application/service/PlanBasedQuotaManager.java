package com.geo.analytics.application.service;

import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.model.QuotaCreditCalculator;
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
    private final WorkspacePlanResolver workspacePlanResolver;

    public PlanBasedQuotaManager(
            @Qualifier("planQuotaProxyManager") ProxyManager<String> proxyManager,
            @Qualifier(Bucket4jConfiguration.PLAN_QUOTA_CAFFEINE_PROXY_MANAGER)
                    CaffeineProxyManager<String> planQuotaCaffeineProxyManager,
            WorkspacePlanResolver workspacePlanResolver) {
        this.proxyManager = Objects.requireNonNull(proxyManager);
        this.planQuotaCaffeineProxyManager = Objects.requireNonNull(planQuotaCaffeineProxyManager);
        this.workspacePlanResolver = Objects.requireNonNull(workspacePlanResolver);
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
        // Why: ジョブのプランと同じ読み方にそろえる。リポジトリを @Transactional の外から呼ぶと RLS 用の組織IDが
        //      接続に渡らずワークスペースが見えないため、どのプランでも STANDARD になっていた（#193、ADR-042）。
        return workspacePlanResolver.resolvePlan(workspaceId);
    }

    private BucketConfiguration configurationForWorkspace(UUID workspaceId) {
        var daily = resolveWorkspacePlan(workspaceId).getDailyLimit();
        long capacity = (long) daily * QuotaCreditCalculator.DEPOSIT_PER_KEYWORD;
        var bandwidth = Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(capacity, Duration.ofDays(1))
                .build();
        return BucketConfiguration.builder().addLimit(bandwidth).build();
    }
}

