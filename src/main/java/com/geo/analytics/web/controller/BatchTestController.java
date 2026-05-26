package com.geo.analytics.web.controller;

import com.geo.analytics.application.service.AsyncBatchService;
import com.geo.analytics.infrastructure.ai.GeminiBatchClient;
import com.geo.analytics.infrastructure.ai.dto.GeminiBatchJob;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/test/batch")
@PreAuthorize("hasRole('ADMIN')")
public class BatchTestController {
    private final AsyncBatchService asyncBatchService;
    private final GeminiBatchClient geminiBatchClient;

    public BatchTestController(AsyncBatchService asyncBatchService, GeminiBatchClient geminiBatchClient) {
        this.asyncBatchService = asyncBatchService;
        this.geminiBatchClient = geminiBatchClient;
    }

    @GetMapping("/run")
    public ResponseEntity<String> forceRunBatchSubmission() {
        asyncBatchService.submitFileUploadedJobsToBatchApi();
        return ResponseEntity.ok("submitFileUploadedJobsToBatchApi triggered");
    }

    @GetMapping("/poll")
    public ResponseEntity<String> forceRunBatchPolling() {
        asyncBatchService.pollRunningJobsAndProcessCompletedResults();
        return ResponseEntity.ok("pollRunningJobsAndProcessCompletedResults triggered");
    }

    @GetMapping("/list")
    public ResponseEntity<List<GeminiBatchJob>> listBatchJobs() {
        List<GeminiBatchJob> batchJobs = geminiBatchClient.listBatchJobs();
        return ResponseEntity.ok(batchJobs);
    }

    @GetMapping("/cancel")
    public ResponseEntity<String> cancelBatchJob(@RequestParam String jobName) {
        geminiBatchClient.cancelBatchJob(jobName);
        return ResponseEntity.ok("Cancel request sent for: " + jobName);
    }
}
