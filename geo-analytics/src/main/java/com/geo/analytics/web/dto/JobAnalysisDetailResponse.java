package com.geo.analytics.web.dto;

import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.ProjectEntity;
import java.util.List;
import java.util.UUID;

public record JobAnalysisDetailResponse(
    UUID jobId,
    String jobStatus,
    String brandName,
    String errorMessage,
    JobProjectResponse project,
    List<ResultDetailResponse> results
) {
    public static JobAnalysisDetailResponse from(JobEntity jobEntity, ProjectEntity projectEntity, List<ResultDetailResponse> resultDetails) {
        JobProjectResponse projectResponse = projectEntity != null ? JobProjectResponse.from(projectEntity) : null;
        return new JobAnalysisDetailResponse(
            jobEntity.getId(),
            jobEntity.getJobStatus().name(),
            jobEntity.getBrandName(),
            jobEntity.getErrorMessage(),
            projectResponse,
            resultDetails);
    }
}
