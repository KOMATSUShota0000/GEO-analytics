package com.geo.analytics.application.service;

import com.geo.analytics.application.command.UpdateProjectContextCommand;
import com.geo.analytics.domain.entity.ProjectEntity;
import com.geo.analytics.domain.model.MinorityReport;
import com.geo.analytics.infrastructure.repository.ProjectRepository;
import com.geo.analytics.infrastructure.tenant.TenantPlanScope;
import com.geo.analytics.web.dto.MinorityReportDto;
import com.geo.analytics.web.dto.ProjectContextResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProjectContextService {
    private final ProjectRepository projectRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ProjectContextTextLimiter projectContextTextLimiter;

    public ProjectContextService(
            ProjectRepository projectRepository,
            JdbcTemplate jdbcTemplate,
            ProjectContextTextLimiter projectContextTextLimiter) {
        this.projectRepository = projectRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.projectContextTextLimiter = projectContextTextLimiter;
    }

    public Optional<ProjectContextResponse> getContext(UUID projectId) {
        return readWorkspaceId(projectId)
                .flatMap(
                        workspaceId ->
                                TenantPlanScope.executeWithTenant(
                                        workspaceId, () -> projectRepository.findById(projectId).map(this::toContextResponse)));
    }

    @Transactional
    public ProjectContextResponse patchContext(UUID projectId, UpdateProjectContextCommand updateProjectContextCommand) {
        UUID workspaceId = readWorkspaceId(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project not found: " + projectId));
        return TenantPlanScope.executeWithTenant(workspaceId, () -> {
            ProjectEntity projectEntity =
                    projectRepository.findById(projectId).orElseThrow(() -> new EntityNotFoundException("Project not found: " + projectId));
            projectEntity.setIndustryType(updateProjectContextCommand.industryType());
            projectEntity.setExtractedStrengths(
                    projectContextTextLimiter.limit(updateProjectContextCommand.extractedStrengths()));
            projectEntity.setTargetAudience(
                    projectContextTextLimiter.limit(updateProjectContextCommand.targetAudience()));
            List<MinorityReport> limitedMinority =
                    updateProjectContextCommand.minorityReports().stream()
                            .map(
                                    m ->
                                            new MinorityReport(
                                                    projectContextTextLimiter.limit(m.insight()),
                                                    projectContextTextLimiter.limit(m.conflictReason()),
                                                    projectContextTextLimiter.limit(m.evidence())))
                            .toList();
            projectEntity.setMinorityReports(new ArrayList<>(limitedMinority));
            return toContextResponse(projectRepository.save(projectEntity));
        });
    }

    private ProjectContextResponse toContextResponse(ProjectEntity projectEntity) {
        String es = projectEntity.getExtractedStrengths();
        List<String> strengths;
        if (es == null || es.isBlank()) {
            strengths = List.of();
        } else {
            strengths = es.lines().toList();
        }
        String ta = projectEntity.getTargetAudience();
        List<MinorityReportDto> minorityDtos =
                projectEntity.getMinorityReports() == null || projectEntity.getMinorityReports().isEmpty()
                        ? List.of()
                        : projectEntity.getMinorityReports().stream()
                                .map(
                                        m ->
                                                new MinorityReportDto(
                                                        m.insight() == null ? "" : m.insight(),
                                                        m.conflictReason() == null ? "" : m.conflictReason(),
                                                        m.evidence() == null ? "" : m.evidence()))
                                .toList();
        return new ProjectContextResponse(
                projectEntity.getIndustryType(), strengths, ta == null ? "" : ta, minorityDtos);
    }

    private Optional<UUID> readWorkspaceId(UUID projectId) {
        List<String> rows =
                jdbcTemplate.query(
                        "SELECT tenant_id FROM projects WHERE id = ?",
                        ps -> ps.setObject(1, projectId),
                        (rs, rowNum) -> rs.getString(1));
        if (rows.isEmpty() || rows.get(0) == null || rows.get(0).isBlank()) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(rows.get(0)));
    }
}
