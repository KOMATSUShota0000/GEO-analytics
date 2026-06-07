package com.geo.analytics.application.service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.ConsultantOutputData;
import com.geo.analytics.application.dto.JobAnalysisAggregate;
import com.geo.analytics.application.dto.PdfGenerationStartResult;
import com.geo.analytics.application.dto.TaskDTO;
import com.geo.analytics.domain.entity.AuditHistoryEntity;
import com.geo.analytics.domain.entity.AuditRubricResultEntity;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.enums.RubricCriterionId;
import com.geo.analytics.domain.model.CompetitorProfile;
import com.geo.analytics.domain.model.RemediationTask;
import com.geo.analytics.domain.service.AiRecognitionClassifier;
import com.geo.analytics.domain.service.GeoVisibilityCalculatorService;
import com.geo.analytics.domain.entity.ProjectEntity;
import com.geo.analytics.domain.entity.WorkspaceEntity;
import com.geo.analytics.domain.entity.QueryEntity;
import com.geo.analytics.domain.enums.CompetitorExtractionMode;
import com.geo.analytics.domain.enums.JobStatus;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.model.PlanLimitsSnapshot;
import com.geo.analytics.domain.support.TextWhitespaceNormalizer;
import com.geo.analytics.infrastructure.persistence.JsonbOperations;
import com.geo.analytics.infrastructure.repository.AuditHistoryRepository;
import com.geo.analytics.infrastructure.repository.AuditRubricResultRepository;
import com.geo.analytics.infrastructure.repository.JobRepository;
import com.geo.analytics.infrastructure.repository.ProjectRepository;
import com.geo.analytics.infrastructure.repository.QueryRepository;
import com.geo.analytics.infrastructure.repository.WorkspaceRepository;
import com.geo.analytics.web.dto.RemediationTaskResponse;
import com.geo.analytics.web.dto.ContentEvidenceItemResponse;
import com.geo.analytics.web.dto.ScoreBreakdown;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import com.geo.analytics.infrastructure.tenant.TenantIdentity;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import com.geo.analytics.infrastructure.tenant.TenantPlanScope;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ScopedValue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Service
@Transactional(readOnly = true)
public class JobPersistenceService {
    private static final Logger log = LoggerFactory.getLogger(JobPersistenceService.class);
    private static final int STACK_TRACE_LIMIT = 20_000;
    private static final TypeReference<List<RemediationTask>> REMEDIATION_LIST_TYPE = new TypeReference<>() {};

