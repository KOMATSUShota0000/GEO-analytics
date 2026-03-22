package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.PdfWhiteLabelInjection;
import com.geo.analytics.application.port.PdfReportPort;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.infrastructure.config.PdfStorageConfig;
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

@Service
public class AsyncPdfReportService {
    private static final Logger log = LoggerFactory.getLogger(AsyncPdfReportService.class);
    private static final int MAX_STACK_CHARS = 4096;

    private final PdfReportPort pdfReportPort;
    private final JobPersistenceService jobPersistenceService;
    private final PdfStorageConfig pdfStorageConfig;
    private final String internalToken;
    private final String defaultBrandColor;
    private final String defaultLogoUrl;

    public AsyncPdfReportService(
            PdfReportPort pdfReportPort,
            JobPersistenceService jobPersistenceService,
            PdfStorageConfig pdfStorageConfig,
            @Value("${app.pdf.internal-token}") String internalToken,
            @Value("${app.pdf.default-brand-color}") String defaultBrandColor,
            @Value("${app.pdf.default-logo-url}") String defaultLogoUrl) {
        this.pdfReportPort = pdfReportPort;
        this.jobPersistenceService = jobPersistenceService;
        this.pdfStorageConfig = pdfStorageConfig;
        this.internalToken = internalToken;
        this.defaultBrandColor = defaultBrandColor;
        this.defaultLogoUrl = defaultLogoUrl == null ? "" : defaultLogoUrl;
    }

    @Async
    public void generatePdfReport(UUID jobId) {
        try {
            JobEntity jobEntity = jobPersistenceService.findJobById(jobId);
            PdfWhiteLabelInjection injection = new PdfWhiteLabelInjection(
                defaultBrandColor,
                defaultLogoUrl,
                jobEntity.getBrandName());
            byte[] pdfBytes = pdfReportPort.renderPrintRoutePdf(jobId, internalToken, injection);
            Path targetPath = pdfStorageConfig.getTempDirectory().resolve(jobId + ".pdf");
            Files.write(targetPath, pdfBytes);
            String absolutePath = targetPath.toAbsolutePath().normalize().toString();
            jobPersistenceService.markPdfCompletedAndPublish(jobId, absolutePath);
        } catch (Exception exception) {
            String truncated = truncateStackTrace(exception);
            log.error("PDF generation failed jobId={} trace={}", jobId, truncated);
            try {
                jobPersistenceService.markPdfFailedAndPublish(jobId);
            } catch (Exception publishException) {
                log.error("PDF failure state update failed jobId={}", jobId, publishException);
            }
        }
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
