package com.geo.analytics.infrastructure.repository;
import com.geo.analytics.domain.entity.AuditHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface AuditHistoryRepository extends JpaRepository<AuditHistoryEntity, UUID> {
    List<AuditHistoryEntity> findByJobId(UUID jobId);
    Optional<AuditHistoryEntity> findByJobIdAndQuery(UUID jobId, String query);
    List<AuditHistoryEntity> findByProject_IdOrderByAuditDateAsc(UUID projectId);
}
