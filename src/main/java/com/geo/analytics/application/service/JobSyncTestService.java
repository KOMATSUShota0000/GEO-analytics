package com.geo.analytics.application.service;
import com.geo.analytics.application.dto.ConsultantOutputData;
import com.geo.analytics.application.dto.SyncVerificationResult;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.QueryEntity;
import com.geo.analytics.domain.enums.JobStatus;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.infrastructure.persistence.JsonbOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
@Service
public class JobSyncTestService {
    private static final Logger log = LoggerFactory.getLogger(JobSyncTestService.class);
    private final JobPersistenceService jobPersistenceService;
    private final SyncVerificationService syncVerificationService;
    private final SomScoreParser somScoreParser;
    private final JsonbOperations jsonbOperations;
    public JobSyncTestService(
            JobPersistenceService jobPersistenceService,
            SyncVerificationService syncVerificationService,
            SomScoreParser somScoreParser,
            JsonbOperations jsonbOperations) {
        this.jobPersistenceService = jobPersistenceService;
        this.syncVerificationService = syncVerificationService;
        this.somScoreParser = somScoreParser;
        this.jsonbOperations = jsonbOperations;
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
            Objects.requireNonNullElse(jobEntity.getAppliedPlan(), SubscriptionPlan.STANDARD);
        SyncVerificationResult syncVerificationResult = syncVerificationService.verify(
            jobEntity.getBrandName(),
            queryEntity.getQueryText(),
            subscriptionPlan,
            null,
            null,
            jobEntity.getBrandName());
        String rawJson = syncVerificationResult.rawResponseJson();
        String serializedConsultant;
        try {
            ConsultantOutputData consultantOutputData = somScoreParser.parseConsultantOutput(rawJson);
            serializedConsultant = jsonbOperations.serialize(consultantOutputData);
        } catch (RuntimeException ex) {
            log.warn(
                "Failed to parse consultant output for audit persistence query={} rawResponse={}",
                queryEntity.getQueryText(),
                rawJson,
                ex);
            serializedConsultant = rawJson;
        }
        Double somScore = syncVerificationResult.somScore();
        Double modifiedZ = syncVerificationResult.modifiedZScore();
        if (somScore == null || modifiedZ == null) {
            log.warn(
                "Incomplete audit metrics after verification query={} somScoreNull={} modifiedZNull={} rawResponse={}",
                queryEntity.getQueryText(),
                somScore == null,
                modifiedZ == null,
                rawJson);
        }
        boolean brand = Boolean.TRUE.equals(syncVerificationResult.brandMentioned());
        Integer mr = syncVerificationResult.mentionRank();
        Integer ov = syncVerificationResult.overallScore();
        jobPersistenceService.upsertAuditHistoryForJobQuery(
            jobId,
            queryEntity.getId(),
            queryEntity.getQueryText(),
            serializedConsultant,
            somScore,
            brand,
            mr,
            ov,
            syncVerificationResult.resolvedEntityLabel(),
            syncVerificationResult.tokenCount(),
            syncVerificationResult.aiCitationPosition(),
            syncVerificationResult.sentimentIntensity(),
            syncVerificationResult.visibilityStage(),
            syncVerificationResult.calculationVersion(),
            modifiedZ,
            syncVerificationResult.gbvsNormalizedScore(),
            syncVerificationResult.modelInsightsJson());
        jobPersistenceService.updateJobStatus(jobId, JobStatus.COMPLETED, null);
        return jobPersistenceService.findJobById(jobId);
    }
}
