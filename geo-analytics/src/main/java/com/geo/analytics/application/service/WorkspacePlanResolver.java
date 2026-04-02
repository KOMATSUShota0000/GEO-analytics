package com.geo.analytics.application.service;

import com.geo.analytics.domain.entity.WorkspaceEntity;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.infrastructure.repository.WorkspaceRepository;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class WorkspacePlanResolver {
    private final WorkspaceRepository workspaceRepository;

    public WorkspacePlanResolver(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository);
    }

    public SubscriptionPlan resolvePlan(UUID workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .map(WorkspaceEntity::getSubscriptionPlan)
                .filter(Objects::nonNull)
                .orElse(SubscriptionPlan.STANDARD);
    }
}
