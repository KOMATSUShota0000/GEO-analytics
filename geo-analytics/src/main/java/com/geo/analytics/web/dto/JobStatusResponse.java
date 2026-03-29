package com.geo.analytics.web.dto;

import com.geo.analytics.application.dto.StrategyInsight;
import com.geo.analytics.domain.entity.JobEntity;
import java.time.LocalDateTime;
import java.util.List;
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
    LocalDateTime updatedAt,
    String diagnosticMessage,
    List<String> recommendedActions,
    Double jobMedianModifiedZ
) {
    public static JobStatusResponse from(JobEntity jobEntity) {
        return from(jobEntity, null);
    }

    public static JobStatusResponse from(JobEntity jobEntity, StrategyInsight rollup) {
        String dm = null;
        List<String> ra = List.of();
        Double zm = null;
        if (rollup != null
            && rollup.diagnosticMessage() != null
            && !rollup.diagnosticMessage().isBlank()) {
            dm = rollup.diagnosticMessage();
            ra = List.copyOf(rollup.recommendedActions());
            zm = rollup.representativeModifiedZ();
        }
        return new JobStatusResponse(
            jobEntity.getId(),
            jobEntity.getProjectId(),
            jobEntity.getJobStatus() != null ? jobEntity.getJobStatus().name() : "UNKNOWN",
            jobEntity.getBrandName(),
            jobEntity.getErrorMessage(),
            jobEntity.getPdfStatus(),
            jobEntity.getPdfFilePath(),
            jobEntity.getCreatedAt(),
            jobEntity.getUpdatedAt(),
            dm,
            ra,
            zm);
    }
}
