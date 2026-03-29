package com.geo.analytics.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.ConsultantOutputData;
import com.geo.analytics.domain.entity.AuditHistoryEntity;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.infrastructure.ai.GeminiBatchClient;
import com.geo.analytics.infrastructure.ai.dto.GapAnalystJsonlLine;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class GapBatchSubmissionService {
    private static final int RESPONSE_EXCERPT = 1800;
    private final JobPersistenceService jobPersistenceService;
    private final GeminiBatchClient geminiBatchClient;
    private final StrategyInsightService strategyInsightService;
    private final ObjectMapper objectMapper;

    public GapBatchSubmissionService(
            JobPersistenceService jobPersistenceService,
            GeminiBatchClient geminiBatchClient,
            StrategyInsightService strategyInsightService,
            ObjectMapper objectMapper) {
        this.jobPersistenceService = jobPersistenceService;
        this.geminiBatchClient = geminiBatchClient;
        this.strategyInsightService = strategyInsightService;
        this.objectMapper = objectMapper;
    }

    public void submitGapAnalysisBatch(
            UUID jobId,
            List<AuditHistoryEntity> outlierRows,
            double medZ,
            int medSt,
            String trendClip) {
        if (outlierRows == null || outlierRows.isEmpty()) {
            return;
        }
        var keyOpt = jobPersistenceService.claimGapBatchIdempotencyKeyForUpdate(jobId);
        if (keyOpt.isEmpty()) {
            return;
        }
        JobEntity jobEntity = jobPersistenceService.findJobById(jobId);
        String existing = jobEntity.getGapAnalysisGeminiJobName();
        if (existing != null && !existing.isBlank()) {
            return;
        }
        UUID idempotencyKey = keyOpt.get();
        var lines = new ArrayList<GapAnalystJsonlLine>();
        for (AuditHistoryEntity row : outlierRows) {
            var excerpt = extractResponseExcerpt(row.getRawResponse());
            var prompt = strategyInsightService.buildGapAnalystFullPrompt(row, medZ, medSt, trendClip, excerpt);
            lines.add(new GapAnalystJsonlLine(row.getId(), prompt));
        }
        var path = geminiBatchClient.writeGapAnalystBatchJsonlToTempFile(lines);
        var meta = geminiBatchClient.uploadJsonlFile(path);
        var batch = geminiBatchClient.createBatchJob(meta, idempotencyKey.toString());
        jobPersistenceService.saveGapAnalysisGeminiJobName(jobId, batch.name());
    }

    private String extractResponseExcerpt(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            var data = objectMapper.readValue(raw, ConsultantOutputData.class);
            var r = data.response();
            if (r != null && !r.isBlank()) {
                var t = r.strip();
                return t.length() <= RESPONSE_EXCERPT ? t : t.substring(0, RESPONSE_EXCERPT);
            }
        } catch (Exception ignored) {
        }
        var s = raw.strip();
        return s.length() <= RESPONSE_EXCERPT ? s : s.substring(0, RESPONSE_EXCERPT);
    }
}
