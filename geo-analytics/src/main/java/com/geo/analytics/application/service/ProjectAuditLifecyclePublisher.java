package com.geo.analytics.application.service;

import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.event.ProjectAuditCompletedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class ProjectAuditLifecyclePublisher {
    private final ApplicationEventPublisher applicationEventPublisher;

    public ProjectAuditLifecyclePublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void publishAuditCompleted(JobEntity jobEntity) {
        UUID projectId = jobEntity.getProjectId();
        UUID workspaceId = jobEntity.getWorkspaceId();
        if (projectId == null || workspaceId == null) {
            return;
        }
        applicationEventPublisher.publishEvent(new ProjectAuditCompletedEvent(projectId, jobEntity.getId(), workspaceId));
    }
}
