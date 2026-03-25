package com.geo.analytics.application.service;
import com.geo.analytics.application.dto.ConsultantOutputData;
import com.geo.analytics.application.dto.SyncVerificationResult;
import com.geo.analytics.application.port.JobStatusBroadcastPublisher;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.ProjectEntity;
import com.geo.analytics.domain.entity.QueryEntity;
import com.geo.analytics.domain.service.EntityNormalizer;
import com.geo.analytics.domain.enums.JobStatus;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.exception.ThresholdExceededException;
import com.geo.analytics.infrastructure.config.StreamingExecutorConfig;
import com.geo.analytics.infrastructure.persistence.JsonbOperations;
import com.geo.analytics.infrastructure.repository.ProjectRepository;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import com.geo.analytics.infrastructure.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
@Service
public class JobQuerySubmissionService {
    private static final Logger log = LoggerFactory.getLogger(JobQuerySubmissionService.class);
    private final JobPersistenceService jobPersistenceService;
    private final AsyncSgeMeasurementService asyncSgeMeasurementService;
    private final SyncVerificationService syncVerificationService;
    private final SomScoreParser somScoreParser;
    private final JsonbOperations jsonbOperations;
    private final JobStatusBroadcastPublisher jobStatusBroadcastPublisher;
    private final JobStreamRegistryService jobStreamRegistryService;
    private final ExecutorService streamDeliveryVirtualExecutor;
    private final int realtimeThreshold;
    private final ProjectRepository projectRepository;
    public JobQuerySubmissionService(
            JobPersistenceService jobPersistenceService,
            AsyncSgeMeasurementService asyncSgeMeasurementService,
            SyncVerificationService syncVerificationService,
            SomScoreParser somScoreParser,
            JsonbOperations jsonbOperations,
            JobStatusBroadcastPublisher jobStatusBroadcastPublisher,
            JobStreamRegistryService jobStreamRegistryService,
            @Qualifier(StreamingExecutorConfig.STREAM_DELIVERY_VIRTUAL_EXECUTOR) ExecutorService streamDeliveryVirtualExecutor,
            @Value("${app.ai.realtime-threshold:10}") int realtimeThreshold,
            ProjectRepository projectRepository) {
        this.jobPersistenceService = jobPersistenceService;
        this.asyncSgeMeasurementService = asyncSgeMeasurementService;
        this.syncVerificationService = syncVerificationService;
        this.somScoreParser = somScoreParser;
        this.jsonbOperations = jsonbOperations;
        this.jobStatusBroadcastPublisher = jobStatusBroadcastPublisher;
        this.jobStreamRegistryService = jobStreamRegistryService;
        this.streamDeliveryVirtualExecutor = streamDeliveryVirtualExecutor;
        this.realtimeThreshold = realtimeThreshold;
        this.projectRepository = projectRepository;
    }
    public void submitQueries(UUID jobId, List<String> queryTexts, SubscriptionPlan plan) {
        int keywordCount = queryTexts.size();
        if (keywordCount > realtimeThreshold) {
            if (plan == SubscriptionPlan.STANDARD) {
                throw new ThresholdExceededException(realtimeThreshold);
            }
            jobPersistenceService.registerQueriesAndTransitionToFileUploaded(jobId, queryTexts, plan);
            startDeepAnalysisBatchPlaceholder(jobId);
            JobEntity jobEntity = jobPersistenceService.findJobById(jobId);
            List<QueryEntity> queryEntities = jobPersistenceService.findQueriesByJobId(jobId);
            asyncSgeMeasurementService.measureSgeForJob(jobEntity, queryEntities);
            return;
        }
        jobPersistenceService.registerQueriesAndTransitionToRealtimeProcessing(jobId, queryTexts, plan);
        executeImmediateParallelProcessing(jobId, plan);
        JobEntity completedJob = jobPersistenceService.findJobById(jobId);
        List<QueryEntity> queryEntities = jobPersistenceService.findQueriesByJobId(jobId);
        asyncSgeMeasurementService.measureSgeForJob(completedJob, queryEntities);
    }
    private void startDeepAnalysisBatchPlaceholder(UUID jobId) {
        log.debug("deep analysis batch placeholder jobId={}", jobId);
    }
    private void executeImmediateParallelProcessing(UUID jobId, SubscriptionPlan subscriptionPlan) {
        JobEntity jobEntity = jobPersistenceService.findJobById(jobId);
        String brandName = jobEntity.getBrandName();
        List<String> competitorHosts = loadCompetitorHosts(jobEntity);
        List<QueryEntity> queryEntities = jobPersistenceService.findQueriesByJobId(jobId);
        UUID tenantId = Objects.requireNonNullElse(jobEntity.getWorkspaceId(), DefaultTenantIds.WORKSPACE_ID);
        List<CompletableFuture<Void>> pendingPersistenceTasks = new ArrayList<>();
        try {
            for (QueryEntity queryEntity : queryEntities) {
                processOneQueryRealtime(
                    jobEntity.getId(),
                    tenantId,
                    brandName,
                    competitorHosts,
                    queryEntity,
                    subscriptionPlan,
                    pendingPersistenceTasks);
            }
            CompletableFuture.allOf(pendingPersistenceTasks.toArray(CompletableFuture[]::new)).join();
            jobPersistenceService.updateJobStatus(jobId, JobStatus.COMPLETED, null);
            jobStatusBroadcastPublisher.publish(jobPersistenceService.findJobById(jobId));
        } finally {
            jobStreamRegistryService.complete(jobId);
        }
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
    private void processOneQueryRealtime(
            UUID jobId,
            UUID tenantId,
            String brandName,
            List<String> competitorHosts,
            QueryEntity queryEntity,
            SubscriptionPlan subscriptionPlan,
            List<CompletableFuture<Void>> pendingPersistenceTasks) {
        TenantContext.executeWithTenant(tenantId, () -> {
            try {
                SyncVerificationResult syncVerificationResult = syncVerificationService.verify(
                    brandName,
                    queryEntity.getQueryText(),
                    subscriptionPlan,
                    jobId,
                    queryEntity.getId(),
                    brandName,
                    competitorHosts);
                pendingPersistenceTasks.add(CompletableFuture.runAsync(
                    () -> TenantContext.executeWithTenant(tenantId, () -> {
                        try {
                            ConsultantOutputData consultantOutputData =
                                somScoreParser.parseConsultantOutput(syncVerificationResult.rawResponseJson());
                            double somScore = syncVerificationResult.somScore() != null
                                ? syncVerificationResult.somScore()
                                : 0.0;
                            boolean brand = Boolean.TRUE.equals(syncVerificationResult.brandMentioned());
                            Integer mr = syncVerificationResult.mentionRank() != null
                                ? syncVerificationResult.mentionRank()
                                : 0;
                            Integer ov = syncVerificationResult.overallScore() != null
                                ? syncVerificationResult.overallScore()
                                : 0;
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
                        } catch (Exception exception) {
                            log.error(
                                "async audit persist failed jobId={} queryId={}",
                                jobId,
                                queryEntity.getId(),
                                exception);
                        }
                    }),
                    streamDeliveryVirtualExecutor));
            } catch (Exception exception) {
                log.error(
                    "immediate mode query failed jobId={} queryId={}",
                    jobId,
                    queryEntity.getId(),
                    exception);
            }
        });
    }
}
