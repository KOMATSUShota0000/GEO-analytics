package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.SelectedCompetitor;
import com.geo.analytics.domain.model.CompetitorProfile;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.ProjectEntity;
import com.geo.analytics.domain.entity.QueryEntity;
import com.geo.analytics.domain.entity.WorkspaceEntity;
import com.geo.analytics.domain.enums.CompetitorExtractionMode;
import com.geo.analytics.domain.enums.JobStatus;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.exception.InsufficientQuotaException;
import com.geo.analytics.domain.exception.RateLimitExceededException;
import com.geo.analytics.domain.model.PlanLimitsSnapshot;
import com.geo.analytics.domain.support.TextWhitespaceNormalizer;
import com.geo.analytics.domain.model.QuotaCreditCalculator;
import com.geo.analytics.domain.service.EntityNormalizer;
import com.geo.analytics.infrastructure.config.StreamingExecutorConfig;
import com.geo.analytics.infrastructure.persistence.JsonbOperations;
import com.geo.analytics.infrastructure.repository.ProjectRepository;
import com.geo.analytics.infrastructure.repository.WorkspaceRepository;
import com.geo.analytics.infrastructure.tenant.ContextPropagator;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import com.geo.analytics.infrastructure.tenant.TenantIdentity;
import com.geo.analytics.infrastructure.tenant.TenantPlanScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
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
    private final ExecutorService streamDeliveryVirtualExecutor;
    private final ExecutorService realtimeParallelVirtualExecutor;
    private final ProjectRepository projectRepository;
    private final WorkspaceRepository workspaceRepository;
    private final HybridCompetitorPipelineService hybridCompetitorPipelineService;
    private final ProjectAuditLifecyclePublisher projectAuditLifecyclePublisher;
    private final PlanBasedQuotaManager quotaManager;
    private final JobBenchmarkCaptureService jobBenchmarkCaptureService;
    private final AiRubricAuditService aiRubricAuditService;

    public JobQuerySubmissionService(
            JobPersistenceService jobPersistenceService,
            AsyncSgeMeasurementService asyncSgeMeasurementService,
            SyncVerificationService syncVerificationService,
            SomScoreParser somScoreParser,
            JsonbOperations jsonbOperations,
            @Qualifier(StreamingExecutorConfig.STREAM_DELIVERY_VIRTUAL_EXECUTOR) ExecutorService streamDeliveryVirtualExecutor,
            @Qualifier(StreamingExecutorConfig.REALTIME_PARALLEL_VIRTUAL_EXECUTOR)
                    ExecutorService realtimeParallelVirtualExecutor,
            ProjectRepository projectRepository,
            WorkspaceRepository workspaceRepository,
            HybridCompetitorPipelineService hybridCompetitorPipelineService,
            ProjectAuditLifecyclePublisher projectAuditLifecyclePublisher,
            PlanBasedQuotaManager quotaManager,
            JobBenchmarkCaptureService jobBenchmarkCaptureService,
            AiRubricAuditService aiRubricAuditService) {
        this.jobPersistenceService = jobPersistenceService;
        this.asyncSgeMeasurementService = asyncSgeMeasurementService;
        this.syncVerificationService = syncVerificationService;
        this.somScoreParser = somScoreParser;
        this.jsonbOperations = jsonbOperations;
        this.streamDeliveryVirtualExecutor = streamDeliveryVirtualExecutor;
        this.realtimeParallelVirtualExecutor = realtimeParallelVirtualExecutor;
        this.projectRepository = projectRepository;
        this.workspaceRepository = workspaceRepository;
        this.hybridCompetitorPipelineService = hybridCompetitorPipelineService;
        this.projectAuditLifecyclePublisher = projectAuditLifecyclePublisher;
        this.quotaManager = Objects.requireNonNull(quotaManager, "planBasedQuotaManager");
        this.jobBenchmarkCaptureService = Objects.requireNonNull(jobBenchmarkCaptureService);
        this.aiRubricAuditService = Objects.requireNonNull(aiRubricAuditService);
    }

    public void submitQueries(UUID jobId, List<String> queryTexts, SubscriptionPlan plan) {
        queryTexts = queryTexts.stream().map(TextWhitespaceNormalizer::normalize).toList();
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
            UUID workspaceId = Objects.requireNonNullElse(job.getWorkspaceId(), DefaultTenantIds.WORKSPACE_ID);
            long realtimeDeposit = (long) keywordCount * QuotaCreditCalculator.DEPOSIT_PER_KEYWORD;
            var realtimeProbe = quotaManager.resolve(workspaceId).tryConsumeAndReturnRemaining(realtimeDeposit);
            if (!realtimeProbe.isConsumed()) {
                var workspacePlan = quotaManager.resolveWorkspacePlan(workspaceId);
                throw new RateLimitExceededException(
                        realtimeProbe, workspacePlan.getDailyLimit(), workspacePlan.name());
            }
            UUID organizationId = resolveOrganizationId(workspaceId);
            jobPersistenceService.updateJobStatus(jobId, JobStatus.EXTRACTING_COMPETITORS, null);
            scheduleHybridContinuation(
                    jobId, queryTexts, planEnum, workspaceId, organizationId, true, realtimeDeposit, workspaceId);
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
        UUID workspaceId = Objects.requireNonNullElse(job.getWorkspaceId(), DefaultTenantIds.WORKSPACE_ID);
        UUID organizationId = resolveOrganizationId(workspaceId);
        jobPersistenceService.updateJobStatus(jobId, JobStatus.EXTRACTING_COMPETITORS, null);
        scheduleHybridContinuation(
                jobId,
                queryTexts,
                planEnum,
                workspaceId,
                organizationId,
                false,
                batchDeposit,
                batchTenantId);
    }

    private UUID resolveOrganizationId(UUID workspaceId) {
        return workspaceRepository
                .findById(workspaceId)
                .map(WorkspaceEntity::getOrganizationId)
                .or(() -> TenantContextHolder.current().map(TenantIdentity::organizationId))
                .orElse(DefaultTenantIds.DEFAULT_ORGANIZATION_ID);
    }

    private void scheduleHybridContinuation(
            UUID jobId,
            List<String> queryTexts,
            SubscriptionPlan planEnum,
            UUID workspaceId,
            UUID organizationId,
            boolean realtime,
            long batchDeposit,
            UUID batchTenantId) {
        List<String> capturedQueries = List.copyOf(queryTexts);
        CompletableFuture.runAsync(
                ContextPropagator.wrapRunnable(
                        () -> TenantPlanScope.executeWithTenantOrganizationAndPlan(
                                workspaceId,
                                organizationId,
                                planEnum,
                                () -> runHybridContinuation(
                                        jobId,
                                        capturedQueries,
                                        planEnum,
                                        realtime,
                                        batchDeposit,
                                        batchTenantId))),
                streamDeliveryVirtualExecutor);
    }

    private void runHybridContinuation(
            UUID jobId,
            List<String> queryTexts,
            SubscriptionPlan planEnum,
            boolean realtime,
            long batchDeposit,
            UUID batchTenantId) {
        try {
            JobEntity jobEntity = jobPersistenceService.findJobById(jobId);
            UUID projectId = jobEntity.getProjectId();
            if (projectId == null) {
                log.warn("hybrid continuation requires projectId jobId={}", jobId);
                throw new IllegalStateException("projectId");
            }
            String targetUrl = jobPersistenceService.loadTargetUrlForProject(projectId);
            CompetitorExtractionMode competitorExtractionMode =
                    Objects.requireNonNullElse(jobEntity.getCompetitorExtractionMode(), CompetitorExtractionMode.LOCAL_STORE);
            List<SelectedCompetitor> selected =
                    hybridCompetitorPipelineService.executePipeline(jobId, projectId, targetUrl, competitorExtractionMode);
            List<String> urls = competitorUrlsFromSelected(selected);
            jobPersistenceService.saveProjectCompetitorUrls(projectId, urls);
            jobPersistenceService.saveProjectCompetitorProfiles(
                    projectId, competitorProfilesFromSelected(selected));
            continueAfterCompetitorsPersisted(
                    jobId, queryTexts, planEnum, realtime, batchDeposit, batchTenantId);
        } catch (Throwable throwable) {
            try {
                JobEntity jobEntity = jobPersistenceService.findJobById(jobId);
                UUID projectId = jobEntity.getProjectId();
                if (projectId == null) {
                    throw throwable;
                }
                jobPersistenceService.saveProjectCompetitorUrls(projectId, List.of("", "", ""));
                continueAfterCompetitorsPersisted(
                        jobId, queryTexts, planEnum, realtime, batchDeposit, batchTenantId);
            } catch (Throwable throwable2) {
                if (batchDeposit > 0L && batchTenantId != null) {
                    quotaManager.addTokens(batchTenantId, batchDeposit);
                }
                jobPersistenceService.updateJobStatus(jobId, JobStatus.FAILED, failurePreview(throwable2));
            }
        }
    }

    private void continueAfterCompetitorsPersisted(
            UUID jobId,
            List<String> queryTexts,
            SubscriptionPlan planEnum,
            boolean realtime,
            long batchDeposit,
            UUID batchTenantId) {
        if (realtime) {
            jobPersistenceService.registerQueriesAndTransitionToRealtimeProcessing(jobId, queryTexts, planEnum);
            JobEntity jobEntity = jobPersistenceService.findJobById(jobId);
            List<QueryEntity> queryEntities = jobPersistenceService.findQueriesByJobId(jobId);
            asyncSgeMeasurementService.measureSgeForJob(jobEntity, queryEntities);
            executeImmediateParallelProcessing(jobId);
            return;
        }
        try {
            jobPersistenceService.registerQueriesAndTransitionToFileUploaded(jobId, queryTexts, planEnum);
        } catch (RuntimeException runtimeException) {
            if (batchDeposit > 0L && batchTenantId != null) {
                quotaManager.addTokens(batchTenantId, batchDeposit);
            }
            throw runtimeException;
        }
        startDeepAnalysisBatchPlaceholder(jobId);
        JobEntity batchJobEntity = jobPersistenceService.findJobById(jobId);
        List<QueryEntity> batchQueryEntities = jobPersistenceService.findQueriesByJobId(jobId);
        asyncSgeMeasurementService.measureSgeForJob(batchJobEntity, batchQueryEntities, queryTexts.size());
    }

    private static List<CompetitorProfile> competitorProfilesFromSelected(List<SelectedCompetitor> selected) {
        List<CompetitorProfile> out = new ArrayList<>();
        if (selected != null) {
            for (SelectedCompetitor competitor : selected) {
                if (competitor == null) {
                    continue;
                }
                String url = competitor.websiteUrl();
                out.add(new CompetitorProfile(
                        competitor.name(),
                        url != null && !url.isBlank() ? url : null,
                        competitor.synthetic()));
                if (out.size() == 3) {
                    return new ArrayList<>(out);
                }
            }
        }
        return new ArrayList<>(out);
    }

    private static List<String> competitorUrlsFromSelected(List<SelectedCompetitor> selected) {
        List<String> out = new ArrayList<>();
        if (selected != null) {
            for (SelectedCompetitor competitor : selected) {
                if (competitor == null) {
                    out.add("");
                } else {
                    String u = competitor.websiteUrl();
                    out.add(u != null && !u.isBlank() ? u : "");
                }
                if (out.size() == 3) {
                    return new ArrayList<>(out);
                }
            }
        }
        while (out.size() < 3) {
            out.add("");
        }
        return new ArrayList<>(out);
    }

    private PlanLimitsSnapshot effectiveLimits(JobEntity job, SubscriptionPlan requestPlan) {
        var raw = job.getPlanLimitsSnapshot();
        if (raw != null && !raw.isBlank()) {
            return jsonbOperations.deserialize(raw, PlanLimitsSnapshot.class);
        }
        return PlanLimitsSnapshot.fromPlan(Objects.requireNonNullElse(job.getAppliedPlan(), requestPlan));
    }

    private void startDeepAnalysisBatchPlaceholder(UUID jobId) {
        log.debug("deep analysis batch placeholder jobId={}", jobId);
    }

    private void executeImmediateParallelProcessing(UUID jobId) {
        var jobEntity = jobPersistenceService.findJobById(jobId);
        var brandName = jobEntity.getBrandName();
        var targetUrl = jobEntity.getTargetUrl();
        var competitorHosts = loadCompetitorHosts(jobEntity);
        var queryEntities = jobPersistenceService.findQueriesByJobId(jobId);
        var tenantId = Objects.requireNonNullElse(jobEntity.getWorkspaceId(), DefaultTenantIds.WORKSPACE_ID);
        var appliedPlan = Objects.requireNonNullElse(jobEntity.getAppliedPlan(), SubscriptionPlan.STANDARD);
        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] futures = queryEntities.stream()
                .map(qe -> CompletableFuture.runAsync(
                        ContextPropagator.wrapRunnable(
                                () -> {
                                    try {
                                        processOneQueryRealtimeCore(
                                                jobId, tenantId, brandName, targetUrl, competitorHosts, qe, appliedPlan);
                                    } catch (Throwable x) {
                                        quotaManager.addTokens(
                                                tenantId,
                                                QuotaCreditCalculator.DEPOSIT_PER_KEYWORD);
                                        throw x;
                                    }
                                }),
                        realtimeParallelVirtualExecutor))
                .toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(futures).join();
            jobBenchmarkCaptureService.capture(jobId);
            aiRubricAuditService.runMultiDomainAuditForCompletedJob(jobId);
            jobPersistenceService.updateJobStatus(jobId, JobStatus.COMPLETED, null);
            var completedJobEntity = jobPersistenceService.findJobById(jobId);
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
                throw new IllegalStateException(
                        c != null
                                ? (c.getMessage() != null ? c.getMessage() : c.toString())
                                : "CompletionException without cause",
                        c != null ? c : ce);
            }
            if (t instanceof RuntimeException re) {
                throw re;
            }
            if (t instanceof Error e) {
                throw e;
            }
            throw new IllegalStateException(
                    t.getMessage() != null ? t.getMessage() : t.toString(), t);
        }
    }

    private List<String> loadCompetitorHosts(JobEntity jobEntity) {
        var projectId = jobEntity.getProjectId();
        if (projectId == null) {
            return List.of();
        }
        var wid = Objects.requireNonNullElse(jobEntity.getWorkspaceId(), DefaultTenantIds.WORKSPACE_ID);
        return TenantPlanScope.executeWithTenant(wid, () -> projectRepository.findByIdWithCompetitorUrls(projectId)
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
            String targetUrl,
            List<String> competitorHosts,
            QueryEntity queryEntity,
            SubscriptionPlan appliedPlan) {
        // target_url がある場合は自社ページをクロールして実コンテンツで SoM 解析する。
        // URL 未設定の旧ジョブのみ内部知識モード（クロールなし）にフォールバックする。
        var syncVerificationResult = targetUrl != null && !targetUrl.isBlank()
                ? syncVerificationService.verifyWithUrl(
                        brandName,
                        queryEntity.getQueryText(),
                        targetUrl,
                        appliedPlan,
                        jobId,
                        queryEntity.getId(),
                        brandName,
                        competitorHosts)
                : syncVerificationService.verify(
                        brandName,
                        queryEntity.getQueryText(),
                        appliedPlan,
                        jobId,
                        queryEntity.getId(),
                        brandName,
                        competitorHosts);
        String rawJson = syncVerificationResult.rawResponseJson();
        String serializedConsultant;
        try {
            var consultantOutputData = somScoreParser.parseConsultantOutput(rawJson);
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
        var brand = Boolean.TRUE.equals(syncVerificationResult.brandMentioned());
        Integer mr = syncVerificationResult.mentionRank();
        Integer ov = syncVerificationResult.overallScore();
        Double gbvsNorm = syncVerificationResult.gbvsNormalizedScore();
        log.info(
                "[DB SAVE DEBUG] jobId={} queryId={} queryText={} somScore={} gbvsNormalizedScore={} modifiedZ={}",
                jobId,
                queryEntity.getId(),
                queryEntity.getQueryText(),
                somScore,
                gbvsNorm,
                modifiedZ);
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
                gbvsNorm,
                syncVerificationResult.competitorScoreRows(),
                syncVerificationResult.modelInsightsJson());
    }

    private static String failurePreview(Throwable t) {
        if (t == null) {
            return "unknown";
        }
        String m = t.getMessage();
        if (m != null && !m.isBlank()) {
            return m;
        }
        return t.getClass().getName();
    }
}
