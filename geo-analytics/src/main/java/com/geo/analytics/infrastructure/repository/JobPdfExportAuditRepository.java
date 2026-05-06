package com.geo.analytics.infrastructure.repository;

import com.geo.analytics.domain.entity.JobPdfExportAuditEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobPdfExportAuditRepository
        extends JpaRepository<JobPdfExportAuditEntity, UUID> {

    List<JobPdfExportAuditEntity> findByJobIdOrderByExportedAtDesc(UUID jobId);
}
