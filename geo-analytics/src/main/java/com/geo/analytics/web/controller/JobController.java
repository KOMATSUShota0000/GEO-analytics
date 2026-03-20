package com.geo.analytics.web.controller;

import com.geo.analytics.application.service.AsyncSgeMeasurementService;
import com.geo.analytics.application.service.JobPersistenceService;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.QueryEntity;
import com.geo.analytics.web.dto.AddQueriesRequest;
import com.geo.analytics.web.dto.CreateJobRequest;
import com.geo.analytics.web.dto.JobStatusResponse;
import com.geo.analytics.web.dto.ResultSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {
    private final JobPersistenceService jobPersistenceService;
    private final AsyncSgeMeasurementService asyncSgeMeasurementService;

    public JobController(
            JobPersistenceService jobPersistenceService,
            AsyncSgeMeasurementService asyncSgeMeasurementService) {
        this.jobPersistenceService = jobPersistenceService;
        this.asyncSgeMeasurementService = asyncSgeMeasurementService;
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

}
