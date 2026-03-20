package com.geo.analytics.web.dto;

import com.geo.analytics.domain.entity.JobEntity;
import java.util.List;
import java.util.UUID;

public record JobAnalysisDetailResponse(
    UUID jobId,
    String jobStatus,
    String brandName,
    String errorMessage,
    List<ResultDetailResponse> results
) {
    public static JobAnalysisDetailResponse from(JobEntity jobEntity, List<ResultDetailResponse> resultDetails) {
        return new JobAnalysisDetailResponse(
            jobEntity.getId(),
            jobEntity.getJobStatus().name(),
            jobEntity.getBrandName(),
            jobEntity.getErrorMessage(),
            resultDetails
        );
    }
}
