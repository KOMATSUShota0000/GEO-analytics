package com.geo.analytics.web.controller;

import com.geo.analytics.application.port.PdfReportPort;
import com.geo.analytics.application.service.AsyncSgeMeasurementService;
import com.geo.analytics.application.service.JobPersistenceService;
import com.geo.analytics.application.service.JobSyncTestService;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.QueryEntity;
import com.geo.analytics.web.dto.AddQueriesRequest;
import com.geo.analytics.web.dto.CreateJobRequest;
import com.geo.analytics.web.dto.JobStatusResponse;
import com.geo.analytics.web.dto.ResultSummaryResponse;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {
    private static final Logger log = LoggerFactory.getLogger(JobController.class);
    private static final ZoneId ZONE_TOKYO = ZoneId.of("Asia/Tokyo");
    private static final DateTimeFormatter PDF_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final JobPersistenceService jobPersistenceService;
    private final AsyncSgeMeasurementService asyncSgeMeasurementService;
    private final PdfReportPort pdfReportPort;
    private final JobSyncTestService jobSyncTestService;

    public JobController(
            JobPersistenceService jobPersistenceService,
            AsyncSgeMeasurementService asyncSgeMeasurementService,
            PdfReportPort pdfReportPort,
            JobSyncTestService jobSyncTestService) {
        this.jobPersistenceService = jobPersistenceService;
        this.asyncSgeMeasurementService = asyncSgeMeasurementService;
        this.pdfReportPort = pdfReportPort;
        this.jobSyncTestService = jobSyncTestService;
    }

    @PostMapping
    public ResponseEntity<JobStatusResponse> createJob(@RequestBody @Valid CreateJobRequest createJobRequest) {
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
        jobPersistenceService.registerQueriesAndTransitionToFileUploaded(jobId, addQueriesRequest.queries());
        JobEntity jobEntityForSge = jobPersistenceService.findJobById(jobId);
        List<QueryEntity> queryEntitiesForSge = jobPersistenceService.findQueriesByJobId(jobId);
        asyncSgeMeasurementService.measureSgeForJob(jobEntityForSge, queryEntitiesForSge);
        return ResponseEntity.noContent().build();
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

    @GetMapping("/{jobId}/pdf")
    public ResponseEntity<?> getJobPdf(@PathVariable UUID jobId) {
        Optional<JobEntity> jobEntityOptional = jobPersistenceService.findJobByIdOptional(jobId);
        if (jobEntityOptional.isEmpty()) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "解析結果が存在しないためPDFを生成できません");
            problemDetail.setTitle("PDF Not Available");
            return ResponseEntity.badRequest().body(problemDetail);
        }
        JobEntity jobEntity = jobEntityOptional.get();
        if (jobPersistenceService.findResultsByJobId(jobId).isEmpty()) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "解析結果が存在しないためPDFを生成できません");
            problemDetail.setTitle("PDF Not Available");
            return ResponseEntity.badRequest().body(problemDetail);
        }
        try {
            byte[] pdfBytes = pdfReportPort.renderJobReportPdf(jobId);
            String filename = buildPdfFilename(jobEntity.getBrandName());
            ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(pdfBytes);
        } catch (Exception exception) {
            log.error("PDF generation failed jobId={} message={}", jobId, exception.getMessage(), exception);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "PDF generation failed");
            problemDetail.setTitle("PDF Generation Failed");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
        }
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
