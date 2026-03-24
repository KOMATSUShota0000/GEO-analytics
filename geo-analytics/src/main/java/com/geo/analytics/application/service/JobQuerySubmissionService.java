package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.SomScoreData;
import com.geo.analytics.application.dto.SyncVerificationResult;
import com.geo.analytics.application.port.JobStatusBroadcastPublisher;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.QueryEntity;
import com.geo.analytics.domain.entity.ResultEntity;
import com.geo.analytics.domain.enums.JobStatus;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.exception.ThresholdExceededException;
import com.geo.analytics.infrastructure.persistence.JsonbOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class JobQuerySubmissionService {
    private static final Logger log = LoggerFactory.getLogger(JobQuerySubmissionService.class);

    private final JobPersistenceService jobPersistenceService;
    private final AsyncSgeMeasurementService asyncSgeMeasurementService;
    private final SyncVerificationService syncVerificationService;
    private final SomScoreParser somScoreParser;
    private final JsonbOperations jsonbOperations;
    private final JobStatusBroadcastPublisher jobStatusBroadcastPublisher;
    private final int realtimeThreshold;

    public JobQuerySubmissionService(
            JobPersistenceService jobPersistenceService,
            AsyncSgeMeasurementService asyncSgeMeasurementService,
            SyncVerificationService syncVerificationService,
            SomScoreParser somScoreParser,
            JsonbOperations jsonbOperations,
            JobStatusBroadcastPublisher jobStatusBroadcastPublisher,
            @Value("${app.ai.realtime-threshold:10}") int realtimeThreshold) {
        this.jobPersistenceService = jobPersistenceService;
        this.asyncSgeMeasurementService = asyncSgeMeasurementService;
        this.syncVerificationService = syncVerificationService;
        this.somScoreParser = somScoreParser;
        this.jsonbOperations = jsonbOperations;
        this.jobStatusBroadcastPublisher = jobStatusBroadcastPublisher;
        this.realtimeThreshold = realtimeThreshold;
    }

    public void submitQueries(UUID jobId, List<String> queryTexts, SubscriptionPlan plan) {
        int keywordCount = queryTexts.size();

        if (keywordCount > realtimeThreshold) {
            if (plan == SubscriptionPlan.STANDARD) {
                throw new ThresholdExceededException(realtimeThreshold);
            }
            jobPersistenceService.registerQueriesAndTransitionToFileUploaded(jobId, queryTexts);
            startDeepAnalysisBatchPlaceholder(jobId);
            JobEntity jobEntity = jobPersistenceService.findJobById(jobId);
            List<QueryEntity> queryEntities = jobPersistenceService.findQueriesByJobId(jobId);
            asyncSgeMeasurementService.measureSgeForJob(jobEntity, queryEntities);
            return;
        }

        jobPersistenceService.registerQueriesAndTransitionToRealtimeProcessing(jobId, queryTexts);
        executeImmediateParallelProcessing(jobId);
        JobEntity completedJob = jobPersistenceService.findJobById(jobId);
        List<QueryEntity> queryEntities = jobPersistenceService.findQueriesByJobId(jobId);
        asyncSgeMeasurementService.measureSgeForJob(completedJob, queryEntities);
    }

    private void startDeepAnalysisBatchPlaceholder(UUID jobId) {
        log.debug("deep analysis batch placeholder jobId={}", jobId);
    }

    private void executeImmediateParallelProcessing(UUID jobId) {
        JobEntity jobEntity = jobPersistenceService.findJobById(jobId);
        String brandName = jobEntity.getBrandName();
        List<QueryEntity> queryEntities = jobPersistenceService.findQueriesByJobId(jobId);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (QueryEntity queryEntity : queryEntities) {
                futures.add(executor.submit(() -> processOneQueryRealtime(jobId, brandName, queryEntity)));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interruptedException);
                } catch (ExecutionException executionException) {
                    Throwable cause = executionException.getCause() != null
                        ? executionException.getCause()
                        : executionException;
                    log.error("immediate mode task failed jobId={}", jobId, cause);
                }
            }
        }
        jobPersistenceService.updateJobStatus(jobId, JobStatus.COMPLETED, null);
        jobStatusBroadcastPublisher.publish(jobPersistenceService.findJobById(jobId));
    }

    private void processOneQueryRealtime(UUID jobId, String brandName, QueryEntity queryEntity) {
        try {
            SyncVerificationResult syncVerificationResult =
                syncVerificationService.verify(brandName, queryEntity.getQueryText());
            SomScoreData parsedSomScoreData = somScoreParser.parse(syncVerificationResult.rawResponseJson());
            ResultEntity resultEntity = new ResultEntity();
            resultEntity.setJobId(jobId);
            resultEntity.setQuery(queryEntity.getQueryText());
            resultEntity.setRawResponse(jsonbOperations.serialize(parsedSomScoreData));
            resultEntity.setSomScore(
                SomScoreRules.computeFromCitationRank(
                    parsedSomScoreData.mentionRank(),
                    parsedSomScoreData.brandMentioned()));
            resultEntity.setBrandMentioned(Boolean.TRUE.equals(parsedSomScoreData.brandMentioned()));
            resultEntity.setMentionRank(parsedSomScoreData.mentionRank());
            jobPersistenceService.upsertResultAndMarkQueryProcessed(resultEntity, queryEntity.getId());
        } catch (Exception exception) {
            log.error(
                "immediate mode query failed jobId={} queryId={}",
                jobId,
                queryEntity.getId(),
                exception);
        }
    }
}
