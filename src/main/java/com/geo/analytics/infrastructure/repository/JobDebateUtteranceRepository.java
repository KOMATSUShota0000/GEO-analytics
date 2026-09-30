package com.geo.analytics.infrastructure.repository;

import com.geo.analytics.domain.entity.JobDebateUtteranceEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface JobDebateUtteranceRepository extends Repository<JobDebateUtteranceEntity, UUID> {

    List<JobDebateUtteranceEntity> findByJobIdOrderBySeqAsc(UUID jobId);
}
