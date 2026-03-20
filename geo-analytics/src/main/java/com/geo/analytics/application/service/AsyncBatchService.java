package com.geo.analytics.application.service;

import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.enums.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class AsyncBatchService {
    private static final Logger logger = LoggerFactory.getLogger(AsyncBatchService.class);

    private final JobPersistenceService jobPersistenceService;
    private final GeminiBatchExecutorService geminiBatchExecutorService;

    public AsyncBatchService(
            JobPersistenceService jobPersistenceService,
            GeminiBatchExecutorService geminiBatchExecutorService) {
        this.jobPersistenceService = jobPersistenceService;
        this.geminiBatchExecutorService = geminiBatchExecutorService;
    }

    @Scheduled(fixedDelay = 600_000)
    public void submitFileUploadedJobsToBatchApi() {
        logger.info("Batch submission task started");
        List<JobEntity> fileUploadedJobs =
            jobPersistenceService.findJobsByStatus(JobStatus.FILE_UPLOADED);
        logger.info("Found {} FILE_UPLOADED job(s) to submit", fileUploadedJobs.size());
        fileUploadedJobs.forEach(jobEntity -> {
            logger.info("[Job:{}] Processing started", jobEntity.getId());
            logger.info("[Job:{}] Step 1/3 - Delegating to GeminiBatchExecutorService.uploadAndSubmitBatchJob", jobEntity.getId());
            geminiBatchExecutorService.uploadAndSubmitBatchJob(jobEntity)
                .thenRun(() ->
                    logger.info("[Job:{}] Step 3/3 - uploadAndSubmitBatchJob completed (async)", jobEntity.getId()))
                .exceptionally(throwable -> {
                    String detail = throwable.getMessage();
                    if (detail == null || detail.isBlank()) {
                        detail = throwable.getClass().getName();
                    }
                    logger.error("[Job:{}] Batch submission failed - message: {}",
                        jobEntity.getId(), detail, throwable);
                    jobPersistenceService.updateJobStatus(
                        jobEntity.getId(), JobStatus.FAILED,
                        "Async submit wrapper failed: " + throwable.getClass().getSimpleName() + ": " + detail);
                    return null;
                });
            logger.info("[Job:{}] Step 1/3 - Delegation to virtual thread completed (async task dispatched)", jobEntity.getId());
        });
        logger.info("Batch submission task dispatched all jobs");
    }

    @Scheduled(fixedDelay = 300_000)
    public void pollRunningJobsAndProcessCompletedResults() {
        logger.info("Batch polling task started");
        List<JobEntity> runningJobs =
            jobPersistenceService.findJobsByStatus(JobStatus.RUNNING);
        logger.info("Found {} RUNNING job(s) to poll", runningJobs.size());
        runningJobs.forEach(jobEntity -> {
            logger.info("[Job:{}] Polling started", jobEntity.getId());
            geminiBatchExecutorService.pollAndProcessBatchResult(jobEntity)
                .thenRun(() ->
                    logger.info("[Job:{}] pollAndProcessBatchResult completed (async)", jobEntity.getId()))
                .exceptionally(throwable -> {
                    String detail = throwable.getMessage();
                    if (detail == null || detail.isBlank()) {
                        detail = throwable.getClass().getName();
                    }
                    logger.error("[Job:{}] Result polling failed - message: {}",
                        jobEntity.getId(), detail, throwable);
                    jobPersistenceService.updateJobStatus(
                        jobEntity.getId(), JobStatus.FAILED,
                        "Async poll wrapper failed: " + throwable.getClass().getSimpleName() + ": " + detail);
                    return null;
                });
        });
        logger.info("Batch polling task dispatched all jobs");
    }
}
