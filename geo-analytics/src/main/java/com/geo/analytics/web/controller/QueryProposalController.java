package com.geo.analytics.web.controller;

import com.geo.analytics.application.dto.UserStrategicKnowledge;
import com.geo.analytics.application.service.QueryProposalService;
import com.geo.analytics.web.dto.QueryProposalKnowledgeRequest;
import com.geo.analytics.web.dto.QueryProposalRequest;
import com.geo.analytics.web.dto.QueryProposalResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
                QueryProposalResponse.from(queryProposalService.propose(request.url(), knowledge)));
    }
}
