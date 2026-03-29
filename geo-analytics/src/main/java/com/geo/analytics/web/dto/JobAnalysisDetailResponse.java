package com.geo.analytics.web.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.ProjectEntity;
import java.util.List;
import java.util.UUID;
public record JobAnalysisDetailResponse(
    UUID jobId,
    String jobStatus,
    String brandName,
    String errorMessage,
    @JsonProperty("brand_color") String brandColor,
    @JsonProperty("logo_url") String logoUrl,
    JobProjectResponse project,
    @JsonProperty("job_summary_diagnostic") String jobSummaryDiagnostic,
    @JsonProperty("job_summary_recommended_actions") List<String> jobSummaryRecommendedActions,
    @JsonProperty("job_median_modified_z") Double jobMedianModifiedZ,
    @JsonProperty("job_median_visibility_stage") Integer jobMedianVisibilityStage,
    List<ResultDetailResponse> results
) {
    public static JobAnalysisDetailResponse from(
            JobEntity jobEntity,
            ProjectEntity projectEntity,
            List<ResultDetailResponse> resultDetails,
            String jobSummaryDiagnostic,
            List<String> jobSummaryRecommendedActions,
            Double jobMedianModifiedZ,
            Integer jobMedianVisibilityStage) {
        JobProjectResponse projectResponse = projectEntity != null ? JobProjectResponse.from(projectEntity) : null;
        String bc = resolveBrandColor(jobEntity, projectEntity);
        String logo = resolveLogoUrl(jobEntity, projectEntity);
        return new JobAnalysisDetailResponse(
            jobEntity.getId(),
            jobEntity.getJobStatus().name(),
            jobEntity.getBrandName(),
            jobEntity.getErrorMessage(),
            bc,
            logo,
            projectResponse,
            jobSummaryDiagnostic,
            jobSummaryRecommendedActions != null ? List.copyOf(jobSummaryRecommendedActions) : List.of(),
            jobMedianModifiedZ,
            jobMedianVisibilityStage,
            resultDetails);
    }
    private static String resolveBrandColor(JobEntity jobEntity, ProjectEntity projectEntity) {
        String j = jobEntity.getBrandColor();
        if (j != null && !j.isBlank()) {
            return j;
        }
        if (projectEntity != null) {
            String p = projectEntity.getBrandColor();
            if (p != null && !p.isBlank()) {
                return p;
            }
        }
        return "#4F46E5";
    }
    private static String resolveLogoUrl(JobEntity jobEntity, ProjectEntity projectEntity) {
        String j = jobEntity.getLogoUrl();
        if (j != null && !j.isBlank()) {
            return j;
        }
        if (projectEntity != null) {
            String p = projectEntity.getLogoUrl();
            if (p != null && !p.isBlank()) {
                return p;
            }
        }
        return null;
    }
}
