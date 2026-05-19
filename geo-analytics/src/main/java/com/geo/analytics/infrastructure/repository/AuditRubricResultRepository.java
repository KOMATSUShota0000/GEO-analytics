package com.geo.analytics.infrastructure.repository;

import com.geo.analytics.domain.entity.AuditRubricResultEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditRubricResultRepository extends JpaRepository<AuditRubricResultEntity, UUID> {

    List<AuditRubricResultEntity> findByAuditHistoryId(UUID auditHistoryId);

    /**
     * 指定 audit_history 群から criterion 別・self/competitor 別の平均スコアを集約する。
     * 相対評価ベンチマーク用。N+1 を避けるため単一クエリで集計する。
     * tenant_id は RLS により自動スコープされる（{@code BaseTenantEntity}）。
     */
    @Query(
            "select r.isSelf as selfFlag, r.criterionId as criterionId, avg(r.score) as avgScore "
                    + "from AuditRubricResultEntity r "
                    + "where r.auditHistoryId in :ids and r.criterionId in :criteria "
                    + "group by r.isSelf, r.criterionId")
    List<RubricBenchmarkAggregate> aggregateByCriterion(
            @Param("ids") List<UUID> ids, @Param("criteria") List<String> criteria);

    /** 相対評価ベンチマーク集計の射影。 */
    interface RubricBenchmarkAggregate {
        Boolean getSelfFlag();

        String getCriterionId();

        Number getAvgScore();
    }
}
