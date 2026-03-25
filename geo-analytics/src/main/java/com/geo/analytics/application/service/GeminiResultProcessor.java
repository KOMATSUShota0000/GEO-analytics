package com.geo.analytics.application.service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.SomScoreData;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.infrastructure.ai.GeminiBatchApiException;
import com.geo.analytics.infrastructure.ai.dto.GeminiBatchOutputRecord;
import com.geo.analytics.infrastructure.persistence.JsonbOperations;
import com.geo.analytics.infrastructure.persistence.JsonbSerializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
@Service
public class GeminiResultProcessor {
    private static final Logger log = LoggerFactory.getLogger(GeminiResultProcessor.class);
    private static final int OUTPUT_LINE_LOG_MAX_CHARS = 2000;
    private final JobPersistenceService jobPersistenceService;
    private final ObjectMapper objectMapper;
    private final SomScoreParser somScoreParser;
    private final JsonbOperations jsonbOperations;
    public GeminiResultProcessor(
            JobPersistenceService jobPersistenceService,
            ObjectMapper objectMapper,
            SomScoreParser somScoreParser,
            JsonbOperations jsonbOperations) {
        this.jobPersistenceService = jobPersistenceService;
        this.objectMapper = objectMapper;
        this.somScoreParser = somScoreParser;
        this.jsonbOperations = jsonbOperations;
    }
    @Transactional
    public void processOutputJsonlAndUpsertResults(JobEntity jobEntity, String outputJsonlContent) {
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
                UUID queryId = UUID.fromString(outputRecord.key());
                String aiResponseText = extractAiResponseText(outputRecord);
                SomScoreData parsedSomScoreData = somScoreParser.parse(aiResponseText);
                double somScore = SomScoreRules.computeFromCitationRank(
                    parsedSomScoreData.mentionRank(),
                    parsedSomScoreData.brandMentioned());
                jobPersistenceService.findQueryById(queryId).ifPresent(queryEntity ->
                    jobPersistenceService.upsertAuditHistoryForJobQuery(
                        jobEntity.getId(),
                        queryId,
                        queryEntity.getQueryText(),
                        jsonbOperations.serialize(parsedSomScoreData),
                        somScore,
                        Boolean.TRUE.equals(parsedSomScoreData.brandMentioned()),
                        parsedSomScoreData.mentionRank(),
                        parsedSomScoreData.overallScore()));
            } catch (JsonProcessingException
                | IllegalArgumentException
                | JsonbSerializationException
                | GeminiBatchApiException exception) {
                String lineForLog = outputLine.length() > OUTPUT_LINE_LOG_MAX_CHARS
                    ? outputLine.substring(0, OUTPUT_LINE_LOG_MAX_CHARS) + "..."
                    : outputLine;
                log.warn(
                    "Skipping batch output JSONL line jobId={} outputLine={}",
                    jobEntity.getId(),
                    lineForLog,
                    exception);
            }
        }
    }
    private String extractAiResponseText(GeminiBatchOutputRecord outputRecord) {
        JsonNode responseNode = outputRecord.response();
        if (responseNode == null || responseNode.isNull()) {
            throw new GeminiBatchApiException(
                "Batch output record has null response for key: " + outputRecord.key());
        }
        JsonNode candidatesList = responseNode.path("candidates");
        if (!candidatesList.isArray() || candidatesList.isEmpty()) {
            throw new GeminiBatchApiException(
                "No candidates in batch output response for key: " + outputRecord.key());
        }
        JsonNode textNode = candidatesList.get(0)
            .path("content").path("parts").get(0).path("text");
        if (textNode == null || textNode.isNull() || textNode.isMissingNode()) {
            throw new GeminiBatchApiException(
                "Could not extract text from batch output for key: " + outputRecord.key());
        }
        return textNode.asText();
    }
}
