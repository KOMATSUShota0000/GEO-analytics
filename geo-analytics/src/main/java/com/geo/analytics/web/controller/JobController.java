package com.geo.analytics.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.service.AsyncPdfReportService;
import com.geo.analytics.application.service.JobPersistenceService;
import com.geo.analytics.application.service.JobQuerySubmissionService;
import com.geo.analytics.application.service.JobStreamRegistryService;
import com.geo.analytics.application.service.JobSyncTestService;
import com.geo.analytics.domain.PdfJobStatusValues;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.enums.JobStatus;
import com.geo.analytics.infrastructure.config.PdfStorageConfig;
import com.geo.analytics.application.dto.JobAnalysisAggregate;
import com.geo.analytics.application.dto.PdfGenerationStartResult;
import com.geo.analytics.web.dto.AddQueriesRequest;
import com.geo.analytics.web.dto.CreateJobRequest;
import com.geo.analytics.web.dto.JobAnalysisDetailResponse;
import com.geo.analytics.web.dto.JobStatusResponse;
import com.geo.analytics.web.dto.ResultDetailResponse;
import com.geo.analytics.web.dto.ResultSummaryResponse;
import com.geo.analytics.web.dto.StreamErrorPayload;
import com.geo.analytics.web.dto.VerifyStreamEvent;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {
    private static final Logger log = LoggerFactory.getLogger(JobController.class);
    private static final ZoneId ZONE_TOKYO = ZoneId.of("Asia/Tokyo");
    private static final DateTimeFormatter PDF_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final JobPersistenceService jobPersistenceService;
    private final JobQuerySubmissionService jobQuerySubmissionService;
    private final JobSyncTestService jobSyncTestService;
    private final AsyncPdfReportService asyncPdfReportService;
    private final PdfStorageConfig pdfStorageConfig;
    private final JobStreamRegistryService jobStreamRegistryService;
    private final ObjectMapper objectMapper;

    public JobController(
            JobPersistenceService jobPersistenceService,
            JobQuerySubmissionService jobQuerySubmissionService,
            JobSyncTestService jobSyncTestService,
            AsyncPdfReportService asyncPdfReportService,
            PdfStorageConfig pdfStorageConfig,
            JobStreamRegistryService jobStreamRegistryService,
            ObjectMapper objectMapper) {
        this.jobPersistenceService = jobPersistenceService;
        this.jobQuerySubmissionService = jobQuerySubmissionService;
        this.jobSyncTestService = jobSyncTestService;
        this.asyncPdfReportService = asyncPdfReportService;
        this.pdfStorageConfig = pdfStorageConfig;
        this.jobStreamRegistryService = jobStreamRegistryService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<JobStatusResponse> createJob(@RequestBody @Valid CreateJobRequest createJobRequest) {
        log.info("createJob request brandName={}", createJobRequest.brandName());
        JobEntity createdJobEntity = jobPersistenceService.createJob(createJobRequest.brandName());
        URI createdResourceLocation = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{jobId}")
            .buildAndExpand(createdJobEntity.getId())
            .toUri();
        return ResponseEntity.created(createdResourceLocation)
            .body(JobStatusResponse.from(createdJobEntity));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<JobStatusResponse> getJobStatus(@PathVariable UUID jobId) {
        JobEntity jobEntity = jobPersistenceService.findJobById(jobId);
        return ResponseEntity.ok(JobStatusResponse.from(jobEntity));
    }

    @GetMapping(value = "/{jobId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamJob(@PathVariable UUID jobId) {
        JobEntity jobEntity = jobPersistenceService.findJobById(jobId);
        JobStatus jobStatus = jobEntity.getJobStatus();
        if (jobStatus == JobStatus.COMPLETED) {
            return createEphemeralSseEmitter(new VerifyStreamEvent("done", "", null));
        }
        if (jobStatus == JobStatus.FAILED) {
            String errorText = jobEntity.getErrorMessage() != null ? jobEntity.getErrorMessage() : "FAILED";
            return createEphemeralErrorSseEmitter(errorText);
        }
        if (jobStatus == JobStatus.FILE_UPLOADED
            || jobStatus == JobStatus.SUBMITTED
            || jobStatus == JobStatus.RUNNING) {
            return createEphemeralSseEmitter(new VerifyStreamEvent("done", "", null));
        }
        return jobStreamRegistryService.register(jobId);
    }

    private SseEmitter createEphemeralSseEmitter(VerifyStreamEvent verifyStreamEvent) {
        SseEmitter sseEmitter = new SseEmitter(30_000L);
        try {
            String jsonPayload = objectMapper.writeValueAsString(verifyStreamEvent);
            sseEmitter.send(SseEmitter.event().name("chunk").data(jsonPayload));
            sseEmitter.complete();
        } catch (Exception exception) {
            log.debug("ephemeral sse chunk failed", exception);
            try {
                sseEmitter.complete();
            } catch (Exception ignored) {
            }
        }
        return sseEmitter;
    }

    private SseEmitter createEphemeralErrorSseEmitter(String message) {
        SseEmitter sseEmitter = new SseEmitter(30_000L);
        try {
            String jsonPayload = objectMapper.writeValueAsString(new StreamErrorPayload(message));
            sseEmitter.send(SseEmitter.event().name("error").data(jsonPayload));
            sseEmitter.complete();
        } catch (Exception exception) {
            log.debug("ephemeral sse error failed", exception);
            try {
                sseEmitter.complete();
            } catch (Exception ignored) {
            }
        }
        return sseEmitter;
    }

    @PostMapping("/{jobId}/test-sync")
    public ResponseEntity<?> testSyncSingleQuery(@PathVariable UUID jobId) {
        try {
            JobEntity jobEntity = jobSyncTestService.runSingleUnprocessedQuerySyncTest(jobId);
            return ResponseEntity.ok(JobStatusResponse.from(jobEntity));
        } catch (IllegalStateException illegalStateException) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                illegalStateException.getMessage());
            problemDetail.setTitle("Sync test not available");
            return ResponseEntity.badRequest().body(problemDetail);
        }
    }

    @PostMapping("/{jobId}/queries")
    public ResponseEntity<Void> addQueriesToJob(
            @PathVariable UUID jobId,
            @RequestBody @Valid AddQueriesRequest addQueriesRequest) {
        jobQuerySubmissionService.submitQueries(jobId, addQueriesRequest.queries(), addQueriesRequest.plan());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{jobId}/analysis")
    public ResponseEntity<JobAnalysisDetailResponse> getJobAnalysis(@PathVariable UUID jobId) {
        JobAnalysisAggregate aggregate = jobPersistenceService.findJobAnalysisAggregate(jobId);
        List<ResultDetailResponse> resultDetails = aggregate.auditHistories().stream()
            .map(ResultDetailResponse::from)
            .toList();
        return ResponseEntity.ok(JobAnalysisDetailResponse.from(aggregate.job(), aggregate.project(), resultDetails));
    }

    @GetMapping("/{jobId}/results")
    public ResponseEntity<List<ResultSummaryResponse>> getJobResults(@PathVariable UUID jobId) {
        jobPersistenceService.findJobById(jobId);
        List<ResultSummaryResponse> summaries = jobPersistenceService.findResultsByJobId(jobId)
            .stream()
            .map(ResultSummaryResponse::from)
            .toList();
        return ResponseEntity.ok(summaries);
    }

    @PostMapping("/{jobId}/pdf/request")
    public ResponseEntity<PdfGenerationStartResult> requestPdfGeneration(@PathVariable UUID jobId) {
        var result = jobPersistenceService.tryMarkPdfGeneratingAndPublish(jobId);
        if (result.accepted()) {
            asyncPdfReportService.generatePdfReport(jobId);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{jobId}/pdf/download")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable UUID jobId) throws IOException {
        JobEntity jobEntity = jobPersistenceService.findJobById(jobId);
        if (!PdfJobStatusValues.COMPLETED.equals(jobEntity.getPdfStatus())) {
            return ResponseEntity.notFound().build();
        }
        String storedPath = jobEntity.getPdfFilePath();
        if (storedPath == null || storedPath.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        Path baseDir = pdfStorageConfig.getTempDirectory().normalize().toAbsolutePath();
        Path filePath = Path.of(storedPath).normalize().toAbsolutePath();
        if (!filePath.startsWith(baseDir)) {
            return ResponseEntity.notFound().build();
        }
        if (!Files.isRegularFile(filePath)) {
            return ResponseEntity.notFound().build();
        }
        byte[] pdfBytes = Files.readAllBytes(filePath);
        String filename = buildPdfFilename(jobEntity.getBrandName());
        ContentDisposition contentDisposition = ContentDisposition.attachment()
            .filename(filename, StandardCharsets.UTF_8)
            .build();
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
            .body(pdfBytes);
    }

    private static String buildPdfFilename(String brandName) {
        String segment = sanitizeFilenameSegment(brandName);
        String datePart = LocalDate.now(ZONE_TOKYO).format(PDF_DATE);
        return segment + "_GEOレポート_" + datePart + ".pdf";
    }

    private static String sanitizeFilenameSegment(String brandName) {
        if (brandName == null || brandName.isBlank()) {
            return "brand";
        }
        String sanitized = brandName.replaceAll("[\\\\/:*?\"<>|]", "_").strip();
        return sanitized.isEmpty() ? "brand" : sanitized;
    }
}
