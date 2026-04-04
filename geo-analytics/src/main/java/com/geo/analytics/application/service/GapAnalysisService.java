package com.geo.analytics.application.service;

import com.geo.analytics.domain.entity.AuditHistoryEntity;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.enums.JobStatus;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.service.GeoVisibilityCalculatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Service
public final class GapAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(GapAnalysisService.class);
    private static final Executor SCHEDULER = Executors.newVirtualThreadPerTaskExecutor();
    private final JobPersistenceService jobPersistenceService;
    private final StrategyInsightService strategyInsightService;
    private final GapBatchSubmissionService gapBatchSubmissionService;

    public GapAnalysisService(
            JobPersistenceService jobPersistenceService,
            StrategyInsightService strategyInsightService,
            GapBatchSubmissionService gapBatchSubmissionService) {
        this.jobPersistenceService = jobPersistenceService;
        this.strategyInsightService = strategyInsightService;
        this.gapBatchSubmissionService = gapBatchSubmissionService;
    }

    public void scheduleForJob(UUID jobId) {
        SCHEDULER.execute(() -> {
            try {
                runForJob(jobId);
            } catch (Exception exception) {
                log.warn("gap_analysis_failed jobId={}", jobId, exception);
            }
        });
    }

    public void runForJob(UUID jobId) {
        JobEntity jobEntity = jobPersistenceService.findJobById(jobId);
        SubscriptionPlan plan = Objects.requireNonNullElse(jobEntity.getAppliedPlan(), SubscriptionPlan.STANDARD);
        List<AuditHistoryEntity> rows = jobPersistenceService.findResultsByJobId(jobId);
        if (rows.size() < 2) {
            finalizeJobRollupAndGapFlag(jobId, rows);
            return;
        }
        Double medZ = strategyInsightService.medianModifiedZ(rows);
        if (medZ == null) {
            finalizeJobRollupAndGapFlag(jobId, rows);
            return;
        }
        Integer medStBox = strategyInsightService.medianVisibilityStage(rows);
        int medSt = medStBox != null ? medStBox : 1;
        var rollup = strategyInsightService.rollupJob(rows);
        jobPersistenceService.updateJobStrategyRollup(
            jobId,
            rollup.diagnosticMessage(),
            List.copyOf(rollup.recommendedActions()));
        String trendFull = rollup.diagnosticMessage() != null ? rollup.diagnosticMessage() : "";
        String trendClip = trendFull.length() > 420 ? trendFull.substring(0, 420) : trendFull;
        var outlierRows = new ArrayList<AuditHistoryEntity>();
        for (AuditHistoryEntity row : rows) {
            if (row.getModifiedZScore() == null) {
                continue;
            }
            double z = row.getModifiedZScore();
            int st = row.getVisibilityStage() != null ? row.getVisibilityStage() : 10;
            boolean outlier = Math.abs(z - medZ) >= 1.0;
            if (!outlier) {
                var ins = strategyInsightService.keywordInsightRelative(z, medZ, st, medSt);
                jobPersistenceService.updateAuditStrategyInsights(
                    row.getId(),
                    ins.diagnosticMessage(),
                    ins.recommendedActions(),
                    StrategyInsightService.REL_BASELINE_VERSION);
            } else if (plan == SubscriptionPlan.STANDARD) {
                var ins = strategyInsightService.keywordInsightRelative(z, medZ, st, medSt);
                jobPersistenceService.updateAuditStrategyInsights(
                    row.getId(),
                    ins.diagnosticMessage(),
                    ins.recommendedActions(),
                    StrategyInsightService.REL_BASELINE_VERSION);
            } else {
                outlierRows.add(row);
            }
        }
        if (plan.usesProTierFeatures()) {
            if (outlierRows.isEmpty()) {
                jobPersistenceService.markGapAnalysisCompleted(jobId, true);
            } else {
                List<AuditHistoryEntity> snapshot = List.copyOf(outlierRows);
                SCHEDULER.execute(() -> {
                    try {
                        gapBatchSubmissionService.submitGapAnalysisBatch(jobId, snapshot, medZ, medSt, trendClip);
                    } catch (Exception exception) {
                        log.warn("gap_batch_submit_failed jobId={}", jobId, exception);
                        for (AuditHistoryEntity row : snapshot) {
                            Double z = row.getModifiedZScore();
                            var ins = strategyInsightService.fromModifiedZ(z != null ? z : 0.0);
                            jobPersistenceService.updateAuditStrategyInsights(
                                row.getId(),
                                ins.diagnosticMessage(),
                                ins.recommendedActions(),
                                GeoVisibilityCalculatorService.CALCULATION_VERSION);
                        }
                        jobPersistenceService.markGapAnalysisCompleted(jobId, true);
                    }
                });
            }
        } else {
            jobPersistenceService.markGapAnalysisCompleted(jobId, true);
        }
    }

    public void retryGapBatchForJob(UUID jobId) {
        try {
            JobEntity jobEntity = jobPersistenceService.findJobById(jobId);
            if (jobEntity.getJobStatus() != JobStatus.COMPLETED) {
                return;
            }
            SubscriptionPlan applied = jobEntity.getAppliedPlan();
            if (applied == null || !applied.usesProTierFeatures()) {
                return;
            }
            String gapName = jobEntity.getGapAnalysisGeminiJobName();
            if (gapName != null && !gapName.isBlank()) {
                return;
            }
            if (jobEntity.getGapBatchIdempotencyKey() == null) {
                return;
            }
            List<AuditHistoryEntity> rows = jobPersistenceService.findResultsByJobId(jobId);
            if (rows.size() < 2) {
                jobPersistenceService.markGapAnalysisCompleted(jobId, true);
                return;
            }
            Double medZ = strategyInsightService.medianModifiedZ(rows);
            if (medZ == null) {
                jobPersistenceService.markGapAnalysisCompleted(jobId, true);
                return;
            }
            Integer medStBox = strategyInsightService.medianVisibilityStage(rows);
            int medSt = medStBox != null ? medStBox : 1;
            var rollup = strategyInsightService.rollupJob(rows);
            String trendFull = rollup.diagnosticMessage() != null ? rollup.diagnosticMessage() : "";
            String trendClip = trendFull.length() > 420 ? trendFull.substring(0, 420) : trendFull;
            var outlierRows = new ArrayList<AuditHistoryEntity>();
            for (AuditHistoryEntity row : rows) {
                if (row.getModifiedZScore() == null) {
                    continue;
                }
                if (Math.abs(row.getModifiedZScore() - medZ) >= 1.0) {
                    outlierRows.add(row);
                }
            }
            if (outlierRows.isEmpty()) {
                jobPersistenceService.markGapAnalysisCompleted(jobId, true);
                return;
            }
            gapBatchSubmissionService.submitGapAnalysisBatch(jobId, outlierRows, medZ, medSt, trendClip);
        } catch (Exception exception) {
            log.warn("gap_batch_retry_failed jobId={}", jobId, exception);
        }
    }

    private void finalizeJobRollupAndGapFlag(UUID jobId, List<AuditHistoryEntity> rows) {
        var rollup = strategyInsightService.rollupJob(rows);
        jobPersistenceService.updateJobStrategyRollup(
            jobId,
            rollup.diagnosticMessage(),
            List.copyOf(rollup.recommendedActions()));
        jobPersistenceService.markGapAnalysisCompleted(jobId, true);
    }
}
