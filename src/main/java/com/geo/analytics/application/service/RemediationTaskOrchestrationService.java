package com.geo.analytics.application.service;

import com.geo.analytics.domain.entity.AuditHistoryEntity;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 完了したジョブのルーブリック監査結果から未達基準を洗い出し、改善タスクを生成して永続化する。
 *
 * <p>Why: 生成の連鎖（ギャップ判定 → タスク生成 → 永続化 → API → UI）は個々の部品が実装済みでありながら
 * 起点が一度も呼ばれておらず、UI の改善タスクパネルが常に非表示だった。呼び出し順の制約（ルーブリック監査の
 * 行が永続化された後でしか判定できない）を1箇所に閉じ込め、解析フローからは1行で起動できるようにする。
 */
@Service
public class RemediationTaskOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(RemediationTaskOrchestrationService.class);

    private final JobPersistenceService jobPersistenceService;
    private final RubricGapAnalysisService rubricGapAnalysisService;
    private final AiRemediationService aiRemediationService;

    public RemediationTaskOrchestrationService(
            JobPersistenceService jobPersistenceService,
            RubricGapAnalysisService rubricGapAnalysisService,
            AiRemediationService aiRemediationService) {
        this.jobPersistenceService = jobPersistenceService;
        this.rubricGapAnalysisService = rubricGapAnalysisService;
        this.aiRemediationService = aiRemediationService;
    }

    /**
     * Why: 改善タスクの生成失敗で解析全体を落とさない。ベンチマーク取得・ルーブリック監査と同じ縮退方針で、
     * 失敗しても解析は COMPLETED まで進める（タスクが欠けるだけでスコアは成立する）。
     */
    public void generateForCompletedJob(UUID jobId) {
        if (jobId == null) {
            return;
        }
        try {
            var aggregate = jobPersistenceService.findJobAnalysisAggregate(jobId);
            UUID projectId = aggregate.job() != null ? aggregate.job().getProjectId() : null;
            if (projectId == null) {
                return;
            }
            AuditHistoryEntity latest = LatestAuditHistorySelector.pickLatest(aggregate.auditHistories());
            if (latest == null || latest.getId() == null) {
                return;
            }
            List<String> gapCriterionIds = rubricGapAnalysisService.identifyGaps(latest.getId());
            if (gapCriterionIds.isEmpty()) {
                log.info(
                        "remediation_tasks_skipped_no_gaps jobId={} auditHistoryId={}",
                        jobId,
                        latest.getId());
                return;
            }
            var tasks = aiRemediationService.generateTasks(projectId, latest.getId(), gapCriterionIds);
            log.info(
                    "remediation_tasks_generated jobId={} auditHistoryId={} gaps={} tasks={}",
                    jobId,
                    latest.getId(),
                    gapCriterionIds.size(),
                    tasks.size());
        } catch (RuntimeException runtimeException) {
            log.warn("remediation_tasks_generation_failed jobId={}", jobId, runtimeException);
        }
    }
}
