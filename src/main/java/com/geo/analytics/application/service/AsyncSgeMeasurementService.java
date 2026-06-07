package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.SgeMentionResult;
import com.geo.analytics.application.port.SgeMeasurementPort;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.QueryEntity;
import com.geo.analytics.domain.enums.JobStatus;
import com.geo.analytics.domain.model.QuotaCreditCalculator;
import com.geo.analytics.infrastructure.config.AppProperties;
import com.geo.analytics.infrastructure.ratelimit.SerpApiGlobalRequestGate;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.StructuredTaskScope;

@Service
public class AsyncSgeMeasurementService {
    private static final Logger log = LoggerFactory.getLogger(AsyncSgeMeasurementService.class);
    private final SgeMeasurementPort sgeMeasurementPort;
    private final BatchPersistenceService batchPersistence;
    private final PlanBasedQuotaManager planBasedQuotaManager;
    private final SerpApiGlobalRequestGate serpApiGlobalRequestGate;
    private final String serpApiKey;
    private AsyncSgeMeasurementService self;

    public AsyncSgeMeasurementService(
            SgeMeasurementPort sgeMeasurementPort,
            BatchPersistenceService batchPersistence,
            PlanBasedQuotaManager planBasedQuotaManager,
            SerpApiGlobalRequestGate serpApiGlobalRequestGate,
            AppProperties appProperties) {
        this.sgeMeasurementPort = sgeMeasurementPort;
        this.batchPersistence = batchPersistence;
        this.planBasedQuotaManager = planBasedQuotaManager;
        this.serpApiGlobalRequestGate = serpApiGlobalRequestGate;
        String key = appProperties.getSerpapi().getApiKey();
        this.serpApiKey = key != null ? key : "";
    }

    @Autowired
    @Lazy
    void setSelf(AsyncSgeMeasurementService self) {
        this.self = self;
    }

    public void measureSgeForJob(JobEntity job, List<QueryEntity> queries) {
        self.measureSgeForJob(job, queries, 0);
    }

    @Async
    public void measureSgeForJob(JobEntity job, List<QueryEntity> queries, int dailyQuotaRefundOnFailure) {
        Objects.requireNonNull(job, "job");
        UUID jobId = job.getId();
        List<QueryEntity> queryList = Objects.requireNonNull(queries, "queries");
        log.info("AI Overview measurement started jobId={} queryCount={}", jobId, queryList.size());
        if (serpApiKey.isBlank()) {
            log.warn("SERPAPI key is not configured. Degrading SGE measurement to empty placeholders. jobId={} queryCount={}",
                    jobId, queryList.size());
            UUID wid = Objects.requireNonNullElse(job.getWorkspaceId(), DefaultTenantIds.WORKSPACE_ID);
            for (QueryEntity queryEntity : queryList) {
                batchPersistence.insertSgeResult(
                        wid, jobId, queryEntity.getId(),
                        queryEntity.getQueryText(),
                        "{}",
                        false,
                        0);
            }
            return;
        }
        try {
            if (queryList.isEmpty()) {
                log.warn("Skipping AI Overview measurement: empty query list jobId={}", jobId);
                return;
            }
            String brandName = job.getBrandName();
            List<StructuredTaskScope.Subtask<SgeMentionResult>> subtasks = new ArrayList<>(queryList.size());
            // SerpAPI への各クエリ呼び出しは awaitAll で待ち、個別失敗ではジョブ全体を落とさない。
            // 1件の接続タイムアウト等で解析全体が FAILED になるのを防ぎ（APIキー未設定時の降格と同じ思想）、
            // 失敗クエリの SoM のみ空プレースホルダ（mentioned=false / count=0）へ降格して解析を完走させる。
            try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.<SgeMentionResult>awaitAll())) {
                for (QueryEntity queryEntity : queryList) {
                    final QueryEntity qe = queryEntity;
                    subtasks.add(scope.fork(
                            () -> serpApiGlobalRequestGate.execute(
                                    () -> sgeMeasurementPort.checkSgeMention(qe.getQueryText(), brandName))));
                }
                scope.join();
            }
            UUID wid = Objects.requireNonNullElse(job.getWorkspaceId(), DefaultTenantIds.WORKSPACE_ID);
            for (int i = 0; i < queryList.size(); i++) {
                QueryEntity queryEntity = queryList.get(i);
                StructuredTaskScope.Subtask<SgeMentionResult> subtask = subtasks.get(i);
                SgeMentionResult sgeMentionResult =
                        subtask.state() == StructuredTaskScope.Subtask.State.SUCCESS ? subtask.get() : null;
                if (sgeMentionResult == null) {
                    log.warn(
                            "SGE measurement degraded to empty (SerpAPI失敗/timeout等) jobId={} queryId={} state={}",
                            jobId, queryEntity.getId(), subtask.state());
                    batchPersistence.insertSgeResult(
                            wid, jobId, queryEntity.getId(), queryEntity.getQueryText(), "{}", false, 0);
                    continue;
                }
                batchPersistence.insertSgeResult(
                    wid, jobId, queryEntity.getId(),
                    queryEntity.getQueryText(),
                    sgeMentionResult.rawResponseJson(),
                    sgeMentionResult.mentioned(),
                    sgeMentionResult.mentionCount());
            }
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            failJobAfterMeasurementError(jobId, exception, dailyQuotaRefundOnFailure);
        }
    }

    private void failJobAfterMeasurementError(UUID jobId, Throwable throwable, int dailyQuotaRefundOnFailure) {
        if (dailyQuotaRefundOnFailure > 0) {
            var je = batchPersistence.findJobById(jobId);
            var tid = Objects.requireNonNullElse(je.getWorkspaceId(), DefaultTenantIds.WORKSPACE_ID);
            planBasedQuotaManager.addTokens(
                    tid, (long) dailyQuotaRefundOnFailure * QuotaCreditCalculator.DEPOSIT_PER_KEYWORD);
        }
        String trace = ExceptionStackTraceText.of(throwable);
        batchPersistence.updateJobStatus(jobId, JobStatus.FAILED, trace);
        log.error("AI Overview measurement failed jobId={}", jobId, throwable);
    }
}
