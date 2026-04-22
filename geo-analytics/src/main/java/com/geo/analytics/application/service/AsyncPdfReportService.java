package com.geo.analytics.application.service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.JobStompStatusPayload;
import com.geo.analytics.application.dto.PdfWhiteLabelInjection;
import com.geo.analytics.application.port.PdfBrowserAuthHeaders;
import com.geo.analytics.application.port.PdfReportPort;
import com.geo.analytics.domain.PdfJobStatusValues;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.OrganizationUser;
import com.geo.analytics.infrastructure.config.PdfStorageConfig;
import com.geo.analytics.infrastructure.security.TokenService;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import com.geo.analytics.infrastructure.tenant.TenantIdentity;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.geo.analytics.infrastructure.config.AppProperties;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ScopedValue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
@Service
public class AsyncPdfReportService {
    private static final Logger log = LoggerFactory.getLogger(AsyncPdfReportService.class);
    private static final int MAX_STACK_CHARS = 4096;
    private static final int PDF_RENDER_WALL_SECONDS = 180;
    private static final Executor PDF_RENDER_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    private final PdfReportPort pdfReportPort;
    private final BatchPersistenceService batchPersistence;
    private final PdfStorageConfig pdfStorageConfig;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final String internalToken;
    private final String defaultBrandColor;
    private final String defaultLogoUrl;
    private final StrategyInsightService strategyInsightService;
    private final ObjectMapper objectMapper;
    private final Semaphore pdfPlaywrightSemaphore;
    private final SessionManagementService sessionManagementService;
    private final TokenService tokenService;
    public AsyncPdfReportService(
            PdfReportPort pdfReportPort,
            BatchPersistenceService batchPersistence,
            PdfStorageConfig pdfStorageConfig,
            SimpMessagingTemplate simpMessagingTemplate,
            StrategyInsightService strategyInsightService,
            ObjectMapper objectMapper,
            AppProperties appProperties,
            SessionManagementService sessionManagementService,
            TokenService tokenService) {
        this.pdfReportPort = pdfReportPort;
        this.batchPersistence = batchPersistence;
        this.pdfStorageConfig = pdfStorageConfig;
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.strategyInsightService = strategyInsightService;
        this.objectMapper = objectMapper;
        this.sessionManagementService = sessionManagementService;
        this.tokenService = tokenService;
        AppProperties.Pdf pdf = appProperties.getPdf();
        this.internalToken = pdf.getInternalToken();
        this.defaultBrandColor = pdf.getDefaultBrandColor();
        this.defaultLogoUrl = pdf.getDefaultLogoUrl() == null ? "" : pdf.getDefaultLogoUrl();
        Integer mc = pdf.getMaxConcurrent();
        int pdfMaxConcurrent = mc != null ? mc : 2;
        var permits = Math.max(1, pdfMaxConcurrent);
        this.pdfPlaywrightSemaphore = new Semaphore(permits, true);
    }
    @Async
    public void generatePdfReport(UUID jobId) {
        try {
            try {
                JobEntity jobEntity = batchPersistence.findJobById(jobId);
                if (batchPersistence.findResultsByJobId(jobId).isEmpty()) {
                    log.error("PDF generation skipped: zero analysis results jobId={}", jobId);
                    markPdfFailedAndPublishSafely(jobId);
                    return;
                }
                UUID workspaceId = jobEntity.getWorkspaceId() != null
                    ? jobEntity.getWorkspaceId()
                    : DefaultTenantIds.WORKSPACE_ID;
                var projectInfo = jobEntity.getProjectId() != null
                    ? batchPersistence.findProjectBrandInfo(jobEntity.getProjectId()).orElse(null)
                    : null;
                String brandColor = pickFirstNonBlank(
                    jobEntity.getBrandColor(),
                    projectInfo != null ? projectInfo.brandColor() : null,
                    defaultBrandColor);
                String logoUrl = pickFirstNonBlank(
                    jobEntity.getLogoUrl(),
                    projectInfo != null ? projectInfo.logoUrl() : null,
                    defaultLogoUrl);
                String pdfContextJson = "{}";
                try {
                    var rows = batchPersistence.findResultsByJobId(jobId);
                    var rollup = strategyInsightService.rollupJob(rows);
                    var medZ = strategyInsightService.medianModifiedZ(rows);
                    var medSt = strategyInsightService.medianVisibilityStage(rows);
                    var jd = jobEntity.getJobDiagnosticMessage();
                    var jra = jobEntity.getJobRecommendedActions();
                    String summaryDiag = jd != null && !jd.isBlank()
                        ? jd
                        : (rollup.diagnosticMessage() != null ? rollup.diagnosticMessage() : "");
                    var summaryActs = jra != null && !jra.isEmpty()
                        ? jra
                        : rollup.recommendedActions();
                    var payload = new LinkedHashMap<String, Object>();
                    payload.put("jobSummaryDiagnostic", summaryDiag);
                    payload.put("jobSummaryRecommendedActions", summaryActs);
                    payload.put("jobMedianModifiedZ", medZ);
                    payload.put("jobMedianVisibilityStage", medSt);
                    pdfContextJson = objectMapper.writeValueAsString(payload);
                } catch (JsonProcessingException jsonProcessingException) {
                    log.warn("pdf_context_json_failed jobId={}", jobId, jsonProcessingException);
                }
                PdfWhiteLabelInjection injection = new PdfWhiteLabelInjection(
                    brandColor,
                    logoUrl,
                    jobEntity.getBrandName(),
                    pdfContextJson);
                PdfBrowserAuthHeaders browserAuth = buildPdfBrowserAuthHeaders(workspaceId);
                pdfPlaywrightSemaphore.acquireUninterruptibly();
                byte[] pdfBytes;
                try {
                    pdfBytes = CompletableFuture.supplyAsync(
                        () -> pdfReportPort.renderPrintRoutePdf(jobId, internalToken, injection, browserAuth),
                        PDF_RENDER_EXECUTOR)
                        .get(PDF_RENDER_WALL_SECONDS, TimeUnit.SECONDS);
                } finally {
                    pdfPlaywrightSemaphore.release();
                }
                Path targetPath = pdfStorageConfig.getTempDirectory().resolve(jobId + ".pdf");
                Files.write(targetPath, pdfBytes);
                String absolutePath = targetPath.toAbsolutePath().normalize().toString();
                batchPersistence.markPdfCompleted(jobId, absolutePath);
                broadcastPdfStatus(jobId);
            } catch (TimeoutException timeoutException) {
                log.error("PDF generation wall-clock timeout jobId={} seconds={}", jobId, PDF_RENDER_WALL_SECONDS, timeoutException);
                markPdfFailedAndPublishSafely(jobId);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                log.error("PDF generation interrupted jobId={}", jobId, interruptedException);
                markPdfFailedAndPublishSafely(jobId);
            } catch (ExecutionException executionException) {
                Throwable cause = executionException.getCause() != null ? executionException.getCause() : executionException;
                logPdfRenderFailureAndMark(jobId, cause);
            } catch (Exception exception) {
                String truncated = truncateStackTrace(exception);
                log.error("PDF generation failed jobId={} trace={}", jobId, truncated);
                markPdfFailedAndPublishSafely(jobId);
            }
        } catch (Throwable throwable) {
            log.error("PDF generation unexpected failure jobId={}", jobId, throwable);
            markPdfFailedAndPublishSafely(jobId);
        }
    }
    private void logPdfRenderFailureAndMark(UUID jobId, Throwable throwable) {
        if (throwable instanceof TimeoutError timeoutError) {
            log.error("PDF Playwright timeout jobId={}", jobId, timeoutError);
        } else if (throwable instanceof PlaywrightException playwrightException) {
            log.error("Playwright PDF generation failed jobId={}", jobId, playwrightException);
        } else {
            String truncated = truncateStackTrace(
                throwable instanceof Exception exception ? exception : new RuntimeException(throwable));
            log.error("PDF render failed jobId={} trace={}", jobId, truncated);
        }
        markPdfFailedAndPublishSafely(jobId);
    }
    private void markPdfFailedAndPublishSafely(UUID jobId) {
        try {
            batchPersistence.markPdfFailed(jobId);
        } catch (Exception publishException) {
            log.error("PDF failure state update failed jobId={}", jobId, publishException);
        }
        broadcastPdfStatus(jobId);
    }

