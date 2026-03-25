package com.geo.analytics.web.dto;

import com.geo.analytics.domain.entity.JobEntity;
import java.time.LocalDateTime;
import java.util.UUID;

public record JobStatusResponse(
    UUID jobId,
    UUID projectId,
    String jobStatus,
    String brandName,
    String errorMessage,
    String pdfStatus,
    String pdfFilePath,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static JobStatusResponse from(JobEntity jobEntity) {
        return new JobStatusResponse(
            jobEntity.getId(),
            jobEntity.getProjectId(),
            jobEntity.getJobStatus() != null ? jobEntity.getJobStatus().name() : "UNKNOWN",
            jobEntity.getBrandName(),
            jobEntity.getErrorMessage(),
            jobEntity.getPdfStatus(),
            jobEntity.getPdfFilePath(),
            jobEntity.getCreatedAt(),
            jobEntity.getUpdatedAt()
        );
    }
}
