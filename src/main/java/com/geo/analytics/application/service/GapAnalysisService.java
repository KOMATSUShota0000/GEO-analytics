package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.DebateJobFacts;
import com.geo.analytics.application.dto.StrategyInsight;
import com.geo.analytics.domain.entity.AuditHistoryEntity;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.enums.AdviceSource;
import com.geo.analytics.domain.enums.DebateStatus;
import com.geo.analytics.domain.enums.JobStatus;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.model.DebateUtterance;
import com.geo.analytics.domain.model.MinorityReport;
import com.geo.analytics.domain.model.RemediationTask;
import com.geo.analytics.domain.model.RemediationTaskOrder;
import com.geo.analytics.domain.model.RoadmapItem;
import com.geo.analytics.domain.service.GeoVisibilityCalculatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.lang.StrictMath;
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
    private final BatchPersistenceService batchPersistence;
    private final StrategyInsightService strategyInsightService;
    private final GapBatchSubmissionService gapBatchSubmissionService;

    public GapAnalysisService(
            BatchPersistenceService batchPersistence,
            StrategyInsightService strategyInsightService,
            GapBatchSubmissionService gapBatchSubmissionService) {
        this.batchPersistence = batchPersistence;
        this.strategyInsightService = strategyInsightService;
        this.gapBatchSubmissionService = gapBatchSubmissionService;
    }

    /**
     * Why: 議論中の印は、裏の処理を起こす前にここで付ける。呼び出し元は直後に解析を完了にするため、
     * 画面が「完了」を受け取った時点で、議論がまだ終わっていないことを読めるようにする（#197）。
     */
    public void scheduleForJob(UUID jobId) {
        try {
            batchPersistence.beginDebate(jobId);
        } catch (RuntimeException exception) {
            log.warn("debate_begin_failed jobId={}", jobId, exception);
        }
        SCHEDULER.execute(() -> {
            try {
                runForJob(jobId);
            } catch (Exception exception) {
                log.warn("gap_analysis_failed jobId={}", jobId, exception);
                try {
                    batchPersistence.abandonDebate(jobId);
                } catch (RuntimeException abandonFailure) {
                    log.warn("debate_abandon_failed jobId={}", jobId, abandonFailure);
                }
            }
        });
    }

    private void finishDebateQuietly(UUID jobId, DebateStatus status) {
        try {
            batchPersistence.finishDebate(jobId, status);
        } catch (RuntimeException exception) {
            log.warn("debate_finish_failed jobId={} status={}", jobId, status, exception);
        }
    }

    /**
     * 議論の発言を、できた順に保存する（#197）。発言者の呼び出しはひとつのスレッドで順に行われる。
     *
     * <p>Why: 保存に失敗しても議論と総合診断は止めない。画面に出る発言が欠けるだけで、解析は成立する。
     */
    private final class JobDebateRecorder implements DebateRecorder {
        private final UUID jobId;
        private final UUID organizationId;
        private int seq;
        private boolean failed;

        JobDebateRecorder(UUID jobId, UUID organizationId) {
            this.jobId = jobId;
            this.organizationId = organizationId;
        }

        @Override
        public void spoke(DebateUtterance utterance) {
            seq++;
            try {
                batchPersistence.insertDebateUtterance(jobId, organizationId, seq, utterance);
            } catch (RuntimeException exception) {
                log.warn("debate_utterance_save_failed jobId={} seq={}", jobId, seq, exception);
            }
        }

        @Override
        public void failed() {
            failed = true;
        }

        DebateStatus outcome() {
            if (failed) {
                return DebateStatus.FAILED;
            }
            return seq > 0 ? DebateStatus.COMPLETED : DebateStatus.SKIPPED;
        }
    }

    /**
     * Why: 改善タスクは議論より先に生成・保存されている（{@code JobQuerySubmissionService} の呼び出し順）。
     * 議論に渡してロードマップを改善タスクの時間割にする（#141）。タスクを保存した監査履歴と同じ1件を選ぶため、
     * 選択規則は {@link LatestAuditHistorySelector} に揃える。
     */
    private List<RemediationTask> loadTasksForDebate(List<AuditHistoryEntity> rows) {
        AuditHistoryEntity latest = LatestAuditHistorySelector.pickLatest(rows);
        if (latest == null || latest.getId() == null) {
            return List.of();
        }
        return RemediationTaskOrder.sort(batchPersistence.findRemediationTasks(latest.getId()));
    }

    private static DebateJobFacts debateJobFactsOf(JobEntity job) {
        return new DebateJobFacts(
                job.getBusinessSummary(), job.getTargetAudience(), job.getFocusPoints(), job.getSelfRubricAuditJson());
    }

    public void runForJob(UUID jobId) {
        JobEntity jobEntity = batchPersistence.findJobById(jobId);
        SubscriptionPlan plan = Objects.requireNonNullElse(jobEntity.getAppliedPlan(), SubscriptionPlan.STANDARD);
        List<AuditHistoryEntity> rows = batchPersistence.findResultsByJobId(jobId);
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
        // AI 議論駆動アドバイス: project コンテキストとプランを渡す。
        // LLM 失敗時は StrategyInsightService 内でテンプレフォールバックされる。
        var projectContext = jobEntity.getProjectId() != null
                ? batchPersistence.findProjectAdviceContext(jobEntity.getProjectId()).orElse(null)
                : null;
        StrategyInsight rollup;
        String adviceSource;
        // Why: 議論の成果物（マイノリティ・レポート #80 / ロードマップ #77）は議論が成立したときにしか
        //      存在しない。テンプレへ落ちた回は null を渡し、前回の議論結果を空配列で潰さない。
        //      議論が成立して0件だった場合は空配列で上書きする。
        List<MinorityReport> minorityReports = null;
        List<RoadmapItem> roadmapItems = null;
        DebateStatus debateStatus = DebateStatus.SKIPPED;
        if (projectContext != null) {
            var recorder = new JobDebateRecorder(jobId, projectContext.organizationId());
            var rollupWithSource =
                    strategyInsightService.rollupJobWithSource(
                            rows, projectContext, plan, loadTasksForDebate(rows), debateJobFactsOf(jobEntity),
                            recorder);
            debateStatus = recorder.outcome();
            rollup = rollupWithSource.insight();
            adviceSource = rollupWithSource.source().name();
            if (rollupWithSource.source() == AdviceSource.AI) {
                minorityReports = rollupWithSource.minorityReports();
                roadmapItems = rollupWithSource.roadmapItems();
            }
        } else {
            rollup = strategyInsightService.rollupJob(rows);
            adviceSource = null;
        }
        batchPersistence.updateJobStrategyRollup(
            jobId,
            rollup.diagnosticMessage(),
            List.copyOf(rollup.recommendedActions()),
            adviceSource,
            minorityReports,
            roadmapItems);
        // Why: 終わりの印は総合診断を書いたあとに付ける。画面は印を見て問い合わせをやめるため、逆にすると
        //      総合診断の無いまま問い合わせが止まる（#197 / #198）。
        finishDebateQuietly(jobId, debateStatus);
        String trendFull = rollup.diagnosticMessage() != null ? rollup.diagnosticMessage() : "";
        String trendClip = trendFull.length() > 420 ? trendFull.substring(0, 420) : trendFull;
        var outlierRows = new ArrayList<AuditHistoryEntity>();
        for (AuditHistoryEntity row : rows) {
            if (row.getModifiedZScore() == null) {
                continue;
            }
            double z = row.getModifiedZScore();
            int st = row.getVisibilityStage() != null ? row.getVisibilityStage() : 10;
            boolean outlier = StrictMath.abs(z - medZ) >= 1.0;
            if (!outlier) {
                var ins = strategyInsightService.keywordInsightRelative(z, medZ, st, medSt);
                batchPersistence.updateAuditStrategyInsights(
                    row.getId(),
                    ins.diagnosticMessage(),
                    ins.recommendedActions(),
                    StrategyInsightService.REL_BASELINE_VERSION);
            } else if (plan == SubscriptionPlan.STANDARD) {
                var ins = strategyInsightService.keywordInsightRelative(z, medZ, st, medSt);
                batchPersistence.updateAuditStrategyInsights(
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
                batchPersistence.markGapAnalysisCompleted(jobId, true);
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
                            batchPersistence.updateAuditStrategyInsights(
                                row.getId(),
                                ins.diagnosticMessage(),
                                ins.recommendedActions(),
                                GeoVisibilityCalculatorService.CALCULATION_VERSION);
                        }
                        batchPersistence.markGapAnalysisCompleted(jobId, true);
                    }
                });
            }
        } else {
            batchPersistence.markGapAnalysisCompleted(jobId, true);
        }
    }

    public void retryGapBatchForJob(UUID jobId) {
        try {
            JobEntity jobEntity = batchPersistence.findJobById(jobId);
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
            List<AuditHistoryEntity> rows = batchPersistence.findResultsByJobId(jobId);
            if (rows.size() < 2) {
                batchPersistence.markGapAnalysisCompleted(jobId, true);
                return;
            }
            Double medZ = strategyInsightService.medianModifiedZ(rows);
            if (medZ == null) {
                batchPersistence.markGapAnalysisCompleted(jobId, true);
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
                if (StrictMath.abs(row.getModifiedZScore() - medZ) >= 1.0) {
                    outlierRows.add(row);
                }
            }
            if (outlierRows.isEmpty()) {
                batchPersistence.markGapAnalysisCompleted(jobId, true);
                return;
            }
            gapBatchSubmissionService.submitGapAnalysisBatch(jobId, outlierRows, medZ, medSt, trendClip);
        } catch (Exception exception) {
            log.warn("gap_batch_retry_failed jobId={}", jobId, exception);
        }
    }

    private void finalizeJobRollupAndGapFlag(UUID jobId, List<AuditHistoryEntity> rows) {
        // 早期確定パス（rows<2 / medZ==null）もテンプレ確定のため TEMPLATE_FALLBACK を明示記録（F-3.1）。
        var rollup = strategyInsightService.rollupJob(rows);
        batchPersistence.updateJobStrategyRollup(
            jobId,
            rollup.diagnosticMessage(),
            List.copyOf(rollup.recommendedActions()),
            AdviceSource.TEMPLATE_FALLBACK.name());
        finishDebateQuietly(jobId, DebateStatus.SKIPPED);
        batchPersistence.markGapAnalysisCompleted(jobId, true);
    }
}
