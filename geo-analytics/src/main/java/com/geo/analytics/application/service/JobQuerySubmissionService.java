package com.geo.analytics.application.service;

import com.geo.analytics.application.port.JobStatusBroadcastPublisher;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.ProjectEntity;
import com.geo.analytics.domain.entity.QueryEntity;
import com.geo.analytics.domain.enums.JobStatus;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.exception.InsufficientQuotaException;
import com.geo.analytics.domain.exception.RateLimitExceededException;
import com.geo.analytics.domain.model.PlanLimitsSnapshot;
import com.geo.analytics.domain.model.QuotaCreditCalculator;
import com.geo.analytics.domain.service.EntityNormalizer;
import com.geo.analytics.infrastructure.config.StreamingExecutorConfig;
import com.geo.analytics.infrastructure.persistence.JsonbOperations;
import com.geo.analytics.infrastructure.repository.ProjectRepository;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import com.geo.analytics.infrastructure.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
    private final PlanBasedQuotaManager quotaManager;

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
            PlanBasedQuotaManager quotaManager) {
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
        this.quotaManager = Objects.requireNonNull(quotaManager, "planBasedQuotaManager");
    }

    public void submitQueries(UUID jobId, List<String> queryTexts, SubscriptionPlan plan) {
        var keywordCount = queryTexts.size();
        if (keywordCount <= 0) {
            return;
        }
        var job = jobPersistenceService.findJobById(jobId);
        var planEnum = Objects.requireNonNullElse(job.getAppliedPlan(), plan);
        var limits = effectiveLimits(job, plan);
        var existing = jobPersistenceService.countQueriesByJobId(jobId);
        if (existing + keywordCount > limits.totalLimit()) {
            throw new InsufficientQuotaException(
                    "登録上限を超過しています。",
                    limits.totalLimit(),
                    planEnum.name());
        }
        if (limits.isRealtimeAllowed(keywordCount)) {
            registerRealtimeRouteAndStartSgeThenExecute(jobId, queryTexts, planEnum);
            return;
        }
        if (!planEnum.usesProTierFeatures()) {
            throw new InsufficientQuotaException(
                    "登録上限を超過しています。",
                    limits.totalLimit(),
                    planEnum.name());
        }
        var batchTenantId = Objects.requireNonNullElse(job.getWorkspaceId(), DefaultTenantIds.WORKSPACE_ID);
        long batchDeposit = (long) keywordCount * QuotaCreditCalculator.DEPOSIT_PER_KEYWORD;
        var batchProbe = quotaManager.resolve(batchTenantId).tryConsumeAndReturnRemaining(batchDeposit);
        if (!batchProbe.isConsumed()) {
            var workspacePlan = quotaManager.resolveWorkspacePlan(batchTenantId);
            throw new RateLimitExceededException(batchProbe, workspacePlan.getDailyLimit(), workspacePlan.name());
        }
        try {
            jobPersistenceService.registerQueriesAndTransitionToFileUploaded(jobId, queryTexts, planEnum);
        } catch (RuntimeException e) {
            quotaManager.addTokens(batchTenantId, batchDeposit);
            throw e;
        }
        startDeepAnalysisBatchPlaceholder(jobId);
        var batchJobEntity = jobPersistenceService.findJobById(jobId);
        var batchQueryEntities = jobPersistenceService.findQueriesByJobId(jobId);
        asyncSgeMeasurementService.measureSgeForJob(batchJobEntity, batchQueryEntities, keywordCount);
    }

    private PlanLimitsSnapshot effectiveLimits(JobEntity job, SubscriptionPlan requestPlan) {
        var raw = job.getPlanLimitsSnapshot();
        if (raw != null && !raw.isBlank()) {
            return jsonbOperations.deserialize(raw, PlanLimitsSnapshot.class);
        }
        return PlanLimitsSnapshot.fromPlan(Objects.requireNonNullElse(job.getAppliedPlan(), requestPlan));
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
        var bucket = quotaManager.resolve(tenantId);
        int n = queryEntities.size();
        long realtimeDeposit = (long) n * QuotaCreditCalculator.DEPOSIT_PER_KEYWORD;
        var probe = bucket.tryConsumeAndReturnRemaining(realtimeDeposit);
        if (!probe.isConsumed()) {
            var workspacePlan = quotaManager.resolveWorkspacePlan(tenantId);
            throw new RateLimitExceededException(probe, workspacePlan.getDailyLimit(), workspacePlan.name());
        }
        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] futures = queryEntities.stream()
                .map(qe -> CompletableFuture.runAsync(
                        () -> TenantContext.executeWithTenant(
                                tenantId,
                                () -> {
                                    try {
                                        processOneQueryRealtimeCore(
                                                jobId, tenantId, brandName, competitorHosts, qe, appliedPlan);
                                    } catch (Throwable x) {
                                        quotaManager.addTokens(
                                                tenantId,
                                                QuotaCreditCalculator.refundAfterDeposit(
                                                        QuotaCreditCalculator.DEPOSIT_PER_KEYWORD, 1L));
                                        throw x;
                                    }
                                }),
                        streamDeliveryVirtualExecutor))
                .toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(futures).join();
            jobPersistenceService.updateJobStatus(jobId, JobStatus.COMPLETED, null);
            var completedJobEntity = jobPersistenceService.findJobById(jobId);
            jobStatusBroadcastPublisher.publish(completedJobEntity);
            projectAuditLifecyclePublisher.publishAuditCompleted(completedJobEntity);
        } catch (Throwable t) {
            if (t instanceof CompletionException ce) {
                var c = ce.getCause();
                if (c instanceof RuntimeException re) {
                    throw re;
                }
                if (c instanceof Error e) {
                    throw e;
                }
                throw new IllegalStateException(c);
            }
            if (t instanceof RuntimeException re) {
                throw re;
            }
            if (t instanceof Error e) {
                throw e;
            }
            throw new IllegalStateException(t);
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

    private void processOneQueryRealtimeCore(
            UUID jobId,
            UUID tenantId,
            String brandName,
            List<String> competitorHosts,
            QueryEntity queryEntity,
            SubscriptionPlan appliedPlan) {
        var syncVerificationResult = syncVerificationService.verify(
                brandName,
                queryEntity.getQueryText(),
                appliedPlan,
                jobId,
                queryEntity.getId(),
                brandName,
                competitorHosts);
        long actual = QuotaCreditCalculator.actualCredits(syncVerificationResult.analysisTextLength(), appliedPlan);
        try {
            var consultantOutputData = somScoreParser.parseConsultantOutput(syncVerificationResult.rawResponseJson());
            var somScore = syncVerificationResult.somScore() != null ? syncVerificationResult.somScore() : 0.0;
            var brand = Boolean.TRUE.equals(syncVerificationResult.brandMentioned());
            var mr = syncVerificationResult.mentionRank() != null ? syncVerificationResult.mentionRank() : 0;
            var ov = syncVerificationResult.overallScore() != null ? syncVerificationResult.overallScore() : 0;
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
                    syncVerificationResult.modifiedZScore() != null ? syncVerificationResult.modifiedZScore() : 0.0,
                    syncVerificationResult.competitorScoreRows(),
                    syncVerificationResult.modelInsightsJson());
        } finally {
            long refund = QuotaCreditCalculator.refundAfterDeposit(QuotaCreditCalculator.DEPOSIT_PER_KEYWORD, actual);
            if (refund > 0L) {
                quotaManager.addTokens(tenantId, refund);
            }
        }
    }
}
