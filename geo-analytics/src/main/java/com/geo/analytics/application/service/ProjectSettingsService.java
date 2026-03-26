package com.geo.analytics.application.service;

import com.geo.analytics.domain.entity.ProjectEntity;
import com.geo.analytics.infrastructure.repository.ProjectRepository;
import com.geo.analytics.infrastructure.tenant.TenantContext;
import com.geo.analytics.web.dto.ProjectSettingsPatchRequest;
import com.geo.analytics.web.dto.ProjectSettingsResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ProjectSettingsService {
    private static final Pattern EMAIL = Pattern.compile("^[\\w.!#$%&'*+/=?^`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$");
    private final ProjectRepository projectRepository;
    private final JdbcTemplate jdbcTemplate;

    public ProjectSettingsService(ProjectRepository projectRepository, JdbcTemplate jdbcTemplate) {
        this.projectRepository = projectRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ProjectSettingsResponse> getSettings(UUID projectId) {
        return readWorkspaceId(projectId)
            .flatMap(workspaceId -> TenantContext.executeWithTenant(
                workspaceId,
                () -> projectRepository.findById(projectId).map(this::toResponse)));
    }

    @Transactional
    public ProjectSettingsResponse patch(UUID projectId, ProjectSettingsPatchRequest projectSettingsPatchRequest) {
        UUID workspaceId = readWorkspaceId(projectId)
            .orElseThrow(() -> new EntityNotFoundException("Project not found: " + projectId));
        return TenantContext.executeWithTenant(workspaceId, () -> {
            ProjectEntity projectEntity = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project not found: " + projectId));
            if (projectSettingsPatchRequest.autoAuditEnabled() != null) {
                projectEntity.setAutoAuditEnabled(projectSettingsPatchRequest.autoAuditEnabled());
            }
            if (projectSettingsPatchRequest.slackWebhookUrl() != null) {
                String slack = projectSettingsPatchRequest.slackWebhookUrl().trim();
                if (!slack.isEmpty() && !slack.startsWith("https://hooks.slack.com/")) {
                    throw new IllegalArgumentException("slackWebhookUrl must start with https://hooks.slack.com/");
                }
                projectEntity.setSlackWebhookUrl(slack.isEmpty() ? null : slack);
            }
            if (projectSettingsPatchRequest.notificationEmail() != null) {
                String em = projectSettingsPatchRequest.notificationEmail().trim();
                if (!em.isEmpty() && !EMAIL.matcher(em).matches()) {
                    throw new IllegalArgumentException("notificationEmail is invalid");
                }
                projectEntity.setNotificationEmail(em.isEmpty() ? null : em);
            }
            return toResponse(projectRepository.save(projectEntity));
        });
    }

    private Optional<UUID> readWorkspaceId(UUID projectId) {
        List<String> rows = jdbcTemplate.query(
            "SELECT tenant_id FROM projects WHERE id = ?",
            ps -> ps.setObject(1, projectId),
            (rs, rowNum) -> rs.getString(1));
        if (rows.isEmpty() || rows.get(0) == null || rows.get(0).isBlank()) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(rows.get(0)));
    }

    private ProjectSettingsResponse toResponse(ProjectEntity projectEntity) {
        return new ProjectSettingsResponse(
            projectEntity.getId(),
            projectEntity.isAutoAuditEnabled(),
            projectEntity.getSlackWebhookUrl(),
            projectEntity.getNotificationEmail(),
            projectEntity.getLastAuditAt());
    }
}
