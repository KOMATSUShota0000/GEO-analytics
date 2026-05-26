package com.geo.analytics.application.dto;

import com.geo.analytics.domain.entity.AuditHistoryEntity;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.ProjectEntity;
import java.util.List;

public record JobAnalysisAggregate(JobEntity job, ProjectEntity project, List<AuditHistoryEntity> auditHistories) {}
