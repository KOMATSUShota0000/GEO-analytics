package com.geo.analytics.web.dto;

import com.geo.analytics.domain.entity.ProjectEntity;
import java.util.List;
import java.util.UUID;

public record JobProjectResponse(UUID projectId, String projectName, String targetUrl, List<String> competitorUrls) {
    public static JobProjectResponse from(ProjectEntity projectEntity) {
        return new JobProjectResponse(
            projectEntity.getId(),
            projectEntity.getName(),
            projectEntity.getTargetUrl(),
            List.copyOf(projectEntity.getCompetitorUrls()));
    }
}
