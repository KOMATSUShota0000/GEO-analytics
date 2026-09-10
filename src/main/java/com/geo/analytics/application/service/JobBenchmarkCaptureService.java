package com.geo.analytics.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.ExtractedPlace;
import com.geo.analytics.application.dto.RubricAuditResult;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.ProjectEntity;
import com.geo.analytics.domain.entity.WorkspaceEntity;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.infrastructure.ai.JobPromptContextFormatter;
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
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * Why: RLS の GUC（app.current_org_id / app.current_tenant_id）は {@code RlsConnectionInterceptor} が
     * トランザクション内でのみ設定する。本メソッドに @Transactional が無かったため GUC が未設定のまま
     * projects を読み、RLS に弾かれて project_not_found で静かに早期 return していた。その結果
     * jobs.self_rubric_audit_json が永久に NULL となり、基礎スコアが算出されず改善タスクが常時ロックされていた。
     * クロールと LLM 監査を含むため readOnly ではなく、外側で長時間トランザクションを張らないよう
     * 読み取り境界のみをここで確保する。
     */
    @Transactional
    public void capture(UUID jobId) {
        log.info("benchmark_capture_started jobId={}", jobId);
        try {
            JobEntity job = batchPersistence.findJobById(jobId);
            UUID projectId = job.getProjectId();
            if (projectId == null) {
                log.warn("benchmark_capture_skipped reason=project_id_null jobId={}", jobId);
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
                    log.warn("benchmark_capture_skipped reason=project_not_found jobId={} projectId={}", jobId, projectId);
                    return;
                }
                String targetUrl = project.getTargetUrl();
                if (targetUrl == null || targetUrl.isBlank()) {
                    log.warn("benchmark_capture_skipped reason=target_url_blank jobId={} projectId={}", jobId, projectId);
                    return;
                }
                String trimmedTarget = targetUrl.trim();
                var selfBundle = smartDomainCrawlService.compileForAudit(trimmedTarget);
                RubricAuditResult selfRubric =
                        rubricAuditService.executeAudit(
                                projectId, selfBundle.mergedAuditText(), JobPromptContextFormatter.format(job));
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
                            objectMapper.writeValueAsString(selfBundle.primaryPage().crawled()),
                            meoCount,
                            meoStars);
                    log.info("benchmark_capture_persisted jobId={} meoCount={} meoStars={}", jobId, meoCount, meoStars);
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
