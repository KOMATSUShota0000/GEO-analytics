package com.geo.analytics.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class MonthlyAuditJobLauncher {
    private static final Logger log = LoggerFactory.getLogger(MonthlyAuditJobLauncher.class);
    private final ScheduledProjectAuditService scheduledProjectAuditService;

    public MonthlyAuditJobLauncher(ScheduledProjectAuditService scheduledProjectAuditService) {
        this.scheduledProjectAuditService = scheduledProjectAuditService;
    }

    @Async
    public void fire(UUID projectId) {
        try {
            scheduledProjectAuditService.executeMonthlyAuditForProject(projectId);
        } catch (Exception exception) {
            log.error("monthly audit launch failed projectId={}", projectId, exception);
        }
    }
}
