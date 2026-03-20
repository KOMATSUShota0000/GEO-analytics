package com.geo.analytics.web.controller;

import com.geo.analytics.application.service.JobPersistenceService;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.web.dto.JobAnalysisDetailResponse;
import com.geo.analytics.web.dto.ResultDetailResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
public class ResultController {
    private final JobPersistenceService jobPersistenceService;

    public ResultController(JobPersistenceService jobPersistenceService) {
        this.jobPersistenceService = jobPersistenceService;
    }

    @GetMapping("/{jobId}/analysis")
    public ResponseEntity<JobAnalysisDetailResponse> getJobAnalysisDetail(@PathVariable UUID jobId) {
        JobEntity jobEntity = jobPersistenceService.findJobById(jobId);
        List<ResultDetailResponse> resultDetails = jobPersistenceService.findResultsByJobId(jobId)
            .stream()
            .map(ResultDetailResponse::from)
            .toList();
        return ResponseEntity.ok(JobAnalysisDetailResponse.from(jobEntity, resultDetails));
    }
}
