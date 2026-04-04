package com.geo.analytics.application.service;

import com.geo.analytics.application.port.JobStatusBroadcastPublisher;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.ProjectEntity;
import com.geo.analytics.domain.entity.QueryEntity;
import com.geo.analytics.domain.service.EntityNormalizer;
import com.geo.analytics.domain.enums.JobStatus;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.exception.InsufficientQuotaException;
import com.geo.analytics.domain.exception.RateLimitExceededException;
import com.geo.analytics.infrastructure.config.StreamingExecutorConfig;
import com.geo.analytics.infrastructure.persistence.JsonbOperations;
import com.geo.analytics.infrastructure.repository.ProjectRepository;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import com.geo.analytics.infrastructure.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
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
    private final ProjectRepository projectRepository;
    private final ProjectAuditLifecyclePublisher projectAuditLifecyclePublisher;
    private final PlanBasedQuotaManager quotaLimiter;

    public JobQuerySubmissionService(
            JobPersistenceService jobPersistenceService,
            AsyncSgeMeasurementService asyncSgeMeasurementService,
            SyncVerificationService syncVerificationService,
            SomScoreParser somScoreParser,
            JsonbOperations jsonbOperations,
            JobStatusBroadcastPublisher jobStatusBroadcastPublisher,
            JobStreamRegistryService jobStreamRegistryService,
            @Qualifier(StreamingExecutorConfig.STREAM_DELIVERY_VIRTUAL_EXECUTOR) ExecutorService streamDeliveryVirtualExecutor,
            ProjectRepository projectRepository,
            ProjectAuditLifecyclePublisher projectAuditLifecyclePublisher,
            PlanBasedQuotaManager quotaLimiter) {
        this.jobPersistenceService = jobPersistenceService;
        this.asyncSgeMeasurementService = asyncSgeMeasurementService;
        this.syncVerificationService = syncVerificationService;
        this.somScoreParser = somScoreParser;
        this.jsonbOperations = jsonbOperations;
        this.jobStatusBroadcastPublisher = jobStatusBroadcastPublisher;
        this.jobStreamRegistryService = jobStreamRegistryService;
        this.streamDeliveryVirtualExecutor = streamDeliveryVirtualExecutor;
        this.projectRepository = projectRepository;
        this.projectAuditLifecyclePublisher = projectAuditLifecyclePublisher;
        this.quotaLimiter = quotaLimiter;
    }

    public void submitQueries(UUID jobId, List<String> queryTexts, SubscriptionPlan plan) {
        var keywordCount = queryTexts.size();
        if (keywordCount <= 0) {
            return;
        }
        var job = jobPersistenceService.findJobById(jobId);
        var snapshot = job.getAppliedPlan() != null ? job.getAppliedPlan() : plan;
        var existing = jobPersistenceService.countQueriesByJobId(jobId);
        if (existing + keywordCount > snapshot.getTotalLimit()) {
            throw new InsufficientQuotaException(
                    "登録上限を超過しています。",
                    snapshot.getTotalLimit(),
                    snapshot.name());
        }
        if (snapshot.isRealtimeAllowed(keywordCount)) {
            registerRealtimeRouteAndStartSgeThenExecute(jobId, queryTexts, snapshot);
            return;
        }
        if (!snapshot.usesProTierFeatures()) {
            throw new InsufficientQuotaException(
                    "登録上限を超過しています。",
                    snapshot.getTotalLimit(),
                    snapshot.name());
        }
        jobPersistenceService.registerQueriesAndTransitionToFileUploaded(jobId, queryTexts, snapshot);
        startDeepAnalysisBatchPlaceholder(jobId);
        var batchJobEntity = jobPersistenceService.findJobById(jobId);
        var batchQueryEntities = jobPersistenceService.findQueriesByJobId(jobId);
        asyncSgeMeasurementService.measureSgeForJob(batchJobEntity, batchQueryEntities);
    }

    private void registerRealtimeRouteAndStartSgeThenExecute(UUID jobId, List<String> queryTexts, SubscriptionPlan plan) {
        jobPersistenceService.registerQueriesAndTransitionToRealtimeProcessing(jobId, queryTexts, plan);
        var jobEntity = jobPersistenceService.findJobById(jobId);
        var queryEntities = jobPersistenceService.findQueriesByJobId(jobId);
        asyncSgeMeasurementService.measureSgeForJob(jobEntity, queryEntities);
        executeImmediateParallelProcessing(jobId);
    }

    private void startDeepAnalysisBatchPlaceholder(UUID jobId) {
        log.debug("deep analysis batch placeholder jobId={}", jobId);
    }

    private void executeImmediateParallelProcessing(UUID jobId) {
        var jobEntity = jobPersistenceService.findJobById(jobId);
        var brandName = jobEntity.getBrandName();
        var competitorHosts = loadCompetitorHosts(jobEntity);
        var queryEntities = jobPersistenceService.findQueriesByJobId(jobId);
        var tenantId = Objects.requireNonNullElse(jobEntity.getWorkspaceId(), DefaultTenantIds.WORKSPACE_ID);
        var appliedPlan = Objects.requireNonNullElse(jobEntity.getAppliedPlan(), SubscriptionPlan.STANDARD);
        var probe = quotaLimiter.resolve(tenantId).tryConsumeAndReturnRemaining(queryEntities.size());
        if (!probe.isConsumed()) {
            var workspacePlan = quotaLimiter.resolveWorkspacePlan(tenantId);
            throw new RateLimitExceededException(probe, workspacePlan.getDailyLimit(), workspacePlan.name());
        }
        var pendingPersistenceTasks = new ArrayList<CompletableFuture<Void>>();
        try {
            for (var queryEntity : queryEntities) {
                processOneQueryRealtime(
                        jobEntity.getId(),
                        tenantId,
                        brandName,
                        competitorHosts,
                        queryEntity,
                        appliedPlan,
                        pendingPersistenceTasks);
            }
            CompletableFuture.allOf(pendingPersistenceTasks.toArray(CompletableFuture[]::new)).join();
            jobPersistenceService.updateJobStatus(jobId, JobStatus.COMPLETED, null);
            var completedJobEntity = jobPersistenceService.findJobById(jobId);
            jobStatusBroadcastPublisher.publish(completedJobEntity);
            projectAuditLifecyclePublisher.publishAuditCompleted(completedJobEntity);
        } finally {
            jobStreamRegistryService.complete(jobId);
        }
    }

    private List<String> loadCompetitorHosts(JobEntity jobEntity) {
        var projectId = jobEntity.getProjectId();
        if (projectId == null) {
            return List.of();
        }
        var wid = Objects.requireNonNullElse(jobEntity.getWorkspaceId(), DefaultTenantIds.WORKSPACE_ID);
        return TenantContext.executeWithTenant(wid, () -> projectRepository.findByIdWithCompetitorUrls(projectId)
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
            SubscriptionPlan appliedPlan,
            List<CompletableFuture<Void>> pendingPersistenceTasks) {
        pendingPersistenceTasks.add(CompletableFuture.runAsync(
                () -> TenantContext.executeWithTenant(tenantId, () -> {
                    try {
                        var syncVerificationResult = syncVerificationService.verify(
                                brandName,
                                queryEntity.getQueryText(),
                                appliedPlan,
                                jobId,
                                queryEntity.getId(),
                                brandName,
                                competitorHosts);
                        var consultantOutputData =
                                somScoreParser.parseConsultantOutput(syncVerificationResult.rawResponseJson());
                        var somScore = syncVerificationResult.somScore() != null
                                ? syncVerificationResult.somScore()
                                : 0.0;
                        var brand = Boolean.TRUE.equals(syncVerificationResult.brandMentioned());
                        var mr = syncVerificationResult.mentionRank() != null
                                ? syncVerificationResult.mentionRank()
                                : 0;
                        var ov = syncVerificationResult.overallScore() != null
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
                                syncVerificationResult.sentimentIntensity(),
                                syncVerificationResult.visibilityStage(),
                                syncVerificationResult.calculationVersion(),
                                syncVerificationResult.modifiedZScore() != null
                                        ? syncVerificationResult.modifiedZScore()
                                        : 0.0,
                                syncVerificationResult.competitorScoreRows(),
                                syncVerificationResult.modelInsightsJson());
                    } catch (Exception exception) {
                        log.error(
                                "immediate mode query failed jobId={} queryId={}",
                                jobId,
                                queryEntity.getId(),
                                exception);
                    }
                }),
                streamDeliveryVirtualExecutor));
    }
}