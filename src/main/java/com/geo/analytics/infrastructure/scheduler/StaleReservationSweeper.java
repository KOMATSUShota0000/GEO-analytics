package com.geo.analytics.infrastructure.scheduler;

import com.geo.analytics.application.service.CreditVaultService;
import com.geo.analytics.domain.entity.WalletTransactionEntity;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import com.geo.analytics.infrastructure.tenant.TenantPlanScope;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 孤児 RESERVE 回収スケジューラ。
 *
 * <p>JVM 異常終了等で {@code CreditReservationAspect} の finally が実行されず、SETTLE/REFUND の子を
 * 持たない RESERVE 行が残留するとクレジットが永久凍結される。本スケジューラが一定間隔で十分に古い
 * 孤児 RESERVE を検出し返金することで、クレジットの自己修復性を担保する。
 */
@Component
public class StaleReservationSweeper {

    private static final Logger log = LoggerFactory.getLogger(StaleReservationSweeper.class);

    // 仮実行中の正常な長時間ジョブ（マルチペルソナ討論 等の LLM ジョブ）を誤返金しないため、
    // 推奨下限の 30 分を大きく上回る 60 分のマージンを採用。判断根拠は ADR を参照。
    private static final long STALE_CUTOFF_MINUTES = 60L;

    private final CreditVaultService creditVaultService;

    public StaleReservationSweeper(CreditVaultService creditVaultService) {
        this.creditVaultService = creditVaultService;
    }

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Tokyo")
    public void sweepStaleReservations() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(STALE_CUTOFF_MINUTES);
        // findStaleReservations はテナント非依存のスケジューラ用グローバルクエリ。
        List<WalletTransactionEntity> stale = creditVaultService.findStaleReservations(cutoff);
        if (stale.isEmpty()) {
            return;
        }
        log.info("StaleReservationSweeper found {} orphan RESERVE row(s) older than {}", stale.size(), cutoff);
        for (WalletTransactionEntity reserve : stale) {
            UUID reservationId = reserve.getId();
            UUID organizationId = reserve.getOrganizationId();
            try {
                // refund は requireOrgId()（TenantContextHolder）に依存するため、対象行の
                // organizationId でテナントスコープを確立してから呼ぶ。プラン値は返金処理で
                // 使用しないため任意（STANDARD を指定）。
                TenantPlanScope.executeWithTenantOrganizationAndPlan(
                        DefaultTenantIds.WORKSPACE_ID,
                        organizationId,
                        SubscriptionPlan.STANDARD,
                        () -> creditVaultService.refund(reservationId));
                log.info(
                        "Refunded orphan RESERVE reservationId={} organizationId={}",
                        reservationId,
                        organizationId);
            } catch (RuntimeException refundException) {
                // 走査〜返金の間にジョブが完了し子行が出来た場合、refund 側の
                // existsByParentReservationId ガードが例外を投げる。二重返金を防ぐ正常系として
                // 個別に握り、残りの行の回収を継続する。
                log.warn(
                        "Skipped orphan RESERVE refund reservationId={} organizationId={}",
                        reservationId,
                        organizationId,
                        refundException);
            }
        }
    }
}
