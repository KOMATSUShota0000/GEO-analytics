package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.SgeMentionResult;
import com.geo.analytics.application.port.JobStatusBroadcastPublisher;
import com.geo.analytics.application.port.SgeMeasurementPort;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.QueryEntity;
import com.geo.analytics.domain.entity.SgeResultEntity;
import com.geo.analytics.domain.enums.JobStatus;
import com.geo.analytics.domain.model.QuotaCreditCalculator;
import com.geo.analytics.infrastructure.config.AppProperties;
import com.geo.analytics.infrastructure.ratelimit.SerpApiGlobalRequestGate;
import com.geo.analytics.infrastructure.repository.SgeResultRepository;
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
    private final SgeResultRepository sgeResultRepository;
    private final JobPersistenceService jobPersistenceService;
    private final JobStatusBroadcastPublisher jobStatusBroadcastPublisher;
    private final PlanBasedQuotaManager planBasedQuotaManager;
    private final SerpApiGlobalRequestGate serpApiGlobalRequestGate;
    private final String serpApiKey;
    private AsyncSgeMeasurementService self;

    public AsyncSgeMeasurementService(
            SgeMeasurementPort sgeMeasurementPort,
            SgeResultRepository sgeResultRepository,
            JobPersistenceService jobPersistenceService,
            JobStatusBroadcastPublisher jobStatusBroadcastPublisher,
            PlanBasedQuotaManager planBasedQuotaManager,
            SerpApiGlobalRequestGate serpApiGlobalRequestGate,
            AppProperties appProperties) {
        this.sgeMeasurementPort = sgeMeasurementPort;
        this.sgeResultRepository = sgeResultRepository;
        this.jobPersistenceService = jobPersistenceService;
        this.jobStatusBroadcastPublisher = jobStatusBroadcastPublisher;
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
        log.info("SGE measurement started jobId={} queryCount={}", jobId, queryList.size());
        try {
            if (serpApiKey.isBlank()) {
                throw new IllegalStateException("SerpApi API key is not configured");
            }
            if (queryList.isEmpty()) {
                throw new IllegalStateException("No queries for SGE measurement");
            }
            String brandName = job.getBrandName();
            List<StructuredTaskScope.Subtask<SgeMentionResult>> subtasks = new ArrayList<>(queryList.size());
            try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {
                for (QueryEntity queryEntity : queryList) {
                    final QueryEntity qe = queryEntity;
                    subtasks.add(scope.fork(() -> {
                        try {
                            return serpApiGlobalRequestGate.execute(
                                    () -> sgeMeasurementPort.checkSgeMention(qe.getQueryText(), brandName));
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    }));
                }
                scope.join();
            }
            for (int i = 0; i < queryList.size(); i++) {
                QueryEntity queryEntity = queryList.get(i);
                SgeMentionResult sgeMentionResult = subtasks.get(i).get();
                if (sgeMentionResult == null) {
                    throw new IllegalStateException(
                            "Adapter returned null for queryId=" + queryEntity.getId() + " queryText=" + queryEntity.getQueryText());
                }
                SgeResultEntity sgeResultEntity = new SgeResultEntity();
                sgeResultEntity.setJobId(jobId);
                sgeResultEntity.setWorkspaceId(job.getWorkspaceId());
                sgeResultEntity.setQueryId(queryEntity.getId());
                sgeResultEntity.setQuery(queryEntity.getQueryText());
                sgeResultEntity.setSgeRawResponse(sgeMentionResult.rawResponseJson());
                sgeResultEntity.setSgeMentioned(sgeMentionResult.mentioned());
                sgeResultEntity.setMentionCount(sgeMentionResult.mentionCount());
                sgeResultRepository.save(sgeResultEntity);
            }
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            failJobAndBroadcast(jobId, exception, dailyQuotaRefundOnFailure);
        }
    }

    private void failJobAndBroadcast(UUID jobId, Throwable throwable, int dailyQuotaRefundOnFailure) {
        if (dailyQuotaRefundOnFailure > 0) {
            var je = jobPersistenceService.findJobById(jobId);
            var tid = Objects.requireNonNullElse(je.getWorkspaceId(), DefaultTenantIds.WORKSPACE_ID);
            planBasedQuotaManager.addTokens(
                    tid, (long) dailyQuotaRefundOnFailure * QuotaCreditCalculator.DEPOSIT_PER_KEYWORD);
        }
        String trace = ExceptionStackTraceText.of(throwable);
        jobPersistenceService.updateJobStatus(jobId, JobStatus.FAILED, trace);
        log.error("SGE measurement failed jobId={}", jobId, throwable);
        jobStatusBroadcastPublisher.publish(jobPersistenceService.findJobById(jobId));
    }
}
