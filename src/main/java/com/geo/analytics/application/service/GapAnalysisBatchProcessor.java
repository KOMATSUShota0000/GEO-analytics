package com.geo.analytics.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.GapAnalystJobResponse;
import com.geo.analytics.infrastructure.ai.GeminiBatchApiException;
import com.geo.analytics.infrastructure.ai.dto.GeminiBatchOutputRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class GapAnalysisBatchProcessor {
    private static final Logger log = LoggerFactory.getLogger(GapAnalysisBatchProcessor.class);
    private static final int OUTPUT_LINE_LOG_MAX_CHARS = 2000;
    private final BatchPersistenceService batchPersistence;
    private final ObjectMapper objectMapper;

    public GapAnalysisBatchProcessor(BatchPersistenceService batchPersistence, ObjectMapper objectMapper) {
        this.batchPersistence = batchPersistence;
        this.objectMapper = objectMapper;
    }

    public void processOutputJsonl(UUID jobId, String outputJsonlContent) {
        for (String outputLine : outputJsonlContent.split("\n")) {
            if (outputLine.isBlank()) {
                continue;
            }
            try {
                GeminiBatchOutputRecord outputRecord =
                    objectMapper.readValue(outputLine, GeminiBatchOutputRecord.class);
                if (outputRecord.status() != null && outputRecord.status().code() != 0) {
                    continue;
                }
                UUID auditHistoryId = UUID.fromString(outputRecord.key());
                String aiResponseText = extractAiResponseText(outputRecord);
                GapAnalystJobResponse parsed = GapAnalystJobResponse.parseStructuredJson(aiResponseText, objectMapper);
                String reason = parsed.diagnosticMessage();
                if (reason != null && !reason.isBlank() && !reason.startsWith("【乖離理由】")) {
                    reason = "【乖離理由】" + reason;
                }
                batchPersistence.updateAuditStrategyInsights(
                    auditHistoryId,
                    reason != null ? reason : "",
                    parsed.recommendedActions(),
                    StrategyInsightService.GAP_ANALYSIS_VERSION);
            } catch (JsonProcessingException
                | IllegalArgumentException
                | GeminiBatchApiException exception) {
                String lineForLog = outputLine.length() > OUTPUT_LINE_LOG_MAX_CHARS
                    ? outputLine.substring(0, OUTPUT_LINE_LOG_MAX_CHARS) + "..."
                    : outputLine;
                log.warn("Skipping gap batch output line jobId={} line={}", jobId, lineForLog, exception);
            }
        }
        batchPersistence.markGapAnalysisCompleted(jobId, true);
    }

    private String extractAiResponseText(GeminiBatchOutputRecord outputRecord) {
        JsonNode responseNode = outputRecord.response();
        if (responseNode == null || responseNode.isNull()) {
            throw new GeminiBatchApiException(
                "Gap batch output record has null response for key: " + outputRecord.key());
        }
        JsonNode candidatesList = responseNode.path("candidates");
        if (!candidatesList.isArray() || candidatesList.isEmpty()) {
            throw new GeminiBatchApiException(
                "No candidates in gap batch output response for key: " + outputRecord.key());
        }
        JsonNode textNode = candidatesList.get(0)
            .path("content").path("parts").get(0).path("text");
        if (textNode == null || textNode.isNull() || textNode.isMissingNode()) {
            throw new GeminiBatchApiException(
                "Could not extract text from gap batch output for key: " + outputRecord.key());
        }
        return textNode.asText();
    }
}
