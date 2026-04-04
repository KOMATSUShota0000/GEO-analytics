package com.geo.analytics.application.service;
import com.geo.analytics.application.dto.CompetitorScoreRow;
import com.geo.analytics.application.dto.JobAnalysisAggregate;
import com.geo.analytics.application.dto.PdfGenerationStartResult;
import com.geo.analytics.application.port.JobStatusBroadcastPublisher;
import com.geo.analytics.domain.PdfJobStatusValues;
import com.geo.analytics.domain.entity.AuditHistoryEntity;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.ProjectEntity;
import com.geo.analytics.domain.entity.JobCompetitorScoreEntity;
import com.geo.analytics.domain.entity.QueryEntity;
import com.geo.analytics.domain.enums.JobStatus;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.infrastructure.repository.AuditHistoryRepository;
import com.geo.analytics.infrastructure.repository.JobRepository;
import com.geo.analytics.infrastructure.repository.ProjectRepository;
import com.geo.analytics.infrastructure.repository.QueryRepository;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import com.geo.analytics.infrastructure.tenant.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
@Service
@Transactional(readOnly = true)
public class JobPersistenceService {
    public record JobCreateOutcome(JobEntity jobEntity, boolean created) {}
    private final JobRepository jobRepository;
    private final QueryRepository queryRepository;
    private final AuditHistoryRepository auditHistoryRepository;
    private final ProjectRepository projectRepository;
    private final JobStatusBroadcastPublisher jobStatusBroadcastPublisher;
    private final ProjectManagementService projectManagementService;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate jobCreateTransactionTemplate;
    private final StrategyInsightService strategyInsightService;
    public JobPersistenceService(
            JobRepository jobRepository,
            QueryRepository queryRepository,
            AuditHistoryRepository auditHistoryRepository,
            ProjectRepository projectRepository,
            JobStatusBroadcastPublisher jobStatusBroadcastPublisher,
            ProjectManagementService projectManagementService,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager platformTransactionManager,
            StrategyInsightService strategyInsightService) {
        this.jobRepository = jobRepository;
        this.queryRepository = queryRepository;
        this.auditHistoryRepository = auditHistoryRepository;
        this.projectRepository = projectRepository;
        this.jobStatusBroadcastPublisher = jobStatusBroadcastPublisher;
        this.projectManagementService = projectManagementService;
        this.jdbcTemplate = jdbcTemplate;
        this.jobCreateTransactionTemplate = new TransactionTemplate(platformTransactionManager);
        this.jobCreateTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.strategyInsightService = strategyInsightService;
    }
    private UUID readWorkspaceIdForJob(UUID jobId) {
        List<String> rows = jdbcTemplate.query(
            "SELECT tenant_id FROM jobs WHERE id = ?",
            ps -> ps.setObject(1, jobId),
            (rs, rowNum) -> rs.getString(1));
        if (rows.isEmpty() || rows.get(0) == null || rows.get(0).isBlank()) {
            return DefaultTenantIds.WORKSPACE_ID;
        }
        return UUID.fromString(rows.get(0));
    }
    private UUID readWorkspaceIdForQuery(UUID queryId) {
        List<String> rows = jdbcTemplate.query(
            "SELECT j.tenant_id FROM jobs j INNER JOIN job_queries q ON q.job_id = j.id WHERE q.id = ?",
            ps -> ps.setObject(1, queryId),
            (rs, rowNum) -> rs.getString(1));
        if (rows.isEmpty() || rows.get(0) == null || rows.get(0).isBlank()) {
            return DefaultTenantIds.WORKSPACE_ID;
        }
        return UUID.fromString(rows.get(0));
    }
    private UUID readWorkspaceIdForAudit(UUID auditHistoryId) {
        List<String> rows = jdbcTemplate.query(
            "SELECT tenant_id FROM audit_histories WHERE id = ?",
            ps -> ps.setObject(1, auditHistoryId),
            (rs, rowNum) -> rs.getString(1));
        if (rows.isEmpty() || rows.get(0) == null || rows.get(0).isBlank()) {
            return DefaultTenantIds.WORKSPACE_ID;
        }
        return UUID.fromString(rows.get(0));
    }
    public JobEntity findJobById(UUID jobId) {
        UUID tenantId = readWorkspaceIdForJob(jobId);
        return TenantContext.executeWithTenant(tenantId, () -> jobRepository.findById(jobId)
            .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId)));
    }
    public Optional<JobEntity> findJobByIdOptional(UUID jobId) {
        UUID tenantId = readWorkspaceIdForJob(jobId);
        return TenantContext.executeWithTenant(tenantId, () -> jobRepository.findById(jobId));
    }
    public List<JobEntity> findJobsByStatus(JobStatus jobStatus) {
        return TenantContext.executeWithTenant(DefaultTenantIds.WORKSPACE_ID, () -> jobRepository.findByJobStatus(jobStatus));
    }
    public List<QueryEntity> findUnprocessedQueriesByJobId(UUID jobId) {
        UUID tenantId = readWorkspaceIdForJob(jobId);
        return TenantContext.executeWithTenant(tenantId, () -> queryRepository.findByJobIdAndProcessedFalse(jobId));
    }
    public List<QueryEntity> findQueriesByJobId(UUID jobId) {
        UUID tenantId = readWorkspaceIdForJob(jobId);
        return TenantContext.executeWithTenant(tenantId, () -> queryRepository.findByJobId(jobId));
    }

    public long countQueriesByJobId(UUID jobId) {
        UUID tenantId = readWorkspaceIdForJob(jobId);
        return TenantContext.executeWithTenant(tenantId, () -> queryRepository.countByJobId(jobId));
    }
    public Optional<QueryEntity> findQueryById(UUID queryId) {
        UUID tenantId = readWorkspaceIdForQuery(queryId);
        return TenantContext.executeWithTenant(tenantId, () -> queryRepository.findById(queryId));
    }
    public List<AuditHistoryEntity> findResultsByJobId(UUID jobId) {
        UUID tenantId = readWorkspaceIdForJob(jobId);
        return TenantContext.executeWithTenant(tenantId, () -> auditHistoryRepository.findByJobId(jobId));
    }
    public JobAnalysisAggregate findJobAnalysisAggregate(UUID jobId) {
        UUID tenantId = readWorkspaceIdForJob(jobId);
        return TenantContext.executeWithTenant(tenantId, () -> {
            JobEntity jobEntity = jobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));
            ProjectEntity projectEntity = null;
            if (jobEntity.getProjectId() != null) {
                projectEntity = projectRepository.findByIdWithCompetitorUrls(jobEntity.getProjectId()).orElse(null);
            }
            List<AuditHistoryEntity> auditHistories = auditHistoryRepository.findByJobId(jobId);
            return new JobAnalysisAggregate(jobEntity, projectEntity, auditHistories);
        });
    }
    @Transactional
    public void upsertAuditHistoryForJobQuery(
            UUID jobId,
            UUID queryId,
            String queryText,
            String rawResponse,
            double somScore,
            boolean brandMentioned,
            Integer mentionRank,
            Integer overallScore,
            String resolvedEntityLabel,
            int tokenCount,
            int rankPosition,
            double sentimentIntensity,
            Integer visibilityStage,
            String calculationVersion,
            double modifiedZScore,
            List<CompetitorScoreRow> competitorScoreRows,
            String modelInsightsJson) {
        UUID tenantId = readWorkspaceIdForJob(jobId);
        TenantContext.executeWithTenant(tenantId, () -> {
            JobEntity jobEntity = jobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));
            UUID workspaceId = jobEntity.getWorkspaceId() != null ? jobEntity.getWorkspaceId() : DefaultTenantIds.WORKSPACE_ID;
            UUID projectId = Objects.requireNonNull(jobEntity.getProjectId(), "projectId");
            ProjectEntity projectEntity = projectRepository.getReferenceById(projectId);
            var negativeAlert = sentimentIntensity < -0.5;
            var insight = strategyInsightService.fromModifiedZ(modifiedZScore);
            var actions = new ArrayList<>(insight.recommendedActions());
            Optional<AuditHistoryEntity> existingOptional = auditHistoryRepository.findByJobIdAndQuery(jobId, queryText);
            if (existingOptional.isPresent()) {
                AuditHistoryEntity existing = existingOptional.get();
                existing.setRawResponse(rawResponse);
                existing.setSomScore(somScore);
                existing.setBrandMentioned(brandMentioned);
                existing.setMentionRank(mentionRank);
                existing.setOverallScore(overallScore);
                existing.setResolvedEntityLabel(resolvedEntityLabel);
                existing.setTokenCount(tokenCount);
                existing.setRankPosition(rankPosition);
                existing.setSentimentIntensity(sentimentIntensity);
                existing.setVisibilityStage(visibilityStage);
                existing.setCalculationVersion(calculationVersion);
                existing.setNegativeAlert(negativeAlert);
                existing.setModifiedZScore(modifiedZScore);
                existing.setDiagnosticMessage(insight.diagnosticMessage());
                existing.setRecommendedActions(actions);
                existing.setAuditDate(LocalDate.now());
                existing.setWorkspaceId(workspaceId);
                existing.setModelInsightsJson(modelInsightsJson);
                applyCompetitorScores(existing, competitorScoreRows);
                auditHistoryRepository.save(existing);
            } else {
                AuditHistoryEntity auditHistoryEntity = new AuditHistoryEntity();
                auditHistoryEntity.setJobId(jobId);
                auditHistoryEntity.setWorkspaceId(workspaceId);
                auditHistoryEntity.setProject(projectEntity);
                auditHistoryEntity.setQuery(queryText);
                auditHistoryEntity.setRawResponse(rawResponse);
                auditHistoryEntity.setSomScore(somScore);
                auditHistoryEntity.setBrandMentioned(brandMentioned);
                auditHistoryEntity.setMentionRank(mentionRank);
                auditHistoryEntity.setOverallScore(overallScore);
                auditHistoryEntity.setResolvedEntityLabel(resolvedEntityLabel);
                auditHistoryEntity.setTokenCount(tokenCount);
                auditHistoryEntity.setRankPosition(rankPosition);
                auditHistoryEntity.setSentimentIntensity(sentimentIntensity);
                auditHistoryEntity.setVisibilityStage(visibilityStage);
                auditHistoryEntity.setCalculationVersion(calculationVersion);
                auditHistoryEntity.setNegativeAlert(negativeAlert);
                auditHistoryEntity.setModifiedZScore(modifiedZScore);
                auditHistoryEntity.setDiagnosticMessage(insight.diagnosticMessage());
                auditHistoryEntity.setRecommendedActions(actions);
                auditHistoryEntity.setAuditDate(LocalDate.now());
                auditHistoryEntity.setModelInsightsJson(modelInsightsJson);
                applyCompetitorScores(auditHistoryEntity, competitorScoreRows);
                auditHistoryRepository.save(auditHistoryEntity);
            }
            queryRepository.findById(queryId).ifPresent(queryEntity -> {
                queryEntity.setProcessed(true);
                queryRepository.save(queryEntity);
            });
        });
    }

    private static void applyCompetitorScores(AuditHistoryEntity audit, List<CompetitorScoreRow> rows) {
        audit.getCompetitorScores().clear();
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (var row : rows) {
            var entity = new JobCompetitorScoreEntity();
            entity.setAuditHistory(audit);
            entity.setCompetitorName(row.competitorName());
            entity.setSomScore(row.somScore());
            entity.setRankPosition(row.rankPosition());
            entity.setVisibilityStage(row.visibilityStage());
            entity.setMatchStatus(row.matchStatus());
            audit.getCompetitorScores().add(entity);
        }
    }
    @Transactional
    public void updateAuditStrategyInsights(
            UUID auditHistoryId,
            String diagnosticMessage,
            List<String> recommendedActions,
            String calculationVersion) {
        UUID tenantId = readWorkspaceIdForAudit(auditHistoryId);
        TenantContext.executeWithTenant(tenantId, () -> auditHistoryRepository.findById(auditHistoryId).ifPresent(entity -> {
            entity.setDiagnosticMessage(diagnosticMessage);
            entity.setRecommendedActions(new ArrayList<>(recommendedActions));
            entity.setCalculationVersion(calculationVersion);
            auditHistoryRepository.save(entity);
        }));
    }
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public JobEntity createJob(String brandName) {
        return createJobWithIdempotency(brandName, null).jobEntity();
    }
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public JobCreateOutcome createJobWithIdempotency(String brandName, UUID idempotencyKey) {
        ProjectEntity projectEntity = projectManagementService.getOrCreateDefaultProject(brandName);
        UUID workspaceId = projectEntity.getWorkspaceId();
        return TenantContext.executeWithTenant(workspaceId, () -> {
            if (idempotencyKey != null) {
                var existing = jobRepository.findByTenantIdAndCreateIdempotencyKey(workspaceId.toString(), idempotencyKey);
                if (existing.isPresent()) {
                    return new JobCreateOutcome(existing.get(), false);
                }
            }
            try {
                var created = jobCreateTransactionTemplate.execute(status -> {
                    JobEntity jobEntity = new JobEntity();
                    jobEntity.setBrandName(brandName);
                    jobEntity.setWorkspaceId(workspaceId);
                    jobEntity.setProjectId(projectEntity.getId());
                    jobEntity.setCreateIdempotencyKey(idempotencyKey);
                    String pbc = projectEntity.getBrandColor();
                    jobEntity.setBrandColor(pbc != null && !pbc.isBlank() ? pbc : "#4F46E5");
                    jobEntity.setLogoUrl(projectEntity.getLogoUrl());
                    return jobRepository.save(jobEntity);
                });
                return new JobCreateOutcome(created, true);
            } catch (DataIntegrityViolationException exception) {
                if (idempotencyKey == null) {
                    throw exception;
                }
                return jobRepository.findByTenantIdAndCreateIdempotencyKey(workspaceId.toString(), idempotencyKey)
                    .map(jobEntity -> new JobCreateOutcome(jobEntity, false))
                    .orElseThrow(() -> exception);
            }
        });
    }
    @Transactional
    public void registerQueriesAndTransitionToFileUploaded(UUID jobId, List<String> queryTexts, SubscriptionPlan subscriptionPlan) {
        registerQueriesWithTransition(jobId, queryTexts, JobStatus.FILE_UPLOADED, subscriptionPlan);
    }
    @Transactional
    public void registerQueriesAndTransitionToRealtimeProcessing(UUID jobId, List<String> queryTexts, SubscriptionPlan subscriptionPlan) {
        registerQueriesWithTransition(jobId, queryTexts, JobStatus.REALTIME_PROCESSING, subscriptionPlan);
    }
    private void registerQueriesWithTransition(
            UUID jobId,
            List<String> queryTexts,
            JobStatus nextStatus,
            SubscriptionPlan subscriptionPlan) {
        UUID tenantId = readWorkspaceIdForJob(jobId);
        TenantContext.executeWithTenant(tenantId, () -> {
            JobEntity jobEntity = jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));
            if (jobEntity.getJobStatus() != JobStatus.CREATED) {
                throw new IllegalStateException(
                    "Queries can only be added to a CREATED job. Current status: " + jobEntity.getJobStatus());
            }
            queryTexts.forEach(queryText -> {
                QueryEntity queryEntity = new QueryEntity();
                queryEntity.setJobId(jobId);
                queryEntity.setWorkspaceId(jobEntity.getWorkspaceId());
                queryEntity.setQueryText(queryText);
                queryRepository.save(queryEntity);
            });
            jobEntity.setAppliedPlan(subscriptionPlan);
            jobEntity.setJobStatus(nextStatus);
            jobRepository.save(jobEntity);
            jobStatusBroadcastPublisher.publish(jobEntity);
        });
    }
    @Transactional
    public void updateJobStatus(UUID jobId, JobStatus newJobStatus, String errorMessage) {
        UUID tenantId = readWorkspaceIdForJob(jobId);
        TenantContext.executeWithTenant(tenantId, () -> {
            JobEntity jobEntity = jobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));
            jobEntity.setJobStatus(newJobStatus);
            jobEntity.setErrorMessage(errorMessage);
            jobRepository.save(jobEntity);
        });
    }
    @Transactional
    public void updateJobStatusToRunningWithGeminiJobName(UUID jobId, String geminiJobName) {
        UUID tenantId = readWorkspaceIdForJob(jobId);
        TenantContext.executeWithTenant(tenantId, () -> {
            JobEntity jobEntity = jobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));
            jobEntity.setJobStatus(JobStatus.RUNNING);
            jobEntity.setGeminiJobName(geminiJobName);
            jobRepository.save(jobEntity);
        });
    }
    @Transactional
    public void updateJobStatusToSubmittedWithGeminiJobName(UUID jobId, String geminiJobName) {
        UUID tenantId = readWorkspaceIdForJob(jobId);
        TenantContext.executeWithTenant(tenantId, () -> {
            JobEntity jobEntity = jobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));
            jobEntity.setJobStatus(JobStatus.SUBMITTED);
            jobEntity.setGeminiJobName(geminiJobName);
            jobRepository.save(jobEntity);
        });
    }
    @Transactional
    public PdfGenerationStartResult tryMarkPdfGeneratingAndPublish(UUID jobId) {
        UUID tenantId = readWorkspaceIdForJob(jobId);
        return TenantContext.executeWithTenant(tenantId, () -> {
            JobEntity jobEntity = jobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));
            if (auditHistoryRepository.findByJobId(jobId).isEmpty()) {
                return new PdfGenerationStartResult(
                    false,
                    "解析結果がまだありません。ジョブ完了後に再度お試しください。");
            }
            jobEntity.setPdfStatus(PdfJobStatusValues.GENERATING);
            jobEntity.setPdfFilePath(null);
            JobEntity saved = jobRepository.save(jobEntity);
            jobStatusBroadcastPublisher.publish(saved);
            return new PdfGenerationStartResult(true, null);
        });
    }
    @Transactional
    public JobEntity markPdfCompletedAndPublish(UUID jobId, String absolutePath) {
        UUID tenantId = readWorkspaceIdForJob(jobId);
        return TenantContext.executeWithTenant(tenantId, () -> {
            JobEntity jobEntity = jobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));
            jobEntity.setPdfStatus(PdfJobStatusValues.COMPLETED);
            jobEntity.setPdfFilePath(absolutePath);
            JobEntity saved = jobRepository.save(jobEntity);
            jobStatusBroadcastPublisher.publish(saved);
            return saved;
        });
    }
    @Transactional
    public JobEntity markPdfFailedAndPublish(UUID jobId) {
        UUID tenantId = readWorkspaceIdForJob(jobId);
        return TenantContext.executeWithTenant(tenantId, () -> {
            JobEntity jobEntity = jobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));
            jobEntity.setPdfStatus(PdfJobStatusValues.FAILED);
            jobEntity.setPdfFilePath(null);
            JobEntity saved = jobRepository.save(jobEntity);
            jobStatusBroadcastPublisher.publish(saved);
            return saved;
        });
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = false)
    public void markPdfFailedBestEffort(UUID jobId) {
        try {
            UUID tenantId = readWorkspaceIdForJob(jobId);
            TenantContext.executeWithTenant(tenantId, () -> {
                jobRepository.findById(jobId).ifPresent(jobEntity -> {
                    jobEntity.setPdfStatus(PdfJobStatusValues.FAILED);
                    jobEntity.setPdfFilePath(null);
                    JobEntity saved = jobRepository.save(jobEntity);
                    jobStatusBroadcastPublisher.publish(saved);
                });
            });
        } catch (Exception exception) {
        }
    }
    @Transactional
    public void updateJobStrategyRollup(UUID jobId, String diagnosticMessage, List<String> recommendedActions) {
        UUID tenantId = readWorkspaceIdForJob(jobId);
        TenantContext.executeWithTenant(tenantId, () -> {
            JobEntity jobEntity = jobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));
            jobEntity.setJobDiagnosticMessage(diagnosticMessage);
            jobEntity.setJobRecommendedActions(
                recommendedActions != null ? new ArrayList<>(recommendedActions) : null);
            jobRepository.save(jobEntity);
        });
    }
    @Transactional
    public Optional<UUID> claimGapBatchIdempotencyKeyForUpdate(UUID jobId) {
        UUID tenantId = readWorkspaceIdForJob(jobId);
        return TenantContext.executeWithTenant(tenantId, () -> {
            JobEntity jobEntity = jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));
            SubscriptionPlan applied = jobEntity.getAppliedPlan();
            if (applied == null || !applied.usesProTierFeatures()) {
                return Optional.empty();
            }
            String existingName = jobEntity.getGapAnalysisGeminiJobName();
            if (existingName != null && !existingName.isBlank()) {
                return Optional.empty();
            }
            if (jobEntity.getGapBatchIdempotencyKey() == null) {
                jobEntity.setGapBatchIdempotencyKey(UUID.randomUUID());
                jobRepository.save(jobEntity);
            }
            return Optional.of(jobEntity.getGapBatchIdempotencyKey());
        });
    }
    @Transactional
    public void saveGapAnalysisGeminiJobName(UUID jobId, String geminiJobName) {
        UUID tenantId = readWorkspaceIdForJob(jobId);
        TenantContext.executeWithTenant(tenantId, () -> {
            JobEntity jobEntity = jobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));
            jobEntity.setGapAnalysisGeminiJobName(geminiJobName);
            jobRepository.save(jobEntity);
        });
    }
    @Transactional
    public void markGapAnalysisCompleted(UUID jobId, boolean completed) {
        UUID tenantId = readWorkspaceIdForJob(jobId);
        TenantContext.executeWithTenant(tenantId, () -> {
            JobEntity jobEntity = jobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));
            jobEntity.setGapAnalysisCompleted(completed);
            jobRepository.save(jobEntity);
        });
    }
    public List<JobEntity> findJobsPendingGapAnalysisOutput() {
        return TenantContext.executeWithTenant(DefaultTenantIds.WORKSPACE_ID,
            () -> jobRepository.findByGapAnalysisGeminiJobNameIsNotNullAndGapAnalysisCompletedIsFalse());
    }
    public List<JobEntity> findProJobsAwaitingGapBatchCreation() {
        return TenantContext.executeWithTenant(DefaultTenantIds.WORKSPACE_ID,
            () -> jobRepository.findProJobsAwaitingGapBatchCreation(
                JobStatus.COMPLETED, SubscriptionPlan.proTierPlans()));
    }
}
