package com.geo.analytics.application.service;

import com.geo.analytics.application.port.JobStatusBroadcastPublisher;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.QueryEntity;
import com.geo.analytics.domain.enums.JobStatus;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.infrastructure.ai.GeminiBatchClient;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import com.geo.analytics.infrastructure.ai.LlmModelNames;
import com.geo.analytics.infrastructure.ai.dto.BatchQueryLine;
import com.geo.analytics.infrastructure.ai.dto.GeminiBatchJob;
import com.geo.analytics.infrastructure.ai.dto.GeminiFileMetadata;
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
    private final JobPersistenceService jobPersistenceService;
    private final JobStatusBroadcastPublisher jobStatusBroadcastPublisher;
    private final ProjectAuditLifecyclePublisher projectAuditLifecyclePublisher;
    private final PlanBasedQuotaManager planBasedQuotaManager;

    public GeminiBatchExecutorService(
            GeminiBatchClient geminiBatchClient,
            JobPersistenceService jobPersistenceService,
            JobStatusBroadcastPublisher jobStatusBroadcastPublisher,
            ProjectAuditLifecyclePublisher projectAuditLifecyclePublisher,
            PlanBasedQuotaManager planBasedQuotaManager) {
        this.geminiBatchClient = geminiBatchClient;
        this.jobPersistenceService = jobPersistenceService;
        this.jobStatusBroadcastPublisher = jobStatusBroadcastPublisher;
        this.projectAuditLifecyclePublisher = projectAuditLifecyclePublisher;
        this.planBasedQuotaManager = planBasedQuotaManager;
    }

    @Async
    public CompletableFuture<Void> uploadAndSubmitBatchJob(JobEntity jobEntity) {
        Path jsonlPath = null;
        int quotaRefundOnFailure = 0;
        try {
            List<QueryEntity> unprocessedQueryEntities =
                jobPersistenceService.findUnprocessedQueriesByJobId(jobEntity.getId());
            if (unprocessedQueryEntities.isEmpty()) {
                jobPersistenceService.updateJobStatus(jobEntity.getId(), JobStatus.COMPLETED, null);
                JobEntity emptyJobEntity = jobPersistenceService.findJobById(jobEntity.getId());
                jobStatusBroadcastPublisher.publish(emptyJobEntity);
                projectAuditLifecyclePublisher.publishAuditCompleted(emptyJobEntity);
                return CompletableFuture.completedFuture(null);
            }
            quotaRefundOnFailure = unprocessedQueryEntities.size();
            List<BatchQueryLine> batchQueryLines = unprocessedQueryEntities.stream()
                .map(queryEntity -> new BatchQueryLine(queryEntity.getId(), queryEntity.getQueryText()))
                .toList();
            SubscriptionPlan subscriptionPlan =
                Objects.requireNonNullElse(jobEntity.getAppliedPlan(), SubscriptionPlan.STANDARD);
            jsonlPath = geminiBatchClient.writeBatchRequestJsonlToTempFile(
                jobEntity.getBrandName(), batchQueryLines, subscriptionPlan);
            GeminiFileMetadata uploadedFileMetadata = geminiBatchClient.uploadJsonlFile(jsonlPath);
            String selectedModel = chooseModelForProfitGuard(unprocessedQueryEntities, subscriptionPlan);
            GeminiBatchJob createdBatchJob = geminiBatchClient.createBatchJob(uploadedFileMetadata, null, selectedModel);
            jobPersistenceService.updateJobStatusToSubmittedWithGeminiJobName(
                jobEntity.getId(), createdBatchJob.name());
            jobStatusBroadcastPublisher.publish(jobPersistenceService.findJobById(jobEntity.getId()));
        } catch (Exception exception) {
            if (quotaRefundOnFailure > 0) {
                var tid = Objects.requireNonNullElse(jobEntity.getWorkspaceId(), DefaultTenantIds.WORKSPACE_ID);
                planBasedQuotaManager.addTokens(tid, quotaRefundOnFailure);
            }
            jobPersistenceService.updateJobStatus(
                jobEntity.getId(),
                JobStatus.FAILED,
                ExceptionStackTraceText.of(exception));
            jobStatusBroadcastPublisher.publish(jobPersistenceService.findJobById(jobEntity.getId()));
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

    private String chooseModelForProfitGuard(List<QueryEntity> queryEntities, SubscriptionPlan subscriptionPlan) {
        long totalTokens = estimateTotalTokens(queryEntities);
        double unitPrice = planUnitPrice(subscriptionPlan);
        double guardBudget = unitPrice * 0.14;
        double predictedPrimary = estimateApiCost(totalTokens, LlmModelNames.GEMINI_25_PRO);
        if (predictedPrimary > guardBudget) {
            return LlmModelNames.GEMINI_25_FLASH;
        }
        return LlmModelNames.GEMINI_25_PRO;
    }

    private static long estimateTotalTokens(List<QueryEntity> queryEntities) {
        long tokens = 0L;
        for (var q : queryEntities) {
            var text = q.getQueryText();
            if (text == null || text.isBlank()) {
                continue;
            }
            long approx = StrictMath.max(1L, text.strip().length() / 4L);
            tokens += approx;
        }
        return StrictMath.max(tokens, 1L);
    }

    private static double estimateApiCost(long totalTokens, String modelName) {
        double per1k = LlmModelNames.GEMINI_25_FLASH.equals(modelName) ? 0.0012 : 0.006;
        return ((double) totalTokens / 1000.0) * per1k;
    }

    private static double planUnitPrice(SubscriptionPlan subscriptionPlan) {
        return switch (subscriptionPlan) {
            case STANDARD -> 29.0;
            case PRO -> 149.0;
            case EXPERT -> 499.0;
        };
    }
}
