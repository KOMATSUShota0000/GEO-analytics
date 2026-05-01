package com.geo.analytics.web.controller;

import com.geo.analytics.application.dto.ConvertProposalToJobOutcome;
import com.geo.analytics.application.dto.UserStrategicKnowledge;
import com.geo.analytics.application.service.QueryProposalService;
import com.geo.analytics.web.dto.ConvertProposalToJobRequest;
import com.geo.analytics.web.dto.ConvertProposalToJobResponse;
import com.geo.analytics.web.dto.QueryProposalKnowledgeRequest;
import com.geo.analytics.web.dto.QueryProposalRequest;
import com.geo.analytics.web.dto.QueryProposalResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/proposals")
public class QueryProposalController {

    private final QueryProposalService queryProposalService;

    public QueryProposalController(QueryProposalService queryProposalService) {
        this.queryProposalService = queryProposalService;
    }

    @PostMapping
    public ResponseEntity<QueryProposalResponse> propose(@Valid @RequestBody QueryProposalRequest request) {
        QueryProposalKnowledgeRequest k = request.knowledge();
        UserStrategicKnowledge knowledge = new UserStrategicKnowledge(
                k.businessDescription(), k.targetAudience(), k.strategicFocus());
        return ResponseEntity.ok(
                QueryProposalResponse.from(
                        queryProposalService.completeProposal(request.url(), knowledge)));
    }

    @PostMapping("/{id}/convert")
    public ResponseEntity<ConvertProposalToJobResponse> convertProposalToJob(
            @PathVariable UUID id, @Valid @RequestBody ConvertProposalToJobRequest request) {
        ConvertProposalToJobOutcome outcome = queryProposalService.convertProposalToJob(id, request.plan());
        UUID jobId = outcome.jobId();
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/jobs/{jobId}")
                .buildAndExpand(jobId)
                .toUri();
        var body = new ConvertProposalToJobResponse(jobId);
        if (!outcome.created()) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.status(HttpStatus.CREATED).location(location).body(body);
    }
}
