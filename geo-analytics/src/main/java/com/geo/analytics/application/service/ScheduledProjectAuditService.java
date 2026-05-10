package com.geo.analytics.application.service;

import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.ProjectEntity;
import com.geo.analytics.domain.entity.ProjectKeywordEntity;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.infrastructure.repository.JobRepository;
import com.geo.analytics.infrastructure.repository.ProjectKeywordRepository;
import com.geo.analytics.infrastructure.repository.ProjectRepository;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import com.geo.analytics.infrastructure.tenant.TenantPlanScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
public class ScheduledProjectAuditService {
    private static final Logger log = LoggerFactory.getLogger(ScheduledProjectAuditService.class);
    private final JdbcTemplate jdbcTemplate;
    private final ProjectRepository projectRepository;
    private final ProjectKeywordRepository projectKeywordRepository;
    private final JobRepository jobRepository;
    private final JobPersistenceService jobPersistenceService;
    private final JobQuerySubmissionService jobQuerySubmissionService;

    public ScheduledProjectAuditService(
            JdbcTemplate jdbcTemplate,
            ProjectRepository projectRepository,
            ProjectKeywordRepository projectKeywordRepository,
            JobRepository jobRepository,
            JobPersistenceService jobPersistenceService,
            JobQuerySubmissionService jobQuerySubmissionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.projectRepository = projectRepository;
        this.projectKeywordRepository = projectKeywordRepository;
        this.jobRepository = jobRepository;
        this.jobPersistenceService = jobPersistenceService;
        this.jobQuerySubmissionService = jobQuerySubmissionService;
    }

    public void executeMonthlyAuditForProject(UUID projectId) {
        UUID workspaceId = readWorkspaceId(projectId);
        TenantPlanScope.executeWithTenant(workspaceId, () -> {
            ProjectEntity projectEntity = projectRepository.findById(projectId).orElse(null);
            if (projectEntity == null || !projectEntity.isAutoAuditEnabled()) {
                return;
            }
            List<ProjectKeywordEntity> keywords = projectKeywordRepository.findByProjectId(projectId);
            if (keywords.isEmpty()) {
                log.info("scheduled audit skipped no keywords projectId={}", projectId);
                return;
            }
            SubscriptionPlan subscriptionPlan = jobRepository.findFirstByProjectIdOrderByCreatedAtDesc(projectId)
                .map(JobEntity::getAppliedPlan)
                .orElse(SubscriptionPlan.STANDARD);
            if (subscriptionPlan == null) {
                subscriptionPlan = SubscriptionPlan.STANDARD;
            }
            String targetUrl = projectEntity.getTargetUrl();
            if (targetUrl == null || targetUrl.isBlank()) {
                targetUrl = "https://placeholder.invalid/scheduled-audit";
            }
            var jobFields =
                    new JobPersistenceService.JobCreateFields(
                            projectEntity.getName(), targetUrl, null, null, null);
            JobEntity jobEntity = jobPersistenceService.createJob(jobFields);
            List<String> texts = keywords.stream().map(ProjectKeywordEntity::getKeywordText).distinct().toList();
            jobQuerySubmissionService.submitQueries(jobEntity.getId(), texts, subscriptionPlan);
            log.info("scheduled audit enqueued jobId={} projectId={}", jobEntity.getId(), projectId);
        });
    }

    private UUID readWorkspaceId(UUID projectId) {
        List<String> rows = jdbcTemplate.query(
            "SELECT tenant_id FROM projects WHERE id = ?",
            ps -> ps.setObject(1, projectId),
            (rs, rowNum) -> rs.getString(1));
        if (rows.isEmpty() || rows.get(0) == null || rows.get(0).isBlank()) {
            return DefaultTenantIds.WORKSPACE_ID;
        }
        return UUID.fromString(rows.get(0));
    }
}
