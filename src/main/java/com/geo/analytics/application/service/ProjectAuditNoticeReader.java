package com.geo.analytics.application.service;

import com.geo.analytics.domain.entity.AuditHistoryEntity;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.ProjectEntity;
import com.geo.analytics.domain.enums.JobStatus;
import com.geo.analytics.infrastructure.repository.AuditHistoryRepository;
import com.geo.analytics.infrastructure.repository.JobRepository;
import com.geo.analytics.infrastructure.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 解析完了のお知らせに載せる内容を読み出す。
 * Why: RLS の組織IDは @Transactional の呼び出しでしか接続へ渡らない（RlsConnectionInterceptor）。
 *      NotificationService がリポジトリを直接呼んでいたためプロジェクトが見えず、お知らせが一度も送られていなかった（#174）。
 *      読み出しだけをここへ分け、メールの送信はトランザクションの外で行う。
 */
@Service
public class ProjectAuditNoticeReader {
    private final ProjectRepository projectRepository;
    private final AuditHistoryRepository auditHistoryRepository;
    private final JobRepository jobRepository;

    public ProjectAuditNoticeReader(
            ProjectRepository projectRepository,
            AuditHistoryRepository auditHistoryRepository,
            JobRepository jobRepository) {
        this.projectRepository = projectRepository;
        this.auditHistoryRepository = auditHistoryRepository;
        this.jobRepository = jobRepository;
    }

    @Transactional(readOnly = true)
    public Optional<AuditNotice> read(UUID projectId, UUID jobId) {
        ProjectEntity projectEntity = projectRepository.findById(projectId).orElse(null);
        if (projectEntity == null || projectEntity.getNotificationEmails().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new AuditNotice(
            projectEntity.getName(),
            projectEntity.getNotificationEmails(),
            buildDigest(jobId, projectId)));
    }

    private AuditDigest buildDigest(UUID jobId, UUID projectId) {
        List<AuditHistoryEntity> current = auditHistoryRepository.findByJobId(jobId);
        double currentAvg = current.stream()
            .map(AuditHistoryEntity::getSomScore)
            .filter(Objects::nonNull)
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0d);
        Optional<JobEntity> prevJob = jobRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
            .filter(j -> JobStatus.COMPLETED.equals(j.getJobStatus()) && !j.getId().equals(jobId))
            .findFirst();
        Double previousAvg = null;
        Map<String, Double> prevByQuery = new HashMap<>();
        if (prevJob.isPresent()) {
            List<AuditHistoryEntity> prevRows = auditHistoryRepository.findByJobId(prevJob.get().getId());
            previousAvg = prevRows.stream()
                .map(AuditHistoryEntity::getSomScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0d);
            for (AuditHistoryEntity row : prevRows) {
                prevByQuery.put(row.getQuery(), row.getSomScore());
            }
        }
        Double deltaAvg = previousAvg == null ? null : round1(currentAvg - previousAvg);
        List<VarianceLine> variances = new ArrayList<>();
        for (AuditHistoryEntity row : current) {
            Double p = prevByQuery.get(row.getQuery());
            Double curSom = row.getSomScore();
            double d = (p == null || curSom == null) ? 0d : curSom - p;
            variances.add(new VarianceLine(row.getQuery(), curSom, p, d));
        }
        variances.sort(Comparator.comparing((VarianceLine v) -> Math.abs(v.delta())).reversed());
        // Why: 並べ替えは丸める前の差で行い、メールに載せる値だけを小数1桁にそろえる（#174）。
        List<VarianceLine> top3 = variances.stream()
            .limit(3)
            .map(v -> new VarianceLine(v.keyword(), round1OrNull(v.currentSom()), round1OrNull(v.previousSom()), round1(v.delta())))
            .toList();
        return new AuditDigest(round1(currentAvg), previousAvg == null ? null : round1(previousAvg), deltaAvg, top3);
    }

    private static Double round1(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private static Double round1OrNull(Double v) {
        return v == null ? null : round1(v);
    }

    public record AuditNotice(String projectName, List<String> recipients, AuditDigest digest) {
    }

    public record AuditDigest(double currentAvg, Double previousAvg, Double deltaAvg, List<VarianceLine> top3) {
    }

    public record VarianceLine(String keyword, Double currentSom, Double previousSom, double delta) {
    }
}
