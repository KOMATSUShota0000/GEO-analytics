package com.geo.analytics.web.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.geo.analytics.application.dto.StrategyInsight;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.enums.DebateStatus;
import com.geo.analytics.domain.model.DebateUtterance;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
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
    String adviceSource,
    String debateStatus,
    List<DebateUtteranceResponse> debateUtterances
) {
    /**
     * Why: サーバーが議論の途中で止まると（再起動など）、議論中の印が残ったままになる。画面は議論中のあいだ
     * 問い合わせを続けるので、ジョブの最後の更新からこの時間を過ぎた「議論中」は失敗として返す（#198）。
     * 議論はふつう数分で終わる。AI の呼び出しが時間切れとやり直しを重ねても収まる長さにした。
     */
    static final Duration DEBATE_STALE_AFTER = Duration.ofMinutes(15);

    public static JobStatusResponse from(JobEntity jobEntity) {
        return from(jobEntity, null);
    }

    public static JobStatusResponse from(JobEntity jobEntity, StrategyInsight rollup) {
        return from(jobEntity, rollup, List.of(), LocalDateTime.now());
    }

    /**
     * Why: 診断文は保存済みの総合診断（4ペルソナ議論、または議論失敗時のテンプレ）を優先する。旧実装はここで
     * 毎回テンプレートから作り直した文を返し、画面がそれを優先表示していたため、議論の診断が画面に出ず
     * PDF とも食い違っていた（#141）。保存前（解析中）だけテンプレートで埋める。
     */
    public static JobStatusResponse from(
            JobEntity jobEntity, StrategyInsight rollup, List<DebateUtterance> utterances, LocalDateTime now) {
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
        String debateStatus = debateStatusAt(jobEntity, now);
        // Why: 失敗した議論の発言は消す決まり（ADR-095）。時間切れで失敗として返すときも、発言は返さない。
        List<DebateUtteranceResponse> debateUtterances =
                DebateStatus.FAILED.name().equals(debateStatus)
                        ? List.of()
                        : utterances.stream().map(DebateUtteranceResponse::from).toList();
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
            jobEntity.getJobAdviceSource(),
            debateStatus,
            debateUtterances);
    }

    private static String debateStatusAt(JobEntity jobEntity, LocalDateTime now) {
        String status = jobEntity.getDebateStatus();
        LocalDateTime updatedAt = jobEntity.getUpdatedAt();
        boolean stale = DebateStatus.RUNNING.name().equals(status)
                && updatedAt != null
                && updatedAt.isBefore(now.minus(DEBATE_STALE_AFTER));
        return stale ? DebateStatus.FAILED.name() : status;
    }
}
