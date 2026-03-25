package com.geo.analytics.application.service;
import com.geo.analytics.application.port.JobStatusBroadcastPublisher;
import com.geo.analytics.application.dto.ConsultantOutputData;
import com.geo.analytics.application.dto.SyncVerificationResult;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.ProjectEntity;
import com.geo.analytics.domain.entity.QueryEntity;
import com.geo.analytics.domain.service.EntityNormalizer;
import com.geo.analytics.domain.enums.JobStatus;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.infrastructure.persistence.JsonbOperations;
import com.geo.analytics.infrastructure.repository.ProjectRepository;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import com.geo.analytics.infrastructure.tenant.TenantContext;
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
    private final ProjectRepository projectRepository;
    public JobSyncTestService(
            JobPersistenceService jobPersistenceService,
            SyncVerificationService syncVerificationService,
            SomScoreParser somScoreParser,
            JsonbOperations jsonbOperations,
            JobStatusBroadcastPublisher jobStatusBroadcastPublisher,
            ProjectRepository projectRepository) {
        this.jobPersistenceService = jobPersistenceService;
        this.syncVerificationService = syncVerificationService;
        this.somScoreParser = somScoreParser;
        this.jsonbOperations = jsonbOperations;
        this.jobStatusBroadcastPublisher = jobStatusBroadcastPublisher;
        this.projectRepository = projectRepository;
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
        List<String> competitorHosts = loadCompetitorHosts(jobEntity);
        SyncVerificationResult syncVerificationResult = syncVerificationService.verify(
            jobEntity.getBrandName(),
            queryEntity.getQueryText(),
            subscriptionPlan,
            null,
            null,
            jobEntity.getBrandName(),
            competitorHosts);
        ConsultantOutputData consultantOutputData =
            somScoreParser.parseConsultantOutput(syncVerificationResult.rawResponseJson());
        double somScore = syncVerificationResult.somScore() != null ? syncVerificationResult.somScore() : 0.0;
        boolean brand = Boolean.TRUE.equals(syncVerificationResult.brandMentioned());
        Integer mr = syncVerificationResult.mentionRank() != null ? syncVerificationResult.mentionRank() : 0;
        Integer ov = syncVerificationResult.overallScore() != null ? syncVerificationResult.overallScore() : 0;
        jobPersistenceService.upsertAuditHistoryForJobQuery(
            jobId,
            queryEntity.getId(),
            queryEntity.getQueryText(),
            jsonbOperations.serialize(consultantOutputData),
            somScore,
            brand,
            mr,
            ov,
            syncVerificationResult.resolvedEntityLabel(),
            syncVerificationResult.tokenCount(),
            syncVerificationResult.rankPosition(),
            syncVerificationResult.sentimentIntensity());
        jobPersistenceService.updateJobStatus(jobId, JobStatus.COMPLETED, null);
        JobEntity updatedJobEntity = jobPersistenceService.findJobById(jobId);
        jobStatusBroadcastPublisher.publish(updatedJobEntity);
        return updatedJobEntity;
    }
    private List<String> loadCompetitorHosts(JobEntity jobEntity) {
        UUID projectId = jobEntity.getProjectId();
        if (projectId == null) {
            return List.of();
        }
        UUID wid = Objects.requireNonNullElse(jobEntity.getWorkspaceId(), DefaultTenantIds.WORKSPACE_ID);
        return TenantContext.executeWithTenant(wid, () -> projectRepository.findById(projectId)
            .map(ProjectEntity::getCompetitorUrls)
            .orElse(List.of())
            .stream()
            .map(EntityNormalizer::hostLabelFromUrl)
            .filter(s -> !s.isBlank())
            .toList());
    }
}
