package com.geo.analytics.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.ExtractedPlace;
import com.geo.analytics.application.dto.RubricAuditResult;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.ProjectEntity;
import com.geo.analytics.domain.entity.WorkspaceEntity;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.infrastructure.repository.ProjectRepository;
import com.geo.analytics.infrastructure.repository.WorkspaceRepository;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import com.geo.analytics.infrastructure.tenant.TenantPlanScope;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JobBenchmarkCaptureService {
    private static final Logger log = LoggerFactory.getLogger(JobBenchmarkCaptureService.class);
    private final BatchPersistenceService batchPersistence;
    private final WorkspaceRepository workspaceRepository;
    private final ProjectRepository projectRepository;
    private final SmartDomainCrawlService smartDomainCrawlService;
    private final RubricAuditService rubricAuditService;
    private final PlacesSearchService placesSearchService;
    private final JobPersistenceService jobPersistenceService;
    private final EmotionalAlertOrchestrationService emotionalAlertOrchestrationService;
    private final ObjectMapper objectMapper;

    public JobBenchmarkCaptureService(
            BatchPersistenceService batchPersistence,
            WorkspaceRepository workspaceRepository,
            ProjectRepository projectRepository,
            SmartDomainCrawlService smartDomainCrawlService,
            RubricAuditService rubricAuditService,
            PlacesSearchService placesSearchService,
            JobPersistenceService jobPersistenceService,
            EmotionalAlertOrchestrationService emotionalAlertOrchestrationService,
            ObjectMapper objectMapper) {
        this.batchPersistence = batchPersistence;
        this.workspaceRepository = workspaceRepository;
        this.projectRepository = projectRepository;
        this.smartDomainCrawlService = smartDomainCrawlService;
        this.rubricAuditService = rubricAuditService;
        this.placesSearchService = placesSearchService;
        this.jobPersistenceService = jobPersistenceService;
        this.emotionalAlertOrchestrationService = emotionalAlertOrchestrationService;
        this.objectMapper = objectMapper;
    }

    public void capture(UUID jobId) {
        try {
            JobEntity job = batchPersistence.findJobById(jobId);
            UUID projectId = job.getProjectId();
            if (projectId == null) {
                return;
            }
            UUID wsId = Objects.requireNonNullElse(job.getWorkspaceId(), DefaultTenantIds.WORKSPACE_ID);
            SubscriptionPlan plan = Objects.requireNonNullElse(job.getAppliedPlan(), SubscriptionPlan.STANDARD);
            UUID orgId = workspaceRepository
                    .findById(wsId)
                    .map(WorkspaceEntity::getOrganizationId)
                    .orElse(DefaultTenantIds.DEFAULT_ORGANIZATION_ID);
            TenantPlanScope.executeWithTenantOrganizationAndPlan(wsId, orgId, plan, () -> {
                ProjectEntity project = projectRepository.findById(projectId).orElse(null);
                if (project == null) {
                    return;
                }
                String targetUrl = project.getTargetUrl();
                if (targetUrl == null || targetUrl.isBlank()) {
                    return;
                }
                String trimmedTarget = targetUrl.trim();
                var selfBundle = smartDomainCrawlService.compileForAudit(trimmedTarget);
                RubricAuditResult selfRubric =
                        rubricAuditService.executeAudit(projectId, selfBundle.mergedAuditText());
                ArrayList<RubricAuditResult> competitorRubrics = new ArrayList<>();
                List<String> urls = project.getCompetitorUrls();
                for (int i = 0; i < urls.size(); i++) {
                    String u = urls.get(i);
                    if (u == null || u.isBlank()) {
                        continue;
                    }
                    try {
                        var competitorBundle = smartDomainCrawlService.compileForAudit(u.trim());
                        competitorRubrics.add(
                                rubricAuditService.executeAudit(projectId, competitorBundle.mergedAuditText()));
                    } catch (Throwable suppressed) {
                        log.warn("competitor benchmark crawl skipped jobId={} url={}", jobId, u, suppressed);
                    }
                }
                Integer meoCount = null;
                Double meoStars = null;
                try {
                    String brand = job.getBrandName();
                    String query =
                            brand != null && !brand.isBlank()
                                    ? brand.trim()
                                    : project.getName() != null ? project.getName().trim() : "";
                    if (!query.isEmpty()) {
                        List<ExtractedPlace> places = placesSearchService.search(projectId, query);
                        for (int i = 0; i < places.size(); i++) {
                            ExtractedPlace place = places.get(i);
                            if (place != null && hostsMatch(place.websiteUrl(), trimmedTarget)) {
                                meoCount = place.userRatingsTotal();
                                meoStars = place.rating();
                                break;
                            }
                        }
                    }
                } catch (Throwable suppressed) {
                    log.warn("meo benchmark lookup skipped jobId={}", jobId, suppressed);
                }
                try {
                    jobPersistenceService.persistJobBenchmarkSnapshot(
                            jobId,
                            objectMapper.writeValueAsString(selfRubric),
                            objectMapper.writeValueAsString(List.copyOf(competitorRubrics)),
                            objectMapper.writeValueAsString(selfBundle.primaryPage().crawled()),
                            meoCount,
                            meoStars);
                    emotionalAlertOrchestrationService.materializeAndPersist(jobId);
                } catch (JsonProcessingException ex) {
                    throw new IllegalStateException(ex);
                }
            });
        } catch (Throwable throwable) {
            log.warn("benchmark capture failed jobId={}", jobId, throwable);
        }
    }

    private static boolean hostsMatch(String websiteUri, String targetUrl) {
        if (websiteUri == null || websiteUri.isBlank() || targetUrl == null || targetUrl.isBlank()) {
            return false;
        }
        try {
            URI w = URI.create(websiteUri.trim());
            URI t = URI.create(targetUrl.trim());
            String wh = w.getHost();
            String th = t.getHost();
            if (wh == null || th == null) {
                return false;
            }
            return normalizeHost(wh).equals(normalizeHost(th));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static String normalizeHost(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        if (h.startsWith("www.")) {
            return h.substring(4);
        }
        return h;
    }
}
