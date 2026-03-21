package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.JobStompStatusPayload;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.enums.JobStatus;
import com.geo.analytics.infrastructure.ai.GeminiBatchClient;
import com.geo.analytics.infrastructure.ai.dto.GeminiBatchJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class AsyncBatchService {
    private static final Logger logger = LoggerFactory.getLogger(AsyncBatchService.class);

    private final JobPersistenceService jobPersistenceService;
    private final GeminiBatchExecutorService geminiBatchExecutorService;
    private final GeminiBatchClient geminiBatchClient;
    private final GeminiResultProcessor geminiResultProcessor;
    private final SimpMessagingTemplate simpMessagingTemplate;

    public AsyncBatchService(
            JobPersistenceService jobPersistenceService,
            GeminiBatchExecutorService geminiBatchExecutorService,
            GeminiBatchClient geminiBatchClient,
            GeminiResultProcessor geminiResultProcessor,
            SimpMessagingTemplate simpMessagingTemplate) {
        this.jobPersistenceService = jobPersistenceService;
        this.geminiBatchExecutorService = geminiBatchExecutorService;
        this.geminiBatchClient = geminiBatchClient;
        this.geminiResultProcessor = geminiResultProcessor;
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    private void broadcastJobStatusOverStomp(UUID jobId) {
        JobEntity refreshedJobEntity = jobPersistenceService.findJobById(jobId);
        JobStompStatusPayload payload = JobStompStatusPayload.from(refreshedJobEntity);
        simpMessagingTemplate.convertAndSend("/topic/jobs/" + jobId, payload);
    }

    @Scheduled(fixedDelay = 10000)
    public void submitFileUploadedJobsToBatchApi() {
        logger.info("Batch submission task started");
        List<JobEntity> fileUploadedJobs =
            jobPersistenceService.findJobsByStatus(JobStatus.FILE_UPLOADED);
        logger.info("Found {} FILE_UPLOADED job(s) to submit", fileUploadedJobs.size());
        fileUploadedJobs.forEach(jobEntity -> {
            logger.info("[Job:{}] Processing started", jobEntity.getId());
            geminiBatchExecutorService.uploadAndSubmitBatchJob(jobEntity)
                .thenRun(() ->
                    logger.info("[Job:{}] uploadAndSubmitBatchJob completed (async)", jobEntity.getId()))
                .exceptionally(throwable -> {
                    logger.error("[Job:{}] Batch submission async failure",
                        jobEntity.getId(), throwable);
                    jobPersistenceService.updateJobStatus(
                        jobEntity.getId(),
                        JobStatus.FAILED,
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
        jobsToPoll.addAll(jobPersistenceService.findJobsByStatus(JobStatus.SUBMITTED));
        jobsToPoll.addAll(jobPersistenceService.findJobsByStatus(JobStatus.RUNNING));
        logger.info("Found {} job(s) to poll (SUBMITTED or RUNNING)", jobsToPoll.size());
        jobsToPoll.forEach(jobEntity -> CompletableFuture.runAsync(() -> {
            try {
                if (jobEntity.getGeminiJobName() == null || jobEntity.getGeminiJobName().isBlank()) {
                    jobPersistenceService.updateJobStatus(
                        jobEntity.getId(),
                        JobStatus.FAILED,
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
                    jobPersistenceService.updateJobStatus(jobEntity.getId(), JobStatus.RUNNING, null);
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
                    jobPersistenceService.updateJobStatus(jobEntity.getId(), JobStatus.COMPLETED, null);
                    broadcastJobStatusOverStomp(jobEntity.getId());
                } else {
                    String stateDescription = batchJobState == null ? "null" : batchJobState;
                    jobPersistenceService.updateJobStatus(
                        jobEntity.getId(),
                        JobStatus.FAILED,
                        "Gemini batch terminal state=" + stateDescription);
                    broadcastJobStatusOverStomp(jobEntity.getId());
                }
            } catch (Exception exception) {
                jobPersistenceService.updateJobStatus(
                    jobEntity.getId(),
                    JobStatus.FAILED,
                    ExceptionStackTraceText.of(exception));
                broadcastJobStatusOverStomp(jobEntity.getId());
            }
        }).exceptionally(throwable -> {
            logger.error("[Job:{}] Batch polling async failure", jobEntity.getId(), throwable);
            jobPersistenceService.updateJobStatus(
                jobEntity.getId(),
                JobStatus.FAILED,
                ExceptionStackTraceText.of(throwable));
            broadcastJobStatusOverStomp(jobEntity.getId());
            return null;
        }));
        logger.info("Batch polling task dispatched all jobs");
    }
}
