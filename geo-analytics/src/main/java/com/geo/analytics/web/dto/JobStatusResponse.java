package com.geo.analytics.web.dto;

import com.geo.analytics.domain.entity.JobEntity;
import java.time.LocalDateTime;
import java.util.UUID;

public record JobStatusResponse(
    UUID jobId,
    String jobStatus,
    String brandName,
    String errorMessage,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static JobStatusResponse from(JobEntity jobEntity) {
        return new JobStatusResponse(
            jobEntity.getId(),
            jobEntity.getJobStatus() != null ? jobEntity.getJobStatus().name() : "UNKNOWN",
            jobEntity.getBrandName(),
            jobEntity.getErrorMessage(),
            jobEntity.getCreatedAt(),
            jobEntity.getUpdatedAt()
        );
    }
}