    public record JobCreateOutcome(JobEntity jobEntity, boolean created) {}
    public record JobCreateFields(
            String brandName,
            String targetUrl,
            String businessSummary,
            String targetAudience,
            String focusPoints,
            CompetitorExtractionMode competitorExtractionMode) {
        public JobCreateFields {
            brandName = TextWhitespaceNormalizer.normalize(brandName);
            targetUrl = TextWhitespaceNormalizer.normalize(targetUrl);
            if (targetUrl == null || targetUrl.isBlank()) {
                throw new IllegalArgumentException("targetUrl must not be blank");
            }
            businessSummary = optionalText(businessSummary);
            targetAudience = optionalText(targetAudience);
            focusPoints = optionalText(focusPoints);
            if (competitorExtractionMode == null) {
                competitorExtractionMode = CompetitorExtractionMode.LOCAL_STORE;
            }
        }

        private static String optionalText(String value) {
            if (value == null) {
                return null;
            }
            String s = value.strip();
            return s.isEmpty() ? null : s;
        }
    }
    public record JobAnalysisAttachment(
            ScoreBreakdown scoreBreakdown,
            List<RemediationTaskResponse> remediationTasks,
            List<ContentEvidenceItemResponse> contentEvidence) {
        public JobAnalysisAttachment {
            remediationTasks = remediationTasks != null ? List.copyOf(remediationTasks) : List.of();
            contentEvidence = contentEvidence != null ? List.copyOf(contentEvidence) : List.of();
        }
    }
    private final JobRepository jobRepository;
    private final QueryRepository queryRepository;
    private final AuditHistoryRepository auditHistoryRepository;
    private final AuditRubricResultRepository auditRubricResultRepository;
    private final ProjectRepository projectRepository;
    private final ProjectManagementService projectManagementService;
    private final JdbcTemplate batchJdbcTemplate;
    private final StrategyInsightService strategyInsightService;
    private final JsonbOperations jsonbOperations;
    private final WorkspaceRepository workspaceRepository;
    private final ObjectMapper objectMapper;
    public JobPersistenceService(
            JobRepository jobRepository,
            QueryRepository queryRepository,
            AuditHistoryRepository auditHistoryRepository,
            AuditRubricResultRepository auditRubricResultRepository,
            ProjectRepository projectRepository,
            ProjectManagementService projectManagementService,
            @Qualifier("batchJdbcTemplate") JdbcTemplate batchJdbcTemplate,
            StrategyInsightService strategyInsightService,
            JsonbOperations jsonbOperations,
            WorkspaceRepository workspaceRepository,
            ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.queryRepository = queryRepository;
        this.auditHistoryRepository = auditHistoryRepository;
        this.auditRubricResultRepository = auditRubricResultRepository;
        this.projectRepository = projectRepository;
        this.projectManagementService = projectManagementService;
        this.batchJdbcTemplate = batchJdbcTemplate;
        this.strategyInsightService = strategyInsightService;
        this.jsonbOperations = jsonbOperations;
        this.workspaceRepository = workspaceRepository;
        this.objectMapper = objectMapper;
    }
    private UUID readWorkspaceIdForJob(UUID jobId) {
        List<String> rows = batchJdbcTemplate.query(
            "SELECT tenant_id FROM jobs WHERE id = ?",
            ps -> ps.setObject(1, jobId),
            (rs, rowNum) -> rs.getString(1));
        if (rows.isEmpty() || rows.get(0) == null || rows.get(0).isBlank()) {
            return DefaultTenantIds.WORKSPACE_ID;
        }
        return UUID.fromString(rows.get(0));
    }
    private SubscriptionPlan readSubscriptionPlanForJob(UUID jobId) {
        List<String> rows = batchJdbcTemplate.query(
                "SELECT subscription_plan FROM jobs WHERE id = ?",
                ps -> ps.setObject(1, jobId),
                (rs, rowNum) -> rs.getString(1));
        if (rows.isEmpty()) {
            return SubscriptionPlan.STANDARD;
        }
        String pl = rows.get(0);
        if (pl == null || pl.isBlank()) {
            return SubscriptionPlan.STANDARD;
        }
        return SubscriptionPlan.valueOf(pl);
    }
    private UUID readWorkspaceIdForProject(UUID projectId) {
        List<String> rows = batchJdbcTemplate.query(
                "SELECT tenant_id FROM projects WHERE id = ?",
                ps -> ps.setObject(1, projectId),
                (rs, rowNum) -> rs.getString(1));
        if (rows.isEmpty() || rows.get(0) == null || rows.get(0).isBlank()) {
            return DefaultTenantIds.WORKSPACE_ID;
        }
        return UUID.fromString(rows.get(0));
    }
    private UUID readWorkspaceIdForQuery(UUID queryId) {
        List<String> rows = batchJdbcTemplate.query(
            "SELECT j.tenant_id FROM jobs j INNER JOIN job_queries q ON q.job_id = j.id WHERE q.id = ?",
            ps -> ps.setObject(1, queryId),
            (rs, rowNum) -> rs.getString(1));
        if (rows.isEmpty() || rows.get(0) == null || rows.get(0).isBlank()) {
            return DefaultTenantIds.WORKSPACE_ID;
        }
        return UUID.fromString(rows.get(0));
    }
    private UUID readWorkspaceIdForAudit(UUID auditHistoryId) {
        List<String> rows = batchJdbcTemplate.query(
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
        return TenantPlanScope.executeWithTenant(tenantId, () -> jobRepository.findById(jobId)
            .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId)));
    }
    public Optional<JobEntity> findJobByIdOptional(UUID jobId) {
        UUID tenantId = readWorkspaceIdForJob(jobId);
        return TenantPlanScope.executeWithTenant(tenantId, () -> jobRepository.findById(jobId));
    }
    public List<JobEntity> findJobsByStatus(JobStatus jobStatus) {
        return TenantPlanScope.executeWithTenant(DefaultTenantIds.WORKSPACE_ID, () -> jobRepository.findByJobStatus(jobStatus));
    }
    public List<QueryEntity> findUnprocessedQueriesByJobId(UUID jobId) {
        UUID tenantId = readWorkspaceIdForJob(jobId);
        return TenantPlanScope.executeWithTenant(tenantId, () -> queryRepository.findByJobIdAndProcessedFalse(jobId));
    }
    public List<QueryEntity> findQueriesByJobId(UUID jobId) {
        UUID tenantId = readWorkspaceIdForJob(jobId);
        return TenantPlanScope.executeWithTenant(tenantId, () -> queryRepository.findByJobId(jobId));
    }

    public long countQueriesByJobId(UUID jobId) {
        UUID tenantId = readWorkspaceIdForJob(jobId);
        return TenantPlanScope.executeWithTenant(tenantId, () -> queryRepository.countByJobId(jobId));
    }
    public Optional<QueryEntity> findQueryById(UUID queryId) {
        UUID tenantId = readWorkspaceIdForQuery(queryId);
        return TenantPlanScope.executeWithTenant(tenantId, () -> queryRepository.findById(queryId));
    }
    public JobAnalysisAttachment loadJobAnalysisAttachment(UUID jobId, List<AuditHistoryEntity> audits) {
        if (jobId == null) {
            return new JobAnalysisAttachment(ScoreBreakdown.empty(), List.of(), List.of());
        }
        if (audits == null || audits.isEmpty()) {
            return new JobAnalysisAttachment(ScoreBreakdown.empty(), List.of(), List.of());
        }
        UUID tenantId = readWorkspaceIdForJob(jobId);
        return TenantPlanScope.executeWithTenant(tenantId, () -> buildAttachment(audits));
    }

    private JobAnalysisAttachment buildAttachment(List<AuditHistoryEntity> audits) {
        AuditHistoryEntity latest = audits.stream()
                .filter(Objects::nonNull)
                .max(Comparator.comparing(
                                AuditHistoryEntity::getAuditDate, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(
                                AuditHistoryEntity::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
        if (latest == null || latest.getId() == null) {
            return new JobAnalysisAttachment(ScoreBreakdown.empty(), List.of(), List.of());
        }
        UUID auditHistoryId = latest.getId();
        List<AuditRubricResultEntity> rubricRows;
        try {
            rubricRows = auditRubricResultRepository.findByAuditHistoryId(auditHistoryId);
        } catch (RuntimeException runtimeException) {
            log.error(
                    "job_analysis_attachment_rubric_load_failed jobId={} auditHistoryId={} trace={}",
                    latest.getJobId(),
                    auditHistoryId,
                    truncateStackTrace(runtimeException));
            rubricRows = List.of();
        }
        CompetitorExtractionMode mode = jobRepository.findById(latest.getJobId())
                .map(JobEntity::getCompetitorExtractionMode)
                .orElse(CompetitorExtractionMode.LOCAL_STORE);
        ScoreBreakdown breakdown = computeBreakdown(rubricRows, mode, latest.getCalculationVersion());
        List<RemediationTaskResponse> tasks = parseRemediationTasks(latest);
        return new JobAnalysisAttachment(breakdown, tasks, buildContentEvidence(rubricRows));
    }

    /**
     * コンテンツの充実度（ルーブリックLLM10項目）のサイト固有エビデンスを組み立てる。
     * 自社（is_self）のLLM項目のみを enum 定義順で並べ、各項目の判定＋本文直接引用＋スコアを露出する。
     */
    private static List<ContentEvidenceItemResponse> buildContentEvidence(List<AuditRubricResultEntity> rubricRows) {
        if (rubricRows == null || rubricRows.isEmpty()) {
            return List.of();
        }
        List<ContentEvidenceItemResponse> out = new ArrayList<>();
        for (AuditRubricResultEntity row : rubricRows) {
            if (row == null || !row.isSelf()) {
                continue;
            }
            RubricCriterionId criterion = parseContentCriterion(row.getCriterionId());
            if (criterion == null || criterion.source() != RubricCriterionId.Source.LLM) {
                continue;
            }
            out.add(new ContentEvidenceItemResponse(
                    row.getCriterionId(),
                    row.getVerdict(),
                    row.getEvidence() != null ? row.getEvidence() : "",
                    row.getScore() != null ? row.getScore().doubleValue() : 0.0d,
                    criterion.maxScore()));
        }
        out.sort(Comparator.comparingInt(e -> contentCriterionOrder(e.criterionId())));
        return out;
    }

    private static RubricCriterionId parseContentCriterion(String criterionId) {
        if (criterionId == null) {
            return null;
        }
        try {
            return RubricCriterionId.valueOf(criterionId);
        } catch (IllegalArgumentException illegalArgumentException) {
            return null;
        }
    }

    private static int contentCriterionOrder(String criterionId) {
        RubricCriterionId criterion = parseContentCriterion(criterionId);
        return criterion != null ? criterion.ordinal() : Integer.MAX_VALUE;
    }

    private static ScoreBreakdown computeBreakdown(
            List<AuditRubricResultEntity> rubricRows, CompetitorExtractionMode mode, String calculationVersion) {
        if (rubricRows == null || rubricRows.isEmpty()) {
            return ScoreBreakdown.empty();
        }
        double aiAuditTotal = 0.0d;
        double meoTotal = 0.0d;
        double machineReadabilityTotal = 0.0d;
        double thirdPartyCoreTotal = 0.0d;
        for (int i = 0; i < rubricRows.size(); i++) {
            AuditRubricResultEntity row = rubricRows.get(i);
            if (row == null || !row.isSelf()) {
                continue;
            }
            RubricCriterionId criterion = parseCriterion(row.getCriterionId());
            if (criterion == null) {
                continue;
            }
            BigDecimal scoreBd = row.getScore();
            if (scoreBd == null) {
                continue;
            }
            double score = scoreBd.doubleValue();
            switch (criterion.source()) {
                case LLM -> aiAuditTotal = StrictMath.fma(score, 1.0d, aiAuditTotal);
                case SYSTEM -> machineReadabilityTotal = StrictMath.fma(score, 1.0d, machineReadabilityTotal);
                case MEO -> meoTotal = StrictMath.fma(score, 1.0d, meoTotal);
                case AUTHORITY -> thirdPartyCoreTotal = StrictMath.fma(score, 1.0d, thirdPartyCoreTotal);
            }
        }
        // V13_GEO4AXIS の3軸内訳を露出する（Sprint4a-1）。小計は GeoVisibilityCalculatorService と単一ソースで一致させ、
        // content + technical + authority = finalScore が整合する（権威=中核+ローカルMEOサブ、ボーナスは Sprint5 で当面0）。
        double content = StrictMath.max(0.0d, StrictMath.min(aiAuditTotal, ScoreBreakdown.MAX_CONTENT));
        double technical = GeoVisibilityCalculatorService.technicalSubScore(machineReadabilityTotal);
        double thirdPartyCore = GeoVisibilityCalculatorService.authorityThirdPartyCore(thirdPartyCoreTotal, mode);
        double localMeoSub = GeoVisibilityCalculatorService.authorityLocalMeoSub(meoTotal, mode);
        double authority = GeoVisibilityCalculatorService.combineAuthority(thirdPartyCoreTotal, meoTotal, mode);
        double wikipediaKgBonus = 0.0d;
        double finalScore = GeoVisibilityCalculatorService.calculateFinalGeoScore(
                aiAuditTotal, machineReadabilityTotal, authority);
        return new ScoreBreakdown(
                aiAuditTotal, meoTotal, machineReadabilityTotal, finalScore,
                content, technical, authority, thirdPartyCore, localMeoSub, wikipediaKgBonus, calculationVersion);
    }

    private List<RemediationTaskResponse> parseRemediationTasks(AuditHistoryEntity history) {
        if (history == null) {
            return List.of();
        }
        String json = history.getJobRecommendedActionsJson();
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<RemediationTask> parsed = objectMapper.readValue(json, REMEDIATION_LIST_TYPE);
            if (parsed == null || parsed.isEmpty()) {
                return List.of();
            }
            ArrayList<RemediationTaskResponse> out = new ArrayList<>(parsed.size());
            for (int i = 0; i < parsed.size(); i++) {
                RemediationTask task = parsed.get(i);
                RemediationTaskResponse mapped = RemediationTaskResponse.from(task);
                if (mapped != null) {
                    out.add(mapped);
                }
            }
            return List.copyOf(out);
        } catch (Exception exception) {
            log.error(
                    "job_analysis_attachment_remediation_parse_failed auditHistoryId={} trace={}",
                    history.getId(),
                    truncateStackTrace(exception));
            return List.of();
        }
    }

    /**
     * コンサル出力 JSON から prioritizedTasks の title を抽出する（戦略診断の動的化用・ADR-020）。
     * rawResponse がコンサル JSON でない（パース失敗時の生テキスト保存）場合は空を返し、
     * 呼び出し側を SoM 帯テンプレートへフォールバックさせる。
     */
    private List<String> extractSiteTaskHints(String consultantJson) {
        if (consultantJson == null || consultantJson.isBlank()) {
            return List.of();
        }
        try {
            ConsultantOutputData data = objectMapper.readValue(consultantJson, ConsultantOutputData.class);
            List<TaskDTO> tasks = data.prioritizedTasks();
            if (tasks == null || tasks.isEmpty()) {
                return List.of();
            }
            ArrayList<String> titles = new ArrayList<>(tasks.size());
            for (int i = 0; i < tasks.size(); i++) {
                TaskDTO task = tasks.get(i);
                if (task == null) {
                    continue;
                }
                String title = task.title();
                if (title != null && !title.isBlank()) {
                    titles.add(title.strip());
                }
            }
            return List.copyOf(titles);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private static RubricCriterionId parseCriterion(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return RubricCriterionId.valueOf(name);
        } catch (IllegalArgumentException illegalArgumentException) {
            return null;
        }
    }

    private static String truncateStackTrace(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        String full = stringWriter.toString();
        if (full.length() <= STACK_TRACE_LIMIT) {
            return full;
        }
        return full.substring(0, STACK_TRACE_LIMIT);
    }

    public List<AuditHistoryEntity> findResultsByJobId(UUID jobId) {
        UUID tenantId = readWorkspaceIdForJob(jobId);
        return TenantPlanScope.executeWithTenant(tenantId, () -> auditHistoryRepository.findByJobId(jobId));
    }
    public JobAnalysisAggregate findJobAnalysisAggregate(UUID jobId) {
        UUID tenantId = readWorkspaceIdForJob(jobId);
        return TenantPlanScope.executeWithTenant(tenantId, () -> {
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
            Double somScore,
            boolean brandMentioned,
            Integer mentionRank,
            Integer overallScore,
            String resolvedEntityLabel,
            int tokenCount,
            Integer aiCitationPosition,
            double sentimentIntensity,
            Integer visibilityStage,
            String calculationVersion,
            Double modifiedZScore,
            Double gbvsNormalizedScore,
            String modelInsightsJson) {
        UUID tenantId = readWorkspaceIdForJob(jobId);
        TenantPlanScope.executeWithTenant(tenantId, () -> {
            JobEntity jobEntity = jobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));
            UUID workspaceId = jobEntity.getWorkspaceId() != null ? jobEntity.getWorkspaceId() : DefaultTenantIds.WORKSPACE_ID;
            UUID projectId = Objects.requireNonNull(jobEntity.getProjectId(), "projectId");
            ProjectEntity projectEntity = projectRepository.getReferenceById(projectId);
            var negativeAlert = sentimentIntensity < -0.5;
            // AIがブランドを正しい実体として認識しているかの定性ステート（V13 Sprint3）。
            // SoM測定で得た応答由来のシグナル（言及有無・正規化済み実体ラベル）と顧客の正規ブランド名から導出する。
            // 追加API呼び出しはなく、スコア計算にも一切渡さない（SoMとの二重計上回避＝ADR-024）。
            var aiRecognitionState = AiRecognitionClassifier.classify(
                brandMentioned, resolvedEntityLabel, jobEntity.getBrandName());
            // 定型文を排し、SoM 絶対値・引用順位に加えサイト固有の優先改善タスクを後半文へ反映する（ADR-018/020）。
            // rawResponse はコンサル出力 JSON（prioritizedTasks を含む）。パース不能時は帯テンプレへフォールバックする。
            var siteTaskHints = extractSiteTaskHints(rawResponse);
            var insight = strategyInsightService.describeForQuery(somScore, aiCitationPosition, siteTaskHints);
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
                existing.setAiCitationPosition(normalizedAiCitationPosition(aiCitationPosition));
                existing.setSentimentIntensity(sentimentIntensity);
                existing.setVisibilityStage(visibilityStage);
                existing.setCalculationVersion(calculationVersion);
                existing.setNegativeAlert(negativeAlert);
                existing.setAiRecognitionState(aiRecognitionState);
                existing.setModifiedZScore(modifiedZScore);
                existing.setGbvsNormalizedScore(gbvsNormalizedScore);
                existing.setDiagnosticMessage(insight.diagnosticMessage());
                existing.setRecommendedActions(actions);
                existing.setAuditDate(LocalDate.now());
                existing.setWorkspaceId(workspaceId);
                existing.setModelInsightsJson(modelInsightsJson);
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
                auditHistoryEntity.setAiCitationPosition(normalizedAiCitationPosition(aiCitationPosition));
                auditHistoryEntity.setSentimentIntensity(sentimentIntensity);
                auditHistoryEntity.setVisibilityStage(visibilityStage);
                auditHistoryEntity.setCalculationVersion(calculationVersion);
                auditHistoryEntity.setNegativeAlert(negativeAlert);
                auditHistoryEntity.setAiRecognitionState(aiRecognitionState);
                auditHistoryEntity.setModifiedZScore(modifiedZScore);
                auditHistoryEntity.setGbvsNormalizedScore(gbvsNormalizedScore);
                auditHistoryEntity.setDiagnosticMessage(insight.diagnosticMessage());
                auditHistoryEntity.setRecommendedActions(actions);
                auditHistoryEntity.setAuditDate(LocalDate.now());
                auditHistoryEntity.setModelInsightsJson(modelInsightsJson);
                auditHistoryRepository.saveAndFlush(auditHistoryEntity);
            }
            queryRepository.findById(queryId).ifPresent(queryEntity -> {
                queryEntity.setProcessed(true);
                queryRepository.save(queryEntity);
            });
        });
    }

    private static Integer normalizedAiCitationPosition(Integer position) {
        return (position != null && position > 0) ? position : null;
    }

    @Transactional
    public void updateAuditStrategyInsights(
            UUID auditHistoryId,
            String diagnosticMessage,
            List<String> recommendedActions,
            String calculationVersion) {
        UUID tenantId = readWorkspaceIdForAudit(auditHistoryId);
        TenantPlanScope.executeWithTenant(tenantId, () -> auditHistoryRepository.findById(auditHistoryId).ifPresent(entity -> {
            entity.setDiagnosticMessage(diagnosticMessage);
            entity.setRecommendedActions(new ArrayList<>(recommendedActions));
            entity.setCalculationVersion(calculationVersion);
            auditHistoryRepository.save(entity);
        }));
    }
    @Transactional(readOnly = false)
    public JobEntity createJob(JobCreateFields fields) {
        return createJobWithIdempotency(fields, null).jobEntity();
    }

    @Transactional(readOnly = false)
    public JobCreateOutcome createJobWithIdempotency(JobCreateFields fields, UUID idempotencyKey) {
        ProjectEntity projectEntity =
                projectManagementService.getOrCreateDefaultProject(fields.brandName(), fields.targetUrl());
        UUID workspaceId = projectEntity.getWorkspaceId();
        UUID orgId = DefaultTenantIds.DEFAULT_ORGANIZATION_ID;
        return runJobCreationWithProject(fields, idempotencyKey, projectEntity, workspaceId, orgId);
    }

    @Transactional(readOnly = false)
    public JobCreateOutcome createJobWithIdempotency(JobCreateFields fields, UUID idempotencyKey, UUID workspaceId) {
        ProjectManagementService.DefaultProjectResolution resolution =
                projectManagementService.getOrCreateDefaultProjectForWorkspace(
                        fields.brandName(), workspaceId, fields.targetUrl());
        ProjectEntity projectEntity = resolution.project();
        UUID resolvedWorkspaceId = projectEntity.getWorkspaceId();
        UUID orgId = resolution.organizationId();
        return runJobCreationWithProject(fields, idempotencyKey, projectEntity, resolvedWorkspaceId, orgId);
    }

    private JobCreateOutcome runJobCreationWithProject(
            JobCreateFields fields,
            UUID idempotencyKey,
            ProjectEntity projectEntity,
            UUID workspaceId,
            UUID organizationId) {
        return TenantPlanScope.executeWithTenant(workspaceId, () -> {
            if (idempotencyKey != null) {
                var existing = jobRepository.findByTenantIdAndCreateIdempotencyKey(workspaceId.toString(), idempotencyKey);
                if (existing.isPresent()) {
                    return new JobCreateOutcome(existing.get(), false);
                }
            }
            return ScopedValue.where(TenantContextHolder.CONTEXT, new TenantIdentity(organizationId, workspaceId, null))
                    .call(
                            () -> {
                                try {
                                    var created =
                                            saveNewJob(
                                                    fields,
                                                    workspaceId,
                                                    projectEntity.getId(),
                                                    idempotencyKey,
                                                    projectEntity.getBrandColor(),
                                                    projectEntity.getLogoUrl());
                                    return new JobCreateOutcome(created, true);
                                } catch (DataIntegrityViolationException exception) {
                                    if (idempotencyKey == null) {
                                        throw exception;
                                    }
                                    return jobRepository
                                            .findByTenantIdAndCreateIdempotencyKey(
                                                    workspaceId.toString(), idempotencyKey)
                                            .map(jobEntity -> new JobCreateOutcome(jobEntity, false))
                                            .orElseThrow(() -> exception);
                                }
                            });
        });
    }
    private JobEntity saveNewJob(
            JobCreateFields fields,
            UUID workspaceId,
            UUID projectId,
            UUID idempotencyKey,
            String brandColor,
            String logoUrl) {
        JobEntity jobEntity = new JobEntity();
        jobEntity.setBrandName(fields.brandName());
        jobEntity.setTargetUrl(fields.targetUrl());
        jobEntity.setBusinessSummary(fields.businessSummary());
        jobEntity.setTargetAudience(fields.targetAudience());
        jobEntity.setFocusPoints(fields.focusPoints());
        jobEntity.setCompetitorExtractionMode(fields.competitorExtractionMode());
        jobEntity.setWorkspaceId(workspaceId);
        jobEntity.setProjectId(projectId);
        jobEntity.setCreateIdempotencyKey(idempotencyKey);
        jobEntity.setBrandColor(brandColor != null && !brandColor.isBlank() ? brandColor : "#4F46E5");
        jobEntity.setLogoUrl(logoUrl);
        return jobRepository.saveAndFlush(jobEntity);
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
        List<String> normalizedQueryTexts =
                queryTexts == null
                        ? null
                        : queryTexts.stream().map(TextWhitespaceNormalizer::normalize).toList();
        UUID tenantId = readWorkspaceIdForJob(jobId);
        SubscriptionPlan resolvedPlan =
                Objects.requireNonNull(subscriptionPlan, "subscriptionPlan");
        String planLimitsSnapshotJson =
                jsonbOperations.serialize(PlanLimitsSnapshot.fromPlan(resolvedPlan));
        TenantPlanScope.executeWithTenant(tenantId, () -> {
            JobEntity jobEntity = jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));
            JobStatus currentStatus = jobEntity.getJobStatus();
            if (currentStatus != JobStatus.CREATED && currentStatus != JobStatus.EXTRACTING_COMPETITORS) {
                throw new IllegalStateException(
                    "Queries can only be added to a CREATED or EXTRACTING_COMPETITORS job. Current status: "
                            + jobEntity.getJobStatus());
            }
            normalizedQueryTexts.forEach(queryText -> {
                QueryEntity queryEntity = new QueryEntity();
                queryEntity.setJobId(jobId);
                queryEntity.setWorkspaceId(jobEntity.getWorkspaceId());
                queryEntity.setQueryText(queryText);
                queryRepository.saveAndFlush(queryEntity);
            });
            jobEntity.setAppliedPlan(resolvedPlan);
            jobEntity.setPlanLimitsSnapshot(planLimitsSnapshotJson);
            jobEntity.setJobStatus(nextStatus);
            jobRepository.save(jobEntity);
        });
    }
    @Transactional
    public void updateJobStatus(UUID jobId, JobStatus newJobStatus, String errorMessage) {
        UUID tenantId = readWorkspaceIdForJob(jobId);
        TenantPlanScope.executeWithTenant(tenantId, () -> {
            JobEntity jobEntity = jobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));
            jobEntity.setJobStatus(newJobStatus);
            jobEntity.setErrorMessage(errorMessage);
            jobRepository.save(jobEntity);
        });
    }
    @Transactional
    public void updateExtractedKnowledge(UUID jobId, String extractedKnowledge) {
        UUID tenantId = readWorkspaceIdForJob(jobId);
        TenantPlanScope.executeWithTenant(tenantId, () -> {
            JobEntity jobEntity = jobRepository.findById(jobId)
                    .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));
            jobEntity.setExtractedKnowledge(extractedKnowledge);
            jobRepository.save(jobEntity);
        });
    }
    @Transactional
    public void persistJobBenchmarkSnapshot(
            UUID jobId,
            String selfRubricJson,
            String competitorRubricsJson,
            String selfCrawlJson,
            Integer meoReviewCount,
            Double meoAverageStars) {
        UUID tenantId = readWorkspaceIdForJob(jobId);
        SubscriptionPlan plan = readSubscriptionPlanForJob(jobId);
        UUID orgId =
                workspaceRepository.findById(tenantId).map(WorkspaceEntity::getOrganizationId).orElse(
                        DefaultTenantIds.DEFAULT_ORGANIZATION_ID);
        TenantPlanScope.executeWithTenantOrganizationAndPlan(tenantId, orgId, plan, () -> {
            JobEntity entity =
                    jobRepository
                            .findById(jobId)
                            .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));
            entity.setSelfRubricAuditJson(selfRubricJson);
            entity.setCompetitorRubricAuditsJson(competitorRubricsJson);
            entity.setSelfCrawledPageJson(selfCrawlJson);
            entity.setMeoReviewCount(meoReviewCount);
            entity.setMeoAverageStars(meoAverageStars);
            jobRepository.save(entity);
        });
    }
    @Transactional
    public void saveJobEmotionalAlert(UUID jobId, String alertJson) {
        if (jobId == null) {
            throw new IllegalArgumentException("jobId");
        }
        UUID tenantId = readWorkspaceIdForJob(jobId);
        SubscriptionPlan plan = readSubscriptionPlanForJob(jobId);
        UUID orgId =
                workspaceRepository.findById(tenantId).map(WorkspaceEntity::getOrganizationId).orElse(
                        DefaultTenantIds.DEFAULT_ORGANIZATION_ID);
        TenantPlanScope.executeWithTenantOrganizationAndPlan(tenantId, orgId, plan, () -> {
            JobEntity entity =
                    jobRepository.findById(jobId).orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));
            entity.setEmotionalAlertJson(alertJson);
            jobRepository.save(entity);
        });
    }
    public String loadTargetUrlForProject(UUID projectId) {
        UUID tenantId = readWorkspaceIdForProject(projectId);
        return TenantPlanScope.executeWithTenant(tenantId, () -> projectRepository.findById(projectId)
                .map(ProjectEntity::getTargetUrl)
                .orElse(""));
    }
    @Transactional
    public void saveProjectCompetitorUrls(UUID projectId, List<String> competitorUrls) {
        UUID tenantId = readWorkspaceIdForProject(projectId);
        TenantPlanScope.executeWithTenant(tenantId, () -> {
            projectRepository
                    .findById(projectId)
                    .ifPresent(projectEntity -> {
                        projectEntity.setCompetitorUrls(normalizeThreeUrls(competitorUrls));
                        projectRepository.save(projectEntity);
                    });
        });
    }
    @Transactional
    public void saveProjectCompetitorProfiles(UUID projectId, List<CompetitorProfile> profiles) {
        UUID tenantId = readWorkspaceIdForProject(projectId);
        TenantPlanScope.executeWithTenant(tenantId, () -> {
            projectRepository
                    .findById(projectId)
                    .ifPresent(projectEntity -> {
                        projectEntity.setCompetitorProfiles(normalizeThreeProfiles(profiles));
                        projectRepository.save(projectEntity);
                    });
        });
    }
    private static List<CompetitorProfile> normalizeThreeProfiles(List<CompetitorProfile> profiles) {
        List<CompetitorProfile> out = new ArrayList<>();
        if (profiles != null) {
            for (CompetitorProfile profile : profiles) {
                if (profile != null) {
                    out.add(profile);
                }
                if (out.size() == 3) {
                    return new ArrayList<>(out);
                }
            }
        }
        return new ArrayList<>(out);
    }
    private static List<String> normalizeThreeUrls(List<String> urls) {
        List<String> out = new ArrayList<>();
        if (urls != null) {
            for (String url : urls) {
                out.add(url != null ? url : "");
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
    @Transactional
    public void updateJobStatusToRunningWithGeminiJobName(UUID jobId, String geminiJobName) {
        UUID tenantId = readWorkspaceIdForJob(jobId);
        TenantPlanScope.executeWithTenant(tenantId, () -> {
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
        TenantPlanScope.executeWithTenant(tenantId, () -> {
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
        return TenantPlanScope.executeWithTenant(tenantId, () -> {
            jobRepository.findById(jobId)
                    .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));
            if (auditHistoryRepository.findByJobId(jobId).isEmpty()) {
                return new PdfGenerationStartResult(
                        false,
                        "解析結果がまだありません。ジョブ完了後に再度お試しください。");
            }
            return new PdfGenerationStartResult(
                    false,
                    "サーバー側のPDF自動生成は廃止されました。ブラウザの印刷機能などをご利用ください。");
        });
    }

    @Transactional
    public void updateJobStrategyRollup(UUID jobId, String diagnosticMessage, List<String> recommendedActions) {
        UUID tenantId = readWorkspaceIdForJob(jobId);
        TenantPlanScope.executeWithTenant(tenantId, () -> {
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
        return TenantPlanScope.executeWithTenant(tenantId, () -> {
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
        TenantPlanScope.executeWithTenant(tenantId, () -> {
            JobEntity jobEntity = jobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));
            jobEntity.setGapAnalysisGeminiJobName(geminiJobName);
            jobRepository.save(jobEntity);
        });
    }
    @Transactional
    public void markGapAnalysisCompleted(UUID jobId, boolean completed) {
        UUID tenantId = readWorkspaceIdForJob(jobId);
        TenantPlanScope.executeWithTenant(tenantId, () -> {
            JobEntity jobEntity = jobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));
            jobEntity.setGapAnalysisCompleted(completed);
            jobRepository.save(jobEntity);
        });
    }
    public List<JobEntity> findJobsPendingGapAnalysisOutput() {
        return TenantPlanScope.executeWithTenant(DefaultTenantIds.WORKSPACE_ID,
            () -> jobRepository.findByGapAnalysisGeminiJobNameIsNotNullAndGapAnalysisCompletedIsFalse());
    }
    public List<JobEntity> findProJobsAwaitingGapBatchCreation() {
        return TenantPlanScope.executeWithTenant(DefaultTenantIds.WORKSPACE_ID,
            () -> jobRepository.findProJobsAwaitingGapBatchCreation(
                JobStatus.COMPLETED, SubscriptionPlan.proTierPlans()));
    }

}
