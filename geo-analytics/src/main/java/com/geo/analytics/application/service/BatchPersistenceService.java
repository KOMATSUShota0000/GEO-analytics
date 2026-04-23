package com.geo.analytics.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.CompetitorScoreRow;
import com.geo.analytics.domain.entity.AuditHistoryEntity;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.QueryEntity;
import com.geo.analytics.domain.enums.JobStatus;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.infrastructure.persistence.GlobalAccess;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ジョブ／監査などバッチ系テーブルへの JDBC アクセス。{@code batchTransactionManager} 上のトランザクションで実行される。
 *
 * <p>{@link GlobalAccess} を型に付与し、{@code @Scheduled} や非同期コールバックなどテナント未束縛の経路からの DB アクセスを
 * {@link com.geo.analytics.infrastructure.persistence.RlsConnectionInterceptor} が拒否しないようにする。
 * HTTP 等で {@link com.geo.analytics.infrastructure.tenant.TenantContextHolder} が束縛されている場合は、従来どおり RLS 用セッション変数が設定される。
 */
@GlobalAccess
@Service
@Transactional("batchTransactionManager")
public class BatchPersistenceService {

    private static final TypeReference<List<String>> LIST_STRING_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public BatchPersistenceService(
            @Qualifier("batchJdbcTemplate") JdbcTemplate batchJdbcTemplate,
            ObjectMapper objectMapper) {
        this.jdbc = batchJdbcTemplate;
        this.objectMapper = objectMapper;
    }

    private static final String JOB_COLS =
            "id, tenant_id, project_id, job_status, subscription_plan, "
            + "plan_limits_snapshot, brand_name, brand_color, logo_url, gemini_job_name, "
            + "error_message, pdf_status, pdf_file_path, created_at, updated_at, "
            + "job_diagnostic_message, job_recommended_actions, gap_batch_idempotency_key, "
            + "create_idempotency_key, gap_analysis_gemini_job_name, gap_analysis_completed";

    public JobEntity findJobById(UUID jobId) {
        return jdbc.queryForObject(
                "SELECT " + JOB_COLS + " FROM jobs WHERE id = ?",
                this::mapJobRow, jobId);
    }

    public Optional<JobEntity> findJobByIdOptional(UUID jobId) {
        List<JobEntity> rows = jdbc.query(
                "SELECT " + JOB_COLS + " FROM jobs WHERE id = ?",
                this::mapJobRow, jobId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public List<AuditHistoryEntity> findResultsByJobId(UUID jobId) {
        return jdbc.query(
                "SELECT id, tenant_id, job_id, project_id, query, raw_response, "
                        + "som_score, gbvs_normalized_score, brand_mentioned, mention_rank, overall_score, resolved_entity_label, "
                        + "token_count, rank_position, sentiment_intensity, visibility_stage, "
                        + "calculation_version, negative_alert, modified_z_score, diagnostic_message, "
                        + "recommended_actions, model_insights, audit_date, created_at "
                        + "FROM audit_histories WHERE job_id = ?",
                (rs, rn) -> mapAuditRow(rs), jobId);
    }

    public Optional<UUID> findAuditIdByJobIdAndQuery(UUID jobId, String queryText) {
        List<UUID> ids = jdbc.query(
                "SELECT id FROM audit_histories WHERE job_id = ? AND query = ?",
                (rs, rn) -> rs.getObject("id", UUID.class), jobId, queryText);
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.getFirst());
    }

    public Optional<QueryEntity> findQueryById(UUID queryId) {
        List<QueryEntity> rows = jdbc.query(
                "SELECT id, tenant_id, job_id, query_text, processed FROM job_queries WHERE id = ?",
                BatchPersistenceService::mapQueryRow, queryId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public List<QueryEntity> findQueriesByJobId(UUID jobId) {
        return jdbc.query(
                "SELECT id, tenant_id, job_id, query_text, processed FROM job_queries WHERE job_id = ?",
                BatchPersistenceService::mapQueryRow, jobId);
    }

    public List<QueryEntity> findUnprocessedQueriesByJobId(UUID jobId) {
        return jdbc.query(
                "SELECT id, tenant_id, job_id, query_text, processed FROM job_queries WHERE job_id = ? AND processed = false",
                BatchPersistenceService::mapQueryRow, jobId);
    }

    public long countQueriesByJobId(UUID jobId) {
        Long c = jdbc.queryForObject("SELECT count(*) FROM job_queries WHERE job_id = ?", Long.class, jobId);
        return c != null ? c : 0L;
    }

    public void updateJobStatus(UUID jobId, JobStatus newStatus, String errorMessage) {
        jdbc.update("UPDATE jobs SET job_status = ?, error_message = ?, updated_at = now() WHERE id = ?",
                newStatus.name(), errorMessage, jobId);
    }

    public void updateJobStatusToSubmittedWithGeminiJobName(UUID jobId, String geminiJobName) {
        jdbc.update("UPDATE jobs SET job_status = ?, gemini_job_name = ?, updated_at = now() WHERE id = ?",
                JobStatus.SUBMITTED.name(), geminiJobName, jobId);
    }

    public void markGapAnalysisCompleted(UUID jobId, boolean completed) {
        jdbc.update("UPDATE jobs SET gap_analysis_completed = ?, updated_at = now() WHERE id = ?",
                completed, jobId);
    }

    public void updateJobStrategyRollup(UUID jobId, String diagnosticMessage, List<String> recommendedActions) {
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "UPDATE jobs SET job_diagnostic_message = ?, job_recommended_actions = ?, updated_at = now() WHERE id = ?");
            ps.setString(1, diagnosticMessage);
            setJsonb(ps, 2, toJson(recommendedActions));
            ps.setObject(3, jobId);
            return ps;
        });
    }

