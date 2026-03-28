package com.geo.analytics.application.service;
import com.geo.analytics.application.dto.JobStompStatusPayload;
import com.geo.analytics.application.dto.PdfWhiteLabelInjection;
import com.geo.analytics.application.port.PdfReportPort;
import com.geo.analytics.domain.PdfJobStatusValues;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.ProjectEntity;
import com.geo.analytics.infrastructure.config.PdfStorageConfig;
import com.geo.analytics.infrastructure.repository.ProjectRepository;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import com.geo.analytics.infrastructure.tenant.TenantContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
@Service
public class AsyncPdfReportService {
    private static final Logger log = LoggerFactory.getLogger(AsyncPdfReportService.class);
    private static final int MAX_STACK_CHARS = 4096;
    private static final int PDF_RENDER_WALL_SECONDS = 180;
    private static final Executor PDF_RENDER_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    private final PdfReportPort pdfReportPort;
    private final JobPersistenceService jobPersistenceService;
    private final ProjectRepository projectRepository;
    private final PdfStorageConfig pdfStorageConfig;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final String internalToken;
    private final String defaultBrandColor;
    private final String defaultLogoUrl;
    public AsyncPdfReportService(
            PdfReportPort pdfReportPort,
            JobPersistenceService jobPersistenceService,
            ProjectRepository projectRepository,
            PdfStorageConfig pdfStorageConfig,
            SimpMessagingTemplate simpMessagingTemplate,
            @Value("${app.pdf.internal-token}") String internalToken,
            @Value("${app.pdf.default-brand-color}") String defaultBrandColor,
            @Value("${app.pdf.default-logo-url}") String defaultLogoUrl) {
        this.pdfReportPort = pdfReportPort;
        this.jobPersistenceService = jobPersistenceService;
        this.projectRepository = projectRepository;
        this.pdfStorageConfig = pdfStorageConfig;
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.internalToken = internalToken;
        this.defaultBrandColor = defaultBrandColor;
        this.defaultLogoUrl = defaultLogoUrl == null ? "" : defaultLogoUrl;
    }
    @Async
    public void generatePdfReport(UUID jobId) {
        try {
            try {
                JobEntity jobEntity = jobPersistenceService.findJobById(jobId);
                if (jobPersistenceService.findResultsByJobId(jobId).isEmpty()) {
                    log.error("PDF generation skipped: zero analysis results jobId={}", jobId);
                    markPdfFailedAndPublishSafely(jobId);
                    return;
                }
                UUID workspaceId = jobEntity.getWorkspaceId() != null
                    ? jobEntity.getWorkspaceId()
                    : DefaultTenantIds.WORKSPACE_ID;
                ProjectEntity projectEntity = TenantContext.executeWithTenant(
                    workspaceId,
                    () -> projectRepository.findById(jobEntity.getProjectId()).orElse(null));
                String brandColor = pickFirstNonBlank(
                    jobEntity.getBrandColor(),
                    projectEntity != null ? projectEntity.getBrandColor() : null,
                    defaultBrandColor);
                String logoUrl = pickFirstNonBlank(
                    jobEntity.getLogoUrl(),
                    projectEntity != null ? projectEntity.getLogoUrl() : null,
                    defaultLogoUrl);
                PdfWhiteLabelInjection injection = new PdfWhiteLabelInjection(brandColor, logoUrl, jobEntity.getBrandName());
                byte[] pdfBytes = CompletableFuture.supplyAsync(
                    () -> pdfReportPort.renderPrintRoutePdf(jobId, internalToken, injection),
                    PDF_RENDER_EXECUTOR)
                    .get(PDF_RENDER_WALL_SECONDS, TimeUnit.SECONDS);
                Path targetPath = pdfStorageConfig.getTempDirectory().resolve(jobId + ".pdf");
                Files.write(targetPath, pdfBytes);
                String absolutePath = targetPath.toAbsolutePath().normalize().toString();
                jobPersistenceService.markPdfCompletedAndPublish(jobId, absolutePath);
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
            jobPersistenceService.markPdfFailedAndPublish(jobId);
            return;
        } catch (Exception publishException) {
            log.error("PDF failure state update failed jobId={}", jobId, publishException);
        }
        jobPersistenceService.markPdfFailedBestEffort(jobId);
        jobPersistenceService.findJobByIdOptional(jobId).ifPresent(job -> {
            if (PdfJobStatusValues.GENERATING.equals(job.getPdfStatus())) {
                var payload = new JobStompStatusPayload(
                    job.getId(),
                    job.getJobStatus() != null ? job.getJobStatus().name() : "UNKNOWN",
                    job.getBrandName(),
                    "PDF generation failed",
                    PdfJobStatusValues.FAILED,
                    null,
                    job.getCreatedAt(),
                    job.getUpdatedAt());
                simpMessagingTemplate.convertAndSend("/topic/jobs/" + jobId, payload);
            }
        });
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
