package com.geo.analytics.web.dto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.ProjectEntity;
import java.util.List;
import java.util.UUID;
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
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
    List<ResultDetailResponse> results,
    @JsonProperty("fact_based_score") Double factBasedScore,
    @JsonProperty("rubric_gaps") List<String> rubricGaps,
    @JsonProperty("score_breakdown") ScoreBreakdown scoreBreakdown,
    @JsonProperty("content_evidence") List<ContentEvidenceItemResponse> contentEvidence,
    @JsonProperty("remediation_tasks") List<RemediationTaskResponse> remediationTasks,
    @JsonProperty("ai_recognition_summary") AiRecognitionSummaryResponse aiRecognitionSummary,
    @JsonProperty("emotional_alert") EmotionalAlertPayload emotionalAlert
) {
    public JobAnalysisDetailResponse {
        jobSummaryRecommendedActions =
                jobSummaryRecommendedActions != null ? List.copyOf(jobSummaryRecommendedActions) : List.of();
        rubricGaps = rubricGaps != null ? List.copyOf(rubricGaps) : List.of();
        contentEvidence = contentEvidence != null ? List.copyOf(contentEvidence) : List.of();
        remediationTasks = remediationTasks != null ? List.copyOf(remediationTasks) : List.of();
    }
    public static JobAnalysisDetailResponse from(
            JobEntity jobEntity,
            ProjectEntity projectEntity,
            List<ResultDetailResponse> resultDetails,
            String jobSummaryDiagnostic,
            List<String> jobSummaryRecommendedActions,
            Double jobMedianModifiedZ,
            Integer jobMedianVisibilityStage,
            Double factBasedScore,
            List<String> rubricGaps,
            ScoreBreakdown scoreBreakdown,
            List<ContentEvidenceItemResponse> contentEvidence,
            List<RemediationTaskResponse> remediationTasks,
            AiRecognitionSummaryResponse aiRecognitionSummary,
            ObjectMapper objectMapper) {
        JobProjectResponse projectResponse = projectEntity != null ? JobProjectResponse.from(projectEntity) : null;
        String bc = resolveBrandColor(jobEntity, projectEntity);
        String logo = resolveLogoUrl(jobEntity, projectEntity);
        EmotionalAlertPayload emotionalAlert = parseEmotionalAlertJson(jobEntity.getEmotionalAlertJson(), objectMapper);
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
            resultDetails,
            factBasedScore,
            rubricGaps,
            scoreBreakdown,
            contentEvidence != null ? List.copyOf(contentEvidence) : List.of(),
            remediationTasks != null ? List.copyOf(remediationTasks) : List.of(),
            aiRecognitionSummary,
            emotionalAlert);
    }

    private static EmotionalAlertPayload parseEmotionalAlertJson(String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank() || objectMapper == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, EmotionalAlertPayload.class);
        } catch (Exception exception) {
            return null;
        }
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