    public void markPdfCompleted(UUID jobId, String absolutePath) {
        jdbc.update("UPDATE jobs SET pdf_status = 'COMPLETED', pdf_file_path = ?, updated_at = now() WHERE id = ?",
                absolutePath, jobId);
    }

    public void markPdfFailed(UUID jobId) {
        jdbc.update("UPDATE jobs SET pdf_status = 'FAILED', pdf_file_path = NULL, updated_at = now() WHERE id = ?",
                jobId);
    }

    public void updateAuditStrategyInsights(UUID auditHistoryId, String diagnosticMessage,
                                            List<String> recommendedActions, String calculationVersion) {
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "UPDATE audit_histories SET diagnostic_message = ?, recommended_actions = ?, "
                    + "calculation_version = ? WHERE id = ?");
            ps.setString(1, diagnosticMessage);
            setJsonb(ps, 2, toJson(recommendedActions));
            ps.setString(3, calculationVersion);
            ps.setObject(4, auditHistoryId);
            return ps;
        });
    }

    public UUID upsertAuditHistory(UUID jobId, UUID workspaceId, UUID projectId,
                                   UUID queryId, String queryText, String rawResponse,
                                   double somScore, Double gbvsNormalizedScore, boolean brandMentioned,
                                   Integer mentionRank, Integer overallScore,
                                   String resolvedEntityLabel, int tokenCount,
                                   int rankPosition, double sentimentIntensity,
                                   Integer visibilityStage, String calculationVersion,
                                   double modifiedZScore, boolean negativeAlert,
                                   String diagnosticMessage, List<String> recommendedActions,
                                   String modelInsightsJson,
                                   List<CompetitorScoreRow> competitorScoreRows) {
        Optional<UUID> existingId = findAuditIdByJobIdAndQuery(jobId, queryText);
        UUID auditId;
        if (existingId.isPresent()) {
            auditId = existingId.get();
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "UPDATE audit_histories SET raw_response = ?, som_score = ?, gbvs_normalized_score = ?, brand_mentioned = ?, "
                        + "mention_rank = ?, overall_score = ?, resolved_entity_label = ?, "
                        + "token_count = ?, rank_position = ?, sentiment_intensity = ?, "
                        + "visibility_stage = ?, calculation_version = ?, negative_alert = ?, "
                        + "modified_z_score = ?, diagnostic_message = ?, recommended_actions = ?, "
                        + "model_insights = ?, audit_date = ? WHERE id = ?");
                setJsonb(ps, 1, rawResponse);
                ps.setDouble(2, somScore);
                setNullableDouble(ps, 3, gbvsNormalizedScore);
                ps.setBoolean(4, brandMentioned);
                setNullableInt(ps, 5, mentionRank);
                setNullableInt(ps, 6, overallScore);
                ps.setString(7, resolvedEntityLabel);
                ps.setInt(8, tokenCount);
                ps.setInt(9, rankPosition);
                ps.setDouble(10, sentimentIntensity);
                setNullableInt(ps, 11, visibilityStage);
                ps.setString(12, calculationVersion);
                ps.setBoolean(13, negativeAlert);
                ps.setDouble(14, modifiedZScore);
                ps.setString(15, diagnosticMessage);
                setJsonb(ps, 16, toJson(recommendedActions));
                setJsonb(ps, 17, modelInsightsJson);
                ps.setObject(18, LocalDate.now());
                ps.setObject(19, auditId);
                return ps;
            });
        } else {
            auditId = UUID.randomUUID();
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO audit_histories (id, tenant_id, job_id, project_id, query, "
                        + "raw_response, som_score, gbvs_normalized_score, brand_mentioned, mention_rank, overall_score, "
                        + "resolved_entity_label, token_count, rank_position, sentiment_intensity, "
                        + "visibility_stage, calculation_version, negative_alert, modified_z_score, "
                        + "diagnostic_message, recommended_actions, model_insights, audit_date, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())");
                ps.setObject(1, auditId);
                ps.setString(2, workspaceId.toString());
                ps.setObject(3, jobId);
                ps.setObject(4, projectId);
                ps.setString(5, queryText);
                setJsonb(ps, 6, rawResponse);
                ps.setDouble(7, somScore);
                setNullableDouble(ps, 8, gbvsNormalizedScore);
                ps.setBoolean(9, brandMentioned);
                setNullableInt(ps, 10, mentionRank);
                setNullableInt(ps, 11, overallScore);
                ps.setString(12, resolvedEntityLabel);
                ps.setInt(13, tokenCount);
                ps.setInt(14, rankPosition);
                ps.setDouble(15, sentimentIntensity);
                setNullableInt(ps, 16, visibilityStage);
                ps.setString(17, calculationVersion);
                ps.setBoolean(18, negativeAlert);
                ps.setDouble(19, modifiedZScore);
                ps.setString(20, diagnosticMessage);
                setJsonb(ps, 21, toJson(recommendedActions));
                setJsonb(ps, 22, modelInsightsJson);
                ps.setObject(23, LocalDate.now());
                return ps;
            });
        }
        jdbc.update("DELETE FROM job_competitor_scores WHERE audit_history_id = ?", auditId);
        if (competitorScoreRows != null) {
            for (CompetitorScoreRow row : competitorScoreRows) {
                jdbc.update(
                        "INSERT INTO job_competitor_scores (id, audit_history_id, competitor_name, "
                        + "som_score, rank_position, visibility_stage, match_status, noun_count) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(), auditId, row.competitorName(),
                        row.somScore(), row.rankPosition(), row.visibilityStage(),
                        row.matchStatus() != null ? row.matchStatus().name() : null, row.nounCount());
            }
        }
        jdbc.update("UPDATE job_queries SET processed = true WHERE id = ?", queryId);
        return auditId;
    }

    public void insertSgeResult(UUID workspaceId, UUID jobId, UUID queryId,
                                String queryText, String sgeRawResponse,
                                boolean sgeMentioned, int mentionCount) {
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO sge_results (id, tenant_id, job_id, query_id, query, "
                    + "sge_raw_response, sge_mentioned, mention_count, created_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, now())");
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, workspaceId.toString());
            ps.setObject(3, jobId);
            ps.setObject(4, queryId);
            ps.setString(5, queryText);
            setJsonb(ps, 6, sgeRawResponse);
            ps.setBoolean(7, sgeMentioned);
            ps.setInt(8, mentionCount);
            return ps;
        });
    }

    public record ProjectBrandInfo(UUID id, String brandColor, String logoUrl) {}

    public Optional<ProjectBrandInfo> findProjectBrandInfo(UUID projectId) {
        List<ProjectBrandInfo> rows = jdbc.query(
                "SELECT id, brand_color, logo_url FROM projects WHERE id = ?",
                (rs, rn) -> new ProjectBrandInfo(
                        rs.getObject("id", UUID.class),
                        rs.getString("brand_color"),
                        rs.getString("logo_url")),
                projectId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public List<String> findCompetitorUrlsByProjectId(UUID projectId) {
        return jdbc.query(
                "SELECT competitor_url FROM project_competitors WHERE project_id = ?",
                (rs, rn) -> rs.getString("competitor_url"), projectId);
    }

    public Optional<UUID> findWorkspaceOrganizationId(UUID workspaceId) {
        List<UUID> rows = jdbc.query(
                "SELECT organization_id FROM workspaces WHERE id = ?",
                (rs, rn) -> rs.getObject("organization_id", UUID.class), workspaceId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public record OrgUserInfo(UUID id, String email, String passwordHash) {}

    public List<JobEntity> findJobsByStatus(JobStatus status) {
        return jdbc.query("SELECT " + JOB_COLS + " FROM jobs WHERE job_status = ?",
                this::mapJobRow, status.name());
    }

    public List<JobEntity> findJobsPendingGapAnalysisOutput() {
        return jdbc.query(
                "SELECT " + JOB_COLS + " FROM jobs WHERE gap_analysis_gemini_job_name IS NOT NULL AND gap_analysis_completed = false",
                this::mapJobRow);
    }

    public List<JobEntity> findProJobsAwaitingGapBatchCreation() {
        return jdbc.query(
                "SELECT " + JOB_COLS + " FROM jobs WHERE job_status = ? AND subscription_plan IN (?, ?)"
                + " AND gap_batch_idempotency_key IS NOT NULL"
                + " AND (gap_analysis_gemini_job_name IS NULL OR gap_analysis_gemini_job_name = '')",
                this::mapJobRow,
                JobStatus.COMPLETED.name(),
                SubscriptionPlan.PRO.name(),
                SubscriptionPlan.EXPERT.name());
    }

    public Optional<UUID> claimGapBatchIdempotencyKeyForUpdate(UUID jobId) {
        List<JobEntity> rows = jdbc.query(
                "SELECT " + JOB_COLS + " FROM jobs WHERE id = ? FOR UPDATE",
                this::mapJobRow, jobId);
        if (rows.isEmpty()) return Optional.empty();
        JobEntity job = rows.getFirst();
        SubscriptionPlan applied = job.getAppliedPlan();
        if (applied == null || !applied.usesProTierFeatures()) return Optional.empty();
        String existingName = job.getGapAnalysisGeminiJobName();
        if (existingName != null && !existingName.isBlank()) return Optional.empty();
        if (job.getGapBatchIdempotencyKey() == null) {
            UUID newKey = UUID.randomUUID();
            jdbc.update("UPDATE jobs SET gap_batch_idempotency_key = ?, updated_at = now() WHERE id = ?",
                    newKey, jobId);
            return Optional.of(newKey);
        }
        return Optional.of(job.getGapBatchIdempotencyKey());
    }

    public void saveGapAnalysisGeminiJobName(UUID jobId, String geminiJobName) {
        jdbc.update("UPDATE jobs SET gap_analysis_gemini_job_name = ?, updated_at = now() WHERE id = ?",
                geminiJobName, jobId);
    }

    public Optional<OrgUserInfo> findFirstActiveOrgUser(UUID organizationId) {
        List<OrgUserInfo> rows = jdbc.query(
                "SELECT id, email, password_hash FROM organization_users "
                + "WHERE organization_id = ? AND deleted_at IS NULL ORDER BY created_at ASC LIMIT 1",
                (rs, rn) -> new OrgUserInfo(
                        rs.getObject("id", UUID.class),
                        rs.getString("email"),
                        rs.getString("password_hash")),
                organizationId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    private JobEntity mapJobRow(ResultSet rs, int rowNum) throws SQLException {
        JobEntity job = new JobEntity();
        job.setId(rs.getObject("id", UUID.class));
        String tid = rs.getString("tenant_id");
        if (tid != null) job.setWorkspaceId(UUID.fromString(tid));
        job.setProjectId(rs.getObject("project_id", UUID.class));
        String st = rs.getString("job_status");
        if (st != null) job.setJobStatus(JobStatus.valueOf(st));
        String pl = rs.getString("subscription_plan");
        if (pl != null) job.setAppliedPlan(SubscriptionPlan.valueOf(pl));
        job.setPlanLimitsSnapshot(rs.getString("plan_limits_snapshot"));
        job.setBrandName(rs.getString("brand_name"));
        job.setBrandColor(rs.getString("brand_color"));
        job.setLogoUrl(rs.getString("logo_url"));
        job.setGeminiJobName(rs.getString("gemini_job_name"));
        job.setErrorMessage(rs.getString("error_message"));
        job.setPdfStatus(rs.getString("pdf_status"));
        job.setPdfFilePath(rs.getString("pdf_file_path"));
        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) job.setCreatedAt(ca.toLocalDateTime());
        Timestamp ua = rs.getTimestamp("updated_at");
        if (ua != null) job.setUpdatedAt(ua.toLocalDateTime());
        job.setJobDiagnosticMessage(rs.getString("job_diagnostic_message"));
        String jraJson = rs.getString("job_recommended_actions");
        if (jraJson != null) {
            try { job.setJobRecommendedActions(objectMapper.readValue(jraJson, LIST_STRING_TYPE)); }
            catch (JsonProcessingException ignored) {}
        }
        job.setGapBatchIdempotencyKey(rs.getObject("gap_batch_idempotency_key", UUID.class));
        job.setCreateIdempotencyKey(rs.getObject("create_idempotency_key", UUID.class));
        job.setGapAnalysisGeminiJobName(rs.getString("gap_analysis_gemini_job_name"));
        job.setGapAnalysisCompleted(rs.getBoolean("gap_analysis_completed"));
        return job;
    }

    private AuditHistoryEntity mapAuditRow(ResultSet rs) throws SQLException {
        AuditHistoryEntity e = new AuditHistoryEntity();
        e.setId(rs.getObject("id", UUID.class));
        String tid = rs.getString("tenant_id");
        if (tid != null) e.setWorkspaceId(UUID.fromString(tid));
        e.setJobId(rs.getObject("job_id", UUID.class));
        e.setQuery(rs.getString("query"));
        e.setRawResponse(rs.getString("raw_response"));
        double somVal = rs.getDouble("som_score");
        e.setSomScore(rs.wasNull() ? null : somVal);
        var gbvsBd = rs.getBigDecimal("gbvs_normalized_score");
        e.setGbvsNormalizedScore(gbvsBd != null ? gbvsBd.doubleValue() : null);
        e.setBrandMentioned(rs.getBoolean("brand_mentioned"));
        e.setMentionRank((Integer) rs.getObject("mention_rank"));
        e.setOverallScore((Integer) rs.getObject("overall_score"));
        e.setResolvedEntityLabel(rs.getString("resolved_entity_label"));
        e.setTokenCount(rs.getInt("token_count"));
        e.setRankPosition(rs.getInt("rank_position"));
        e.setSentimentIntensity(rs.getDouble("sentiment_intensity"));
        e.setVisibilityStage((Integer) rs.getObject("visibility_stage"));
        e.setCalculationVersion(rs.getString("calculation_version"));
        e.setNegativeAlert(rs.getBoolean("negative_alert"));
        e.setModifiedZScore((Double) rs.getObject("modified_z_score"));
        e.setDiagnosticMessage(rs.getString("diagnostic_message"));
        String raJson = rs.getString("recommended_actions");
        if (raJson != null) {
            try { e.setRecommendedActions(objectMapper.readValue(raJson, LIST_STRING_TYPE)); }
            catch (JsonProcessingException ignored) {}
        }
        e.setModelInsightsJson(rs.getString("model_insights"));
        e.setAuditDate(rs.getObject("audit_date", LocalDate.class));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) e.setCreatedAt(ts.toInstant());
        return e;
    }

    private static QueryEntity mapQueryRow(ResultSet rs, int rowNum) throws SQLException {
        QueryEntity q = new QueryEntity();
        q.setId(rs.getObject("id", UUID.class));
        String tid = rs.getString("tenant_id");
        if (tid != null) q.setWorkspaceId(UUID.fromString(tid));
        q.setJobId(rs.getObject("job_id", UUID.class));
        q.setQueryText(rs.getString("query_text"));
        q.setProcessed(rs.getBoolean("processed"));
        return q;
    }

    private static void setJsonb(PreparedStatement ps, int idx, String json) throws SQLException {
        if (json == null) {
            ps.setNull(idx, Types.OTHER);
        } else {
            ps.setObject(idx, json, Types.OTHER);
        }
    }

    private static void setNullableInt(PreparedStatement ps, int idx, Integer val) throws SQLException {
        if (val == null) {
            ps.setNull(idx, Types.INTEGER);
        } else {
            ps.setInt(idx, val);
        }
    }

    private static void setNullableDouble(PreparedStatement ps, int idx, Double val) throws SQLException {
        if (val == null) {
            ps.setNull(idx, Types.DOUBLE);
        } else {
            ps.setDouble(idx, val);
        }
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try { return objectMapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) { return null; }
    }
}
