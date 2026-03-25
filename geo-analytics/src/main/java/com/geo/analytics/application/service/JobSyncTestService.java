package com.geo.analytics.application.service;
import com.geo.analytics.application.port.JobStatusBroadcastPublisher;
import com.geo.analytics.application.dto.SomScoreData;
import com.geo.analytics.application.dto.SyncVerificationResult;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.QueryEntity;
import com.geo.analytics.domain.enums.JobStatus;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.infrastructure.persistence.JsonbOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
@Service
public class JobSyncTestService {
    private final JobPersistenceService jobPersistenceService;
    private final SyncVerificationService syncVerificationService;
    private final SomScoreParser somScoreParser;
    private final JsonbOperations jsonbOperations;
    private final JobStatusBroadcastPublisher jobStatusBroadcastPublisher;
    public JobSyncTestService(
            JobPersistenceService jobPersistenceService,
            SyncVerificationService syncVerificationService,
            SomScoreParser somScoreParser,
            JsonbOperations jsonbOperations,
            JobStatusBroadcastPublisher jobStatusBroadcastPublisher) {
        this.jobPersistenceService = jobPersistenceService;
        this.syncVerificationService = syncVerificationService;
        this.somScoreParser = somScoreParser;
        this.jsonbOperations = jsonbOperations;
        this.jobStatusBroadcastPublisher = jobStatusBroadcastPublisher;
    }
    @Transactional
    public JobEntity runSingleUnprocessedQuerySyncTest(UUID jobId) {
        JobEntity jobEntity = jobPersistenceService.findJobById(jobId);
        List<QueryEntity> pendingQueryEntities =
            jobPersistenceService.findUnprocessedQueriesByJobId(jobId);
        if (pendingQueryEntities.isEmpty()) {
            throw new IllegalStateException("No unprocessed queries for job: " + jobId);
        }
        QueryEntity queryEntity = pendingQueryEntities.getFirst();
        SubscriptionPlan subscriptionPlan =
            Objects.requireNonNullElse(jobEntity.getSubscriptionPlan(), SubscriptionPlan.STANDARD);
        SyncVerificationResult syncVerificationResult =
            syncVerificationService.verify(jobEntity.getBrandName(), queryEntity.getQueryText(), subscriptionPlan);
        SomScoreData parsedSomScoreData = somScoreParser.parse(syncVerificationResult.rawResponseJson());
        jobPersistenceService.upsertAuditHistoryForJobQuery(
            jobId,
            queryEntity.getId(),
            queryEntity.getQueryText(),
            jsonbOperations.serialize(parsedSomScoreData),
            SomScoreRules.computeFromCitationRank(
                parsedSomScoreData.mentionRank(),
                parsedSomScoreData.brandMentioned()),
            Boolean.TRUE.equals(parsedSomScoreData.brandMentioned()),
            parsedSomScoreData.mentionRank(),
            parsedSomScoreData.overallScore());
        jobPersistenceService.updateJobStatus(jobId, JobStatus.COMPLETED, null);
        JobEntity updatedJobEntity = jobPersistenceService.findJobById(jobId);
        jobStatusBroadcastPublisher.publish(updatedJobEntity);
        return updatedJobEntity;
    }
}
