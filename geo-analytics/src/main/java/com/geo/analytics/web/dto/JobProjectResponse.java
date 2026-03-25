package com.geo.analytics.web.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.geo.analytics.domain.entity.ProjectEntity;
import java.util.List;
import java.util.UUID;
public record JobProjectResponse(
    UUID projectId,
    String projectName,
    String targetUrl,
    List<String> competitorUrls,
    @JsonProperty("brand_color") String brandColor,
    @JsonProperty("logo_url") String logoUrl
) {
    public static JobProjectResponse from(ProjectEntity projectEntity) {
        String bc = projectEntity.getBrandColor();
        if (bc == null || bc.isBlank()) {
            bc = "#4F46E5";
        }
        return new JobProjectResponse(
            projectEntity.getId(),
            projectEntity.getName(),
            projectEntity.getTargetUrl(),
            List.copyOf(projectEntity.getCompetitorUrls()),
            bc,
            projectEntity.getLogoUrl());
    }
}
