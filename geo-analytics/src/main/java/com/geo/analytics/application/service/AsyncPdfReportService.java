package com.geo.analytics.application.service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.JobStompStatusPayload;
import com.geo.analytics.application.dto.PdfWhiteLabelInjection;
import com.geo.analytics.application.port.PdfReportPort;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.infrastructure.config.PdfStorageConfig;
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
    private static final int STACK_TRACE_LIMIT = 20_000;
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
    private final PdfBrowserTokenIssuer pdfBrowserTokenIssuer;
    private final PdfAuditService pdfAuditService;
    public AsyncPdfReportService(
            PdfReportPort pdfReportPort,
            BatchPersistenceService batchPersistence,
            PdfStorageConfig pdfStorageConfig,
            SimpMessagingTemplate simpMessagingTemplate,
            StrategyInsightService strategyInsightService,
            ObjectMapper objectMapper,
            AppProperties appProperties,
            PdfBrowserTokenIssuer pdfBrowserTokenIssuer,
            PdfAuditService pdfAuditService) {
        this.pdfReportPort = pdfReportPort;
        this.batchPersistence = batchPersistence;
        this.pdfStorageConfig = pdfStorageConfig;
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.strategyInsightService = strategyInsightService;
        this.objectMapper = objectMapper;
        this.pdfBrowserTokenIssuer = pdfBrowserTokenIssuer;
        this.pdfAuditService = pdfAuditService;
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
                TenantIdentity tenantIdentity = pdfBrowserTokenIssuer.buildTenantIdentityForWorkspace(workspaceId);
                pdfPlaywrightSemaphore.acquireUninterruptibly();
                byte[] pdfBytes;
                try {
                    pdfBytes = CompletableFuture.supplyAsync(
                        () -> ScopedValue
                            .where(TenantContextHolder.CONTEXT, tenantIdentity)
                            .call(() -> pdfReportPort.renderPrintRoutePdf(jobId, internalToken, injection)),
                        PDF_RENDER_EXECUTOR)
                        .get(PDF_RENDER_WALL_SECONDS, TimeUnit.SECONDS);
                } finally {
                    pdfPlaywrightSemaphore.release();
                }
                Path targetPath = pdfStorageConfig.getTempDirectory().resolve(jobId + ".pdf");
                Files.write(targetPath, pdfBytes);
                String absolutePath = targetPath.toAbsolutePath().normalize().toString();
                batchPersistence.markPdfCompleted(jobId, absolutePath);
                recordPdfAuditSafely(jobId, tenantIdentity, pdfBytes.length);
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
            log.error("PDF Playwright timeout jobId={} trace={}", jobId, truncateStackTrace(timeoutError));
        } else if (throwable instanceof PlaywrightException playwrightException) {
            log.error("Playwright PDF generation failed jobId={} trace={}", jobId, truncateStackTrace(playwrightException));
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
            log.error("PDF failure state update failed jobId={} trace={}", jobId, truncateStackTrace(publishException));
        }
        broadcastPdfStatus(jobId);
    }
    private void recordPdfAuditSafely(UUID jobId, TenantIdentity tenantIdentity, int pdfBytes) {
        try {
            ScopedValue
                .where(TenantContextHolder.CONTEXT, tenantIdentity)
                .run(() -> pdfAuditService.recordSuccessfulExport(jobId, pdfBytes));
        } catch (RuntimeException runtimeException) {
            log.error(
                "pdf_audit_record_failed jobId={} trace={}",
                jobId,
                truncateStackTrace(runtimeException));
        }
    }
    private void broadcastPdfStatus(UUID jobId) {
        try {
            batchPersistence.findJobByIdOptional(jobId).ifPresent(job -> {
                var rollup = strategyInsightService.rollupJob(batchPersistence.findResultsByJobId(jobId));
                var payload = JobStompStatusPayload.from(job, rollup);
                simpMessagingTemplate.convertAndSend("/topic/jobs/" + jobId, payload);
            });
        } catch (Exception e) {
            log.warn("PDF status broadcast failed jobId={} trace={}", jobId, truncateStackTrace(e));
        }
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
        if (throwable == null) {
            return "";
        }
        StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        String full = stringWriter.toString();
        if (full.length() <= STACK_TRACE_LIMIT) {
            return full;
        }
        return full.substring(0, STACK_TRACE_LIMIT);
    }
}
