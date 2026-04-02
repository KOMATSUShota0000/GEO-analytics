package com.geo.analytics.application.service;

import com.geo.analytics.application.port.JobStatusBroadcastPublisher;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.QueryEntity;
import com.geo.analytics.domain.enums.JobStatus;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.infrastructure.ai.GeminiBatchClient;
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

    public GeminiBatchExecutorService(
            GeminiBatchClient geminiBatchClient,
            JobPersistenceService jobPersistenceService,
            JobStatusBroadcastPublisher jobStatusBroadcastPublisher,
            ProjectAuditLifecyclePublisher projectAuditLifecyclePublisher) {
        this.geminiBatchClient = geminiBatchClient;
        this.jobPersistenceService = jobPersistenceService;
        this.jobStatusBroadcastPublisher = jobStatusBroadcastPublisher;
        this.projectAuditLifecyclePublisher = projectAuditLifecyclePublisher;
    }

    @Async
    public CompletableFuture<Void> uploadAndSubmitBatchJob(JobEntity jobEntity) {
        Path jsonlPath = null;
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
            List<BatchQueryLine> batchQueryLines = unprocessedQueryEntities.stream()
                .map(queryEntity -> new BatchQueryLine(queryEntity.getId(), queryEntity.getQueryText()))
                .toList();
            SubscriptionPlan subscriptionPlan =
                Objects.requireNonNullElse(jobEntity.getAppliedPlan(), SubscriptionPlan.STANDARD);
            jsonlPath = geminiBatchClient.writeBatchRequestJsonlToTempFile(
                jobEntity.getBrandName(), batchQueryLines, subscriptionPlan);
            GeminiFileMetadata uploadedFileMetadata = geminiBatchClient.uploadJsonlFile(jsonlPath);
            GeminiBatchJob createdBatchJob = geminiBatchClient.createBatchJob(uploadedFileMetadata);
            jobPersistenceService.updateJobStatusToSubmittedWithGeminiJobName(
                jobEntity.getId(), createdBatchJob.name());
            jobStatusBroadcastPublisher.publish(jobPersistenceService.findJobById(jobEntity.getId()));
        } catch (Exception exception) {
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
}
