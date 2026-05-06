package com.geo.analytics.application.service;

import com.geo.analytics.application.port.JobStatusBroadcastPublisher;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.enums.JobStatus;
import com.geo.analytics.domain.model.QuotaCreditCalculator;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import com.geo.analytics.infrastructure.ai.GeminiBatchClient;
import com.geo.analytics.infrastructure.ai.dto.GeminiBatchJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class AsyncBatchService {
    private static final Logger logger = LoggerFactory.getLogger(AsyncBatchService.class);

    private final BatchPersistenceService batchPersistence;
    private final GeminiBatchExecutorService geminiBatchExecutorService;
    private final GeminiBatchClient geminiBatchClient;
    private final GeminiResultProcessor geminiResultProcessor;
    private final JobStatusBroadcastPublisher jobStatusBroadcastPublisher;
    private final ProjectAuditLifecyclePublisher projectAuditLifecyclePublisher;
    private final GapAnalysisBatchProcessor gapAnalysisBatchProcessor;
    private final GapAnalysisService gapAnalysisService;
    private final PlanBasedQuotaManager planBasedQuotaManager;
    private final JobBenchmarkCaptureService jobBenchmarkCaptureService;

    public AsyncBatchService(
            BatchPersistenceService batchPersistence,
            GeminiBatchExecutorService geminiBatchExecutorService,
            GeminiBatchClient geminiBatchClient,
            GeminiResultProcessor geminiResultProcessor,
            JobStatusBroadcastPublisher jobStatusBroadcastPublisher,
            ProjectAuditLifecyclePublisher projectAuditLifecyclePublisher,
            GapAnalysisBatchProcessor gapAnalysisBatchProcessor,
            GapAnalysisService gapAnalysisService,
            PlanBasedQuotaManager planBasedQuotaManager,
            JobBenchmarkCaptureService jobBenchmarkCaptureService) {
        this.batchPersistence = batchPersistence;
        this.geminiBatchExecutorService = geminiBatchExecutorService;
        this.geminiBatchClient = geminiBatchClient;
        this.geminiResultProcessor = geminiResultProcessor;
        this.jobStatusBroadcastPublisher = jobStatusBroadcastPublisher;
        this.projectAuditLifecyclePublisher = projectAuditLifecyclePublisher;
        this.gapAnalysisBatchProcessor = gapAnalysisBatchProcessor;
        this.gapAnalysisService = gapAnalysisService;
        this.planBasedQuotaManager = planBasedQuotaManager;
        this.jobBenchmarkCaptureService = jobBenchmarkCaptureService;
    }

    private void broadcastJobStatusOverStomp(UUID jobId) {
        JobEntity refreshedJobEntity = batchPersistence.findJobById(jobId);
        jobStatusBroadcastPublisher.publish(refreshedJobEntity);
    }

    @Scheduled(fixedDelay = 10000)
    public void submitFileUploadedJobsToBatchApi() {
        logger.info("Batch submission task started");
        List<JobEntity> fileUploadedJobs =
            batchPersistence.findJobsByStatus(JobStatus.FILE_UPLOADED);
        logger.info("Found {} FILE_UPLOADED job(s) to submit", fileUploadedJobs.size());
        fileUploadedJobs.forEach(jobEntity -> {
            logger.info("[Job:{}] Processing started", jobEntity.getId());
            geminiBatchExecutorService.uploadAndSubmitBatchJob(jobEntity)
                .thenRun(() ->
                    logger.info("[Job:{}] uploadAndSubmitBatchJob completed (async)", jobEntity.getId()))
                .exceptionally(throwable -> {
                    logger.error("[Job:{}] Batch submission async failure",
                        jobEntity.getId(), throwable);
                    batchPersistence.updateJobStatus(jobEntity.getId(), JobStatus.FAILED,
                        ExceptionStackTraceText.of(throwable));
                    broadcastJobStatusOverStomp(jobEntity.getId());
                    return null;
                });
        });
        logger.info("Batch submission task dispatched all jobs");
    }

    @Scheduled(fixedDelay = 10000)
    public void pollRunningJobsAndProcessCompletedResults() {
        logger.info("Batch polling task started");
        List<JobEntity> jobsToPoll = new ArrayList<>();
        jobsToPoll.addAll(batchPersistence.findJobsByStatus(JobStatus.SUBMITTED));
        jobsToPoll.addAll(batchPersistence.findJobsByStatus(JobStatus.RUNNING));
        logger.info("Found {} job(s) to poll (SUBMITTED or RUNNING)", jobsToPoll.size());
        jobsToPoll.forEach(jobEntity -> CompletableFuture.runAsync(() -> {
            try {
                if (jobEntity.getGeminiJobName() == null || jobEntity.getGeminiJobName().isBlank()) {
                    batchPersistence.updateJobStatus(jobEntity.getId(), JobStatus.FAILED,
                        "Missing Gemini job name while polling batch");
                    broadcastJobStatusOverStomp(jobEntity.getId());
                    return;
                }
                GeminiBatchJob currentBatchJobStatus =
                    geminiBatchClient.getBatchJobStatus(jobEntity.getGeminiJobName());
                String batchJobState = currentBatchJobStatus.state();
                if (geminiBatchClient.shouldAwaitNextPoll(batchJobState)) {
                    return;
                }
                if (jobEntity.getJobStatus() == JobStatus.SUBMITTED
                    && geminiBatchClient.isBatchJobActivelyProcessing(batchJobState)) {
                    batchPersistence.updateJobStatus(jobEntity.getId(), JobStatus.RUNNING, null);
                    broadcastJobStatusOverStomp(jobEntity.getId());
                }
                if (!geminiBatchClient.isTerminalState(batchJobState)) {
                    return;
                }
                if (geminiBatchClient.isSucceededState(batchJobState)) {
                    String outputFileName =
                        geminiBatchClient.resolveBatchOutputFileName(currentBatchJobStatus);
                    String outputFileContent =
                        geminiBatchClient.downloadOutputFileContent(outputFileName);
                    geminiResultProcessor.processOutputJsonlAndUpsertResults(jobEntity, outputFileContent);
                    jobBenchmarkCaptureService.capture(jobEntity.getId());
                    batchPersistence.updateJobStatus(jobEntity.getId(), JobStatus.COMPLETED, null);
                    broadcastJobStatusOverStomp(jobEntity.getId());
                    projectAuditLifecyclePublisher.publishAuditCompleted(batchPersistence.findJobById(jobEntity.getId()));
                } else {
                    refundBatchQuotaDeposit(jobEntity);
                    String stateDescription = batchJobState == null ? "null" : batchJobState;
                    batchPersistence.updateJobStatus(jobEntity.getId(), JobStatus.FAILED,
                        "Gemini batch terminal state=" + stateDescription);
                    broadcastJobStatusOverStomp(jobEntity.getId());
                }
            } catch (Exception exception) {
                refundBatchQuotaDeposit(jobEntity);
                try {
                    batchPersistence.updateJobStatus(jobEntity.getId(), JobStatus.FAILED,
                        ExceptionStackTraceText.of(exception));
                    broadcastJobStatusOverStomp(jobEntity.getId());
                } catch (RuntimeException secondary) {
                    logger.error("[Job:{}] persist FAILED after quota refund", jobEntity.getId(), secondary);
                }
            }
        }).exceptionally(throwable -> {
            logger.error("[Job:{}] Batch polling async failure", jobEntity.getId(), throwable);
            refundBatchQuotaDeposit(jobEntity);
            try {
                batchPersistence.updateJobStatus(jobEntity.getId(), JobStatus.FAILED,
                    ExceptionStackTraceText.of(throwable));
                broadcastJobStatusOverStomp(jobEntity.getId());
            } catch (RuntimeException secondary) {
                logger.error("[Job:{}] persist FAILED after quota refund", jobEntity.getId(), secondary);
            }
            return null;
        }));
        logger.info("Batch polling task dispatched all jobs");
    }

    @Scheduled(fixedDelay = 15000)
    public void pollGapAnalysisBatches() {
        logger.info("Gap batch polling task started");
        List<JobEntity> gapJobs = batchPersistence.findJobsPendingGapAnalysisOutput();
        logger.info("Found {} job(s) with pending gap batch output", gapJobs.size());
        gapJobs.forEach(jobEntity -> CompletableFuture.runAsync(() -> {
            try {
                pollOneGapAnalysisJob(jobEntity);
            } catch (Exception exception) {
                logger.error("[Job:{}] Gap batch polling failure", jobEntity.getId(), exception);
                batchPersistence.markGapAnalysisCompleted(jobEntity.getId(), true);
                broadcastJobStatusOverStomp(jobEntity.getId());
            }
        }).exceptionally(throwable -> {
            logger.error("[Job:{}] Gap batch polling async failure", jobEntity.getId(), throwable);
            batchPersistence.markGapAnalysisCompleted(jobEntity.getId(), true);
            broadcastJobStatusOverStomp(jobEntity.getId());
            return null;
        }));
        logger.info("Gap batch polling task dispatched all jobs");
    }

    @Scheduled(fixedDelay = 60000)
    public void retryProGapBatchCreation() {
        List<JobEntity> pending = batchPersistence.findProJobsAwaitingGapBatchCreation();
        pending.forEach(jobEntity -> CompletableFuture.runAsync(() -> {
            try {
                gapAnalysisService.retryGapBatchForJob(jobEntity.getId());
            } catch (Exception exception) {
                logger.warn("[Job:{}] Gap batch retry scheduler failure", jobEntity.getId(), exception);
            }
        }));
    }

    private void refundBatchQuotaDeposit(JobEntity jobEntity) {
        var tid = Objects.requireNonNullElse(jobEntity.getWorkspaceId(), DefaultTenantIds.WORKSPACE_ID);
        long n = batchPersistence.countQueriesByJobId(jobEntity.getId());
        if (n > 0L) {
            planBasedQuotaManager.addTokens(tid, n * QuotaCreditCalculator.DEPOSIT_PER_KEYWORD);
        }
    }

    private void pollOneGapAnalysisJob(JobEntity jobEntity) {
        String gapJobName = jobEntity.getGapAnalysisGeminiJobName();
        if (gapJobName == null || gapJobName.isBlank()) {
            return;
        }
        GeminiBatchJob current = geminiBatchClient.getBatchJobStatus(gapJobName);
        String state = current.state();
        if (geminiBatchClient.shouldAwaitNextPoll(state)) {
            return;
        }
        if (!geminiBatchClient.isTerminalState(state)) {
            return;
        }
        UUID jobId = jobEntity.getId();
        if (geminiBatchClient.isSucceededState(state)) {
            String outputFileName = geminiBatchClient.resolveBatchOutputFileName(current);
            String outputContent = geminiBatchClient.downloadOutputFileContent(outputFileName);
            gapAnalysisBatchProcessor.processOutputJsonl(jobId, outputContent);
            broadcastJobStatusOverStomp(jobId);
            projectAuditLifecyclePublisher.publishAuditCompleted(batchPersistence.findJobById(jobId));
        } else {
            batchPersistence.markGapAnalysisCompleted(jobId, true);
            broadcastJobStatusOverStomp(jobId);
        }
    }
}
