package com.geo.analytics.application.service;

import com.geo.analytics.application.port.JobStatusBroadcastPublisher;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.QueryEntity;
import com.geo.analytics.domain.enums.JobStatus;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.model.QuotaCreditCalculator;
import com.geo.analytics.infrastructure.ai.GeminiBatchClient;
import com.geo.analytics.infrastructure.ai.JobPromptContextFormatter;
import com.geo.analytics.infrastructure.ai.LlmModelNames;
import com.geo.analytics.infrastructure.ai.dto.BatchQueryLine;
import com.geo.analytics.infrastructure.ai.dto.GeminiBatchJob;
import com.geo.analytics.infrastructure.ai.dto.GeminiFileMetadata;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Service
public class GeminiBatchExecutorService {
    private final GeminiBatchClient geminiBatchClient;
    private final BatchPersistenceService batchPersistence;
    private final JobStatusBroadcastPublisher jobStatusBroadcastPublisher;
    private final ProjectAuditLifecyclePublisher projectAuditLifecyclePublisher;
    private final PlanBasedQuotaManager planBasedQuotaManager;
    private final JobBenchmarkCaptureService jobBenchmarkCaptureService;
    private final AiRubricAuditService aiRubricAuditService;

    public GeminiBatchExecutorService(
            GeminiBatchClient geminiBatchClient,
            BatchPersistenceService batchPersistence,
            JobStatusBroadcastPublisher jobStatusBroadcastPublisher,
            ProjectAuditLifecyclePublisher projectAuditLifecyclePublisher,
            PlanBasedQuotaManager planBasedQuotaManager,
            JobBenchmarkCaptureService jobBenchmarkCaptureService,
            AiRubricAuditService aiRubricAuditService) {
        this.geminiBatchClient = geminiBatchClient;
        this.batchPersistence = batchPersistence;
        this.jobStatusBroadcastPublisher = jobStatusBroadcastPublisher;
        this.projectAuditLifecyclePublisher = projectAuditLifecyclePublisher;
        this.planBasedQuotaManager = planBasedQuotaManager;
        this.jobBenchmarkCaptureService = jobBenchmarkCaptureService;
        this.aiRubricAuditService = aiRubricAuditService;
    }

    @Async
    public CompletableFuture<Void> uploadAndSubmitBatchJob(JobEntity jobEntity) {
        Path jsonlPath = null;
        int quotaRefundOnFailure = 0;
        try {
            List<QueryEntity> unprocessedQueryEntities =
                batchPersistence.findUnprocessedQueriesByJobId(jobEntity.getId());
            if (unprocessedQueryEntities.isEmpty()) {
                jobBenchmarkCaptureService.capture(jobEntity.getId());
                aiRubricAuditService.runMultiDomainAuditForCompletedJob(jobEntity.getId());
                batchPersistence.updateJobStatus(jobEntity.getId(), JobStatus.COMPLETED, null);
                JobEntity emptyJobEntity = batchPersistence.findJobById(jobEntity.getId());
                jobStatusBroadcastPublisher.publish(emptyJobEntity);
                projectAuditLifecyclePublisher.publishAuditCompleted(emptyJobEntity);
                return CompletableFuture.completedFuture(null);
            }
            quotaRefundOnFailure = unprocessedQueryEntities.size() * QuotaCreditCalculator.DEPOSIT_PER_KEYWORD;
            List<BatchQueryLine> batchQueryLines = unprocessedQueryEntities.stream()
                .map(queryEntity -> new BatchQueryLine(queryEntity.getId(), queryEntity.getQueryText()))
                .toList();
            SubscriptionPlan subscriptionPlan =
                Objects.requireNonNullElse(jobEntity.getAppliedPlan(), SubscriptionPlan.STANDARD);
            String jobPromptContext = JobPromptContextFormatter.format(jobEntity);
            jsonlPath = geminiBatchClient.writeBatchRequestJsonlToTempFile(
                jobEntity.getBrandName(), batchQueryLines, subscriptionPlan, jobPromptContext);
            GeminiFileMetadata uploadedFileMetadata = geminiBatchClient.uploadJsonlFile(jsonlPath);
            String selectedModel = geminiBatchModelForSubscriptionPlan(subscriptionPlan);
            GeminiBatchJob createdBatchJob = geminiBatchClient.createBatchJob(uploadedFileMetadata, null, selectedModel);
            batchPersistence.updateJobStatusToSubmittedWithGeminiJobName(
                jobEntity.getId(), createdBatchJob.name());
            jobStatusBroadcastPublisher.publish(batchPersistence.findJobById(jobEntity.getId()));
        } catch (Exception exception) {
            if (quotaRefundOnFailure > 0) {
                var tid = Objects.requireNonNullElse(jobEntity.getWorkspaceId(), DefaultTenantIds.WORKSPACE_ID);
                planBasedQuotaManager.addTokens(tid, quotaRefundOnFailure);
            }
            batchPersistence.updateJobStatus(
                jobEntity.getId(),
                JobStatus.FAILED,
                ExceptionStackTraceText.of(exception));
            jobStatusBroadcastPublisher.publish(batchPersistence.findJobById(jobEntity.getId()));
        } finally {
            if (jsonlPath != null) {
                try {
                    Files.deleteIfExists(jsonlPath);
                } catch (IOException ignored) {
                }
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    /** 上位プランのみ Pro、その他は Flash に一意マッピング（コスト推定によるダウングレードなし）。 */
    private static String geminiBatchModelForSubscriptionPlan(SubscriptionPlan subscriptionPlan) {
        SubscriptionPlan p = subscriptionPlan != null ? subscriptionPlan : SubscriptionPlan.STANDARD;
        return p.usesProTierFeatures() ? LlmModelNames.GEMINI_25_PRO : LlmModelNames.GEMINI_25_FLASH;
    }
}
