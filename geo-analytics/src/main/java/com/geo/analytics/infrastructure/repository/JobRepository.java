package com.geo.analytics.infrastructure.repository;

import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.enums.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface JobRepository extends JpaRepository<JobEntity, UUID> {
    List<JobEntity> findByJobStatus(JobStatus jobStatus);
}
