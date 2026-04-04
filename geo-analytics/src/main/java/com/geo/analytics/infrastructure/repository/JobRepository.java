package com.geo.analytics.infrastructure.repository;

import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.enums.JobStatus;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<JobEntity, UUID> {
    List<JobEntity> findByJobStatus(JobStatus jobStatus);
    Optional<JobEntity> findFirstByProjectIdOrderByCreatedAtDesc(UUID projectId);
    List<JobEntity> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
    Optional<JobEntity> findByTenantIdAndCreateIdempotencyKey(String tenantId, UUID createIdempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from JobEntity j where j.id = :id")
    Optional<JobEntity> findByIdForUpdate(@Param("id") UUID id);

    List<JobEntity> findByGapAnalysisGeminiJobNameIsNotNullAndGapAnalysisCompletedIsFalse();

    @Query(
        "select j from JobEntity j where j.jobStatus = :st and j.appliedPlan in :plans and j.gapBatchIdempotencyKey is not null and (j.gapAnalysisGeminiJobName is null or j.gapAnalysisGeminiJobName = '')")
    List<JobEntity> findProJobsAwaitingGapBatchCreation(
            @Param("st") JobStatus jobStatus,
            @Param("plans") List<SubscriptionPlan> plans);
}
