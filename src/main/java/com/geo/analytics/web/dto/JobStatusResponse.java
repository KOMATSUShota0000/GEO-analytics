package com.geo.analytics.web.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.geo.analytics.application.dto.StrategyInsight;
import com.geo.analytics.domain.entity.JobEntity;
import java.time.LocalDateTime;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
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
    Double jobMedianModifiedZ,
    String adviceSource
) {
    public static JobStatusResponse from(JobEntity jobEntity) {
        return from(jobEntity, null);
    }

    /**
     * Why: 診断文は保存済みの総合診断（4ペルソナ議論、または議論失敗時のテンプレ）を優先する。旧実装はここで
     * 毎回テンプレートから作り直した文を返し、画面がそれを優先表示していたため、議論の診断が画面に出ず
     * PDF とも食い違っていた（#141）。保存前（解析中）だけテンプレートで埋める。
     */
    public static JobStatusResponse from(JobEntity jobEntity, StrategyInsight rollup) {
        String dm = null;
        Double zm = null;
        if (rollup != null
            && rollup.diagnosticMessage() != null
            && !rollup.diagnosticMessage().isBlank()) {
            dm = rollup.diagnosticMessage();
            zm = rollup.representativeModifiedZ();
        }
        String stored = jobEntity.getJobDiagnosticMessage();
        if (stored != null && !stored.isBlank()) {
            dm = stored;
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
            zm,
            jobEntity.getJobAdviceSource());
    }
}
