package com.geo.analytics.application.service;

import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.infrastructure.persistence.GlobalAccess;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspacePlanResolver {
    public record WorkspaceInfo(UUID organizationId, SubscriptionPlan plan) {}

    private static final String SQL = """
            SELECT organization_id, subscription_plan
            FROM workspaces
            WHERE id = ? AND deleted_at IS NULL
            """;

    private final JdbcTemplate batchJdbcTemplate;

    public WorkspacePlanResolver(@Qualifier("batchJdbcTemplate") JdbcTemplate batchJdbcTemplate) {
        this.batchJdbcTemplate = Objects.requireNonNull(batchJdbcTemplate);
    }

    @Transactional(readOnly = true)
    @GlobalAccess
    public SubscriptionPlan resolvePlan(UUID workspaceId) {
        return resolveWorkspaceInfo(workspaceId).plan();
    }

    @Transactional(readOnly = true)
    @GlobalAccess
    public WorkspaceInfo resolveWorkspaceInfo(UUID workspaceId) {
        try {
            return batchJdbcTemplate.queryForObject(SQL, (rs, rowNum) -> {
                UUID orgId = rs.getObject("organization_id", UUID.class);
                String planStr = rs.getString("subscription_plan");
                SubscriptionPlan plan = SubscriptionPlan.STANDARD;
                if (planStr != null) {
                    try {
                        plan = SubscriptionPlan.valueOf(planStr);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                return new WorkspaceInfo(orgId, plan);
            }, workspaceId);
        } catch (EmptyResultDataAccessException e) {
            return new WorkspaceInfo(null, SubscriptionPlan.STANDARD);
        }
    }
}
