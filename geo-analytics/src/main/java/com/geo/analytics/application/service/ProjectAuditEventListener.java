package com.geo.analytics.application.service;

import com.geo.analytics.domain.event.ProjectAuditCompletedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class ProjectAuditEventListener {
    private final NotificationService notificationService;
    private final ProjectLastAuditService projectLastAuditService;

    public ProjectAuditEventListener(NotificationService notificationService, ProjectLastAuditService projectLastAuditService) {
        this.notificationService = notificationService;
        this.projectLastAuditService = projectLastAuditService;
    }

    @Async
    @EventListener
    public void onProjectAuditCompleted(ProjectAuditCompletedEvent projectAuditCompletedEvent) {
        try {
            notificationService.deliver(projectAuditCompletedEvent);
        } finally {
            projectLastAuditService.markLastAudit(projectAuditCompletedEvent.projectId(), projectAuditCompletedEvent.workspaceId());
        }
    }
}
