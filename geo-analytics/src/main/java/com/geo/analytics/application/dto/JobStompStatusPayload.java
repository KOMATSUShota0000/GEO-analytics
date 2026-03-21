package com.geo.analytics.application.dto;

import com.geo.analytics.domain.entity.JobEntity;
import java.time.LocalDateTime;
import java.util.UUID;

public record JobStompStatusPayload(
    UUID jobId,
    String jobStatus,
    String brandName,
    String errorMessage,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static JobStompStatusPayload from(JobEntity jobEntity) {
        return new JobStompStatusPayload(
            jobEntity.getId(),
            jobEntity.getJobStatus() != null ? jobEntity.getJobStatus().name() : "UNKNOWN",
            jobEntity.getBrandName(),
            jobEntity.getErrorMessage(),
            jobEntity.getCreatedAt(),
            jobEntity.getUpdatedAt());
    }
}
