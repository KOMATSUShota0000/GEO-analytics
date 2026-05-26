package com.geo.analytics.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.service.JobAnalysisBenchmarkAssembler;
import com.geo.analytics.application.service.JobKnowledgeIngestionService;
import com.geo.analytics.application.service.JobPersistenceService;
import com.geo.analytics.application.service.TaskRegenerationService;
import com.geo.analytics.application.service.StrategyInsightService;
import com.geo.analytics.application.service.JobQuerySubmissionService;
import com.geo.analytics.application.service.JobSyncTestService;
import com.geo.analytics.domain.PdfJobStatusValues;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.enums.JobStatus;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.infrastructure.config.PdfStorageConfig;
import com.geo.analytics.application.dto.JobAnalysisAggregate;
import com.geo.analytics.application.dto.PdfGenerationStartResult;
import com.geo.analytics.web.dto.AddQueriesRequest;
import com.geo.analytics.web.dto.CreateJobRequest;
import com.geo.analytics.web.dto.JobAnalysisDetailResponse;
import com.geo.analytics.web.dto.JobStatusResponse;
import com.geo.analytics.web.dto.ResultDetailRanking;
import com.geo.analytics.web.dto.ResultDetailResponse;
import com.geo.analytics.web.dto.ResultSummaryResponse;
import com.geo.analytics.web.dto.RemediationTaskResponse;
import com.geo.analytics.web.dto.RemediationTaskResponseMask;
import com.geo.analytics.web.dto.StreamErrorPayload;
import com.geo.analytics.web.dto.TaskToneRegenerateRequest;
import com.geo.analytics.web.dto.TaskToneRegenerateResponse;
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
import org.springframework.web.multipart.MultipartFile;
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
import java.util.Locale;
import java.util.Objects;
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
    private final PdfStorageConfig pdfStorageConfig;
    private final ObjectMapper objectMapper;
    private final StrategyInsightService strategyInsightService;
    private final JobAnalysisBenchmarkAssembler jobAnalysisBenchmarkAssembler;
    private final TaskRegenerationService taskRegenerationService;
    private final JobKnowledgeIngestionService jobKnowledgeIngestionService;

    public JobController(
            JobPersistenceService jobPersistenceService,
            JobQuerySubmissionService jobQuerySubmissionService,
            JobSyncTestService jobSyncTestService,
            PdfStorageConfig pdfStorageConfig,
            ObjectMapper objectMapper,
            StrategyInsightService strategyInsightService,
            JobAnalysisBenchmarkAssembler jobAnalysisBenchmarkAssembler,
            TaskRegenerationService taskRegenerationService,
            JobKnowledgeIngestionService jobKnowledgeIngestionService) {
        this.jobPersistenceService = jobPersistenceService;
        this.jobQuerySubmissionService = jobQuerySubmissionService;
        this.jobSyncTestService = jobSyncTestService;
        this.pdfStorageConfig = pdfStorageConfig;
        this.objectMapper = objectMapper;
        this.strategyInsightService = strategyInsightService;
        this.jobAnalysisBenchmarkAssembler = jobAnalysisBenchmarkAssembler;
        this.taskRegenerationService = taskRegenerationService;
        this.jobKnowledgeIngestionService = jobKnowledgeIngestionService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<JobStatusResponse> createJob(
            @RequestHeader(value = "Idempotency-Key", required = false) UUID idempotencyKeyHeader,
            @RequestPart("request") @Valid CreateJobRequest createJobRequest,
            @RequestPart(value = "files", required = false) MultipartFile[] files) {
        log.info("createJob request brandName={}", createJobRequest.brandName());
        UUID idempotencyKey = idempotencyKeyHeader != null ? idempotencyKeyHeader : createJobRequest.idempotencyKey();
        var fields = new JobPersistenceService.JobCreateFields(
                createJobRequest.brandName(),
                createJobRequest.targetUrl(),
                createJobRequest.businessSummary(),
                createJobRequest.targetAudience(),
                createJobRequest.focusPoints(),
                createJobRequest.competitorExtractionMode());
        var outcome = jobPersistenceService.createJobWithIdempotency(fields, idempotencyKey);
        JobEntity createdJobEntity = outcome.jobEntity();
        if (outcome.created() && files != null && files.length > 0) {
            jobKnowledgeIngestionService.ingest(createdJobEntity.getId(), files);
        }
        if (!outcome.created()) {
            return ResponseEntity.ok(JobStatusResponse.from(createdJobEntity));
        }
        jobQuerySubmissionService.submitQueries(
                createdJobEntity.getId(),
                List.of(defaultInitialQuery(createJobRequest.brandName(), createJobRequest.targetUrl())),
                SubscriptionPlan.STANDARD);
        JobEntity responseEntity = jobPersistenceService.findJobById(createdJobEntity.getId());
        URI createdResourceLocation = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{jobId}")
            .buildAndExpand(responseEntity.getId())
            .toUri();
        return ResponseEntity.created(createdResourceLocation).body(JobStatusResponse.from(responseEntity));
    }

    private static String defaultInitialQuery(String brandName, String targetUrl) {
        String b = brandName != null ? brandName.strip() : "";
        String host = "";
        try {
            String tu = targetUrl != null ? targetUrl.strip() : "";
            if (!tu.isEmpty()) {
                URI uri = URI.create(tu);
                String h = uri.getHost();
                if (h != null && !h.isBlank()) {
                    host = h.toLowerCase(Locale.ROOT).startsWith("www.") ? h.substring(4) : h;
                }
            }
        } catch (RuntimeException ignored) {
        }
        if (!b.isEmpty() && !host.isEmpty()) {
            return b + " " + host;
        }
        if (!b.isEmpty()) {
            return b;
        }
        return !host.isEmpty() ? host : "GEO";
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<JobStatusResponse> getJobStatus(@PathVariable UUID jobId) {
        JobEntity jobEntity = jobPersistenceService.findJobById(jobId);
        var rollup = strategyInsightService.rollupJob(jobPersistenceService.findResultsByJobId(jobId));
        return ResponseEntity.ok(JobStatusResponse.from(jobEntity, rollup));
    }

    /**
     * Legacy SSE endpoint: immediately closes with a terminal event. Live token streaming from Gemini has been removed;
     * clients should poll job status over HTTP GET or fetch analysis via REST.
     */
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
        return createEphemeralSseEmitter(new VerifyStreamEvent("done", "", null));
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
            var rollup = strategyInsightService.rollupJob(jobPersistenceService.findResultsByJobId(jobId));
            return ResponseEntity.ok(JobStatusResponse.from(jobEntity, rollup));
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

    @PostMapping("/{jobId}/tasks/{taskId}/regenerate")
    public ResponseEntity<TaskToneRegenerateResponse> regenerateTaskTone(
            @PathVariable UUID jobId,
            @PathVariable UUID taskId,
            @RequestBody @Valid TaskToneRegenerateRequest request) {
        return ResponseEntity.ok(taskRegenerationService.regenerate(jobId, taskId, request.tone()));
    }

    @GetMapping("/{jobId}/analysis")
    public ResponseEntity<JobAnalysisDetailResponse> getJobAnalysis(@PathVariable UUID jobId) {
        JobAnalysisAggregate aggregate = jobPersistenceService.findJobAnalysisAggregate(jobId);
        var audits = aggregate.auditHistories();
        var medZ = strategyInsightService.medianModifiedZ(audits);
        var medSt = strategyInsightService.medianVisibilityStage(audits);
        var rollup = strategyInsightService.rollupJob(audits);
        var jobEnt = aggregate.job();
        String jobDiag = jobEnt.getJobDiagnosticMessage();
        if (jobDiag == null || jobDiag.isBlank()) {
            jobDiag = rollup.diagnosticMessage();
        }
        var storedActs = jobEnt.getJobRecommendedActions();
        List<String> jobActs = storedActs != null && !storedActs.isEmpty()
            ? List.copyOf(storedActs)
            : List.copyOf(rollup.recommendedActions());
        SubscriptionPlan plan = Objects.requireNonNullElse(jobEnt.getAppliedPlan(), SubscriptionPlan.STANDARD);
        List<ResultDetailResponse> resultDetails = ResultDetailRanking.withGbvsCompetitionRanks(
            audits.stream()
                .map(a -> ResultDetailResponse.from(a, strategyInsightService, medZ, plan))
                .toList());
        JobAnalysisBenchmarkAssembler.BenchmarkAttach bench = jobAnalysisBenchmarkAssembler.attach(jobEnt);
        JobPersistenceService.JobAnalysisAttachment attachment =
                jobPersistenceService.loadJobAnalysisAttachment(jobId, audits);
        List<RemediationTaskResponse> remediationTasksMasked =
                RemediationTaskResponseMask.apply(bench.factBasedScore(), attachment.remediationTasks());
        return ResponseEntity.ok(JobAnalysisDetailResponse.from(
            jobEnt,
            aggregate.project(),
            resultDetails,
            jobDiag,
            jobActs,
            medZ,
            medSt,
            bench.factBasedScore(),
            bench.rubricGaps(),
            attachment.scoreBreakdown(),
            remediationTasksMasked,
            objectMapper));
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
        return ResponseEntity.ok(jobPersistenceService.tryMarkPdfGeneratingAndPublish(jobId));
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