    private void broadcastPdfStatus(UUID jobId) {
        try {
            batchPersistence.findJobByIdOptional(jobId).ifPresent(job -> {
                var rollup = strategyInsightService.rollupJob(batchPersistence.findResultsByJobId(jobId));
                var payload = JobStompStatusPayload.from(job, rollup);
                simpMessagingTemplate.convertAndSend("/topic/jobs/" + jobId, payload);
            });
        } catch (Exception e) {
            log.warn("PDF status broadcast failed jobId={}", jobId, e);
        }
    }
    private PdfBrowserAuthHeaders buildPdfBrowserAuthHeaders(UUID workspaceId) {
        try {
            UUID orgId = resolveOrganizationIdForWorkspace(workspaceId);
            return ScopedValue.where(TenantContextHolder.CONTEXT, new TenantIdentity(orgId, workspaceId, null))
                    .call(
                            () -> {
                                var userInfo =
                                        batchPersistence
                                                .findFirstActiveOrgUser(orgId)
                                                .orElseThrow(
                                                        () -> new IllegalStateException(
                                                                "PDF生成用の有効なユーザーが組織内に見つかりません: orgId="
                                                                        + orgId));
                                OrganizationUser user = new OrganizationUser();
                                user.setId(userInfo.id());
                                user.setEmail(userInfo.email());
                                user.setPasswordHash(userInfo.passwordHash());
                                user.setOrganizationId(orgId);
                                UUID sessionId =
                                        sessionManagementService.appendRenderingSession(user.getId());
                                String jwt = tokenService.generateAccessToken(user, sessionId);
                                return new PdfBrowserAuthHeaders("Bearer " + jwt, workspaceId.toString());
                            });
        } catch (IllegalStateException illegalStateException) {
            throw illegalStateException;
        } catch (Exception exception) {
            log.warn("pdf_browser_auth failed workspaceId={}", workspaceId, exception);
            return PdfBrowserAuthHeaders.NONE;
        }
    }

    private UUID resolveOrganizationIdForWorkspace(UUID workspaceId) {
        if (DefaultTenantIds.WORKSPACE_ID.equals(workspaceId)) {
            return DefaultTenantIds.DEFAULT_ORGANIZATION_ID;
        }
        return batchPersistence.findWorkspaceOrganizationId(workspaceId)
                .orElseThrow(() -> new IllegalStateException("workspace not found: " + workspaceId));
    }

    private static String pickFirstNonBlank(String a, String b, String fallback) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return fallback != null ? fallback : "";
    }
    private static String truncateStackTrace(Throwable throwable) {
        StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        String full = stringWriter.toString();
        if (full.length() <= MAX_STACK_CHARS) {
            return full;
        }
        return full.substring(0, MAX_STACK_CHARS);
    }
}
