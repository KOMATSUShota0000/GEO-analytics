package com.geo.analytics.application.service;

import com.geo.analytics.domain.entity.ProjectEntity;
import com.geo.analytics.infrastructure.repository.ProjectRepository;
import com.geo.analytics.infrastructure.tenant.TenantPlanScope;
import com.geo.analytics.web.dto.ProjectSettingsPatchRequest;
import com.geo.analytics.web.dto.ProjectSettingsResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ProjectSettingsService {
    private static final Pattern EMAIL = Pattern.compile("^[\\w.!#$%&'*+/=?^`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$");
    static final int MAX_NOTIFICATION_EMAILS = 3;
    private static final int MAX_EMAIL_LENGTH = 320;
    private final ProjectRepository projectRepository;
    private final JdbcTemplate jdbcTemplate;

    public ProjectSettingsService(ProjectRepository projectRepository, JdbcTemplate jdbcTemplate) {
        this.projectRepository = projectRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    // Why: RLS の組織IDは @Transactional の呼び出しでしか接続へ渡らない（RlsConnectionInterceptor）。
    //      付けていなかったためプロジェクトが見えず、設定の読み込みが毎回 404 になっていた（#174）。
    @Transactional(readOnly = true)
    public Optional<ProjectSettingsResponse> getSettings(UUID projectId) {
        return readWorkspaceId(projectId)
            .flatMap(workspaceId -> TenantPlanScope.executeWithTenant(
                workspaceId,
                () -> projectRepository.findById(projectId).map(this::toResponse)));
    }

    @Transactional
    public ProjectSettingsResponse patch(UUID projectId, ProjectSettingsPatchRequest projectSettingsPatchRequest) {
        UUID workspaceId = readWorkspaceId(projectId)
            .orElseThrow(() -> new EntityNotFoundException("Project not found: " + projectId));
        return TenantPlanScope.executeWithTenant(workspaceId, () -> {
            ProjectEntity projectEntity = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project not found: " + projectId));
            if (projectSettingsPatchRequest.autoAuditEnabled() != null) {
                projectEntity.setAutoAuditEnabled(projectSettingsPatchRequest.autoAuditEnabled());
            }
            if (projectSettingsPatchRequest.notificationEmails() != null) {
                projectEntity.setNotificationEmails(normalizeNotificationEmails(projectSettingsPatchRequest.notificationEmails()));
            }
            return toResponse(projectRepository.save(projectEntity));
        });
    }

    // Why: 実際のメールサービスは大文字・小文字の違いを区別せず同じ受信箱へ届けるため、1件にまとめてから上限を数える。
    //      入力値はエラー文に含めない（例外ログにアドレスが残るため）。
    static List<String> normalizeNotificationEmails(List<String> requested) {
        Map<String, String> byLowerCase = new LinkedHashMap<>();
        for (String raw : requested) {
            String email = raw == null ? "" : raw.trim();
            if (email.isEmpty()) {
                continue;
            }
            if (email.length() > MAX_EMAIL_LENGTH || !EMAIL.matcher(email).matches()) {
                throw new IllegalArgumentException("メールアドレスの形式になっていないものがあります");
            }
            byLowerCase.putIfAbsent(email.toLowerCase(Locale.ROOT), email);
        }
        if (byLowerCase.size() > MAX_NOTIFICATION_EMAILS) {
            throw new IllegalArgumentException("通知先のメールアドレスは" + MAX_NOTIFICATION_EMAILS + "件まで登録できます");
        }
        return List.copyOf(byLowerCase.values());
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
            projectEntity.getNotificationEmails(),
            projectEntity.getLastAuditAt());
    }
}
