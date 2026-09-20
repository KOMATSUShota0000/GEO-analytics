package com.geo.analytics.web.dto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.ProjectEntity;
import com.geo.analytics.domain.model.MinorityReport;
import java.lang.StrictMath;
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
    @JsonProperty("score_breakdown") ScoreBreakdown scoreBreakdown,
    @JsonProperty("content_evidence") List<ContentEvidenceItemResponse> contentEvidence,
    @JsonProperty("technical_evidence") String technicalEvidence,
    @JsonProperty("remediation_tasks") List<RemediationTaskResponse> remediationTasks,
    @JsonProperty("ai_recognition_summary") AiRecognitionSummaryResponse aiRecognitionSummary,
    @JsonProperty("emotional_alert") EmotionalAlertPayload emotionalAlert,
    @JsonProperty("reputation_average") Integer reputationAverage,
    @JsonProperty("minority_reports") List<MinorityReportDto> minorityReports
) {
    public JobAnalysisDetailResponse {
        jobSummaryRecommendedActions =
                jobSummaryRecommendedActions != null ? List.copyOf(jobSummaryRecommendedActions) : List.of();
        contentEvidence = contentEvidence != null ? List.copyOf(contentEvidence) : List.of();
        remediationTasks = remediationTasks != null ? List.copyOf(remediationTasks) : List.of();
        minorityReports = minorityReports != null ? List.copyOf(minorityReports) : List.of();
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
            ScoreBreakdown scoreBreakdown,
            List<ContentEvidenceItemResponse> contentEvidence,
            String technicalEvidence,
            List<RemediationTaskResponse> remediationTasks,
            AiRecognitionSummaryResponse aiRecognitionSummary,
            ObjectMapper objectMapper) {
        JobProjectResponse projectResponse = projectEntity != null ? JobProjectResponse.from(projectEntity) : null;
        String bc = resolveBrandColor(jobEntity, projectEntity);
        String logo = resolveLogoUrl(jobEntity, projectEntity);
        EmotionalAlertPayload emotionalAlert = parseEmotionalAlertJson(jobEntity.getEmotionalAlertJson(), objectMapper);
        // Why: 平均は「評判が付いたクエリ」だけで取る。言及の無いクエリを0点として混ぜると、可視性の低さが
        //      評判の低さとして二重に効いてしまう（#62）。
        Integer reputationAverage = averageReputation(resultDetails);
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
            scoreBreakdown,
            contentEvidence != null ? List.copyOf(contentEvidence) : List.of(),
            technicalEvidence,
            remediationTasks != null ? List.copyOf(remediationTasks) : List.of(),
            aiRecognitionSummary,
            emotionalAlert,
            reputationAverage,
            toMinorityReportDtos(jobEntity));
    }

    /** Why: 合意案に入らなかった尖った提案は議論の成果物で、改善タスクとは別枠で画面に出す（#80）。 */
    private static List<MinorityReportDto> toMinorityReportDtos(JobEntity jobEntity) {
        List<MinorityReport> reports = jobEntity.getMinorityReports();
        if (reports == null || reports.isEmpty()) {
            return List.of();
        }
        return reports.stream()
                .filter(r -> r != null && r.insight() != null && !r.insight().isBlank())
                .map(r -> new MinorityReportDto(r.insight(), r.conflictReason(), r.evidence()))
                .toList();
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

    private static Integer averageReputation(List<ResultDetailResponse> resultDetails) {
        if (resultDetails == null || resultDetails.isEmpty()) {
            return null;
        }
        int sum = 0;
        int count = 0;
        for (ResultDetailResponse detail : resultDetails) {
            if (detail.reputationScore() != null) {
                sum += detail.reputationScore();
                count++;
            }
        }
        return count == 0 ? null : (int) StrictMath.round((double) sum / count);
    }
}
