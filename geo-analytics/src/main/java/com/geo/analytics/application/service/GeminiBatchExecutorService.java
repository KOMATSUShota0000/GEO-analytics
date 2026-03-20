package com.geo.analytics.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.SomScoreData;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.QueryEntity;
import com.geo.analytics.domain.entity.ResultEntity;
import com.geo.analytics.domain.enums.JobStatus;
import com.geo.analytics.infrastructure.ai.GeminiBatchApiException;
import com.geo.analytics.infrastructure.ai.GeminiBatchClient;
import com.geo.analytics.infrastructure.ai.dto.GeminiBatchJob;
import com.geo.analytics.infrastructure.ai.dto.GeminiBatchOutputRecord;
import com.geo.analytics.infrastructure.ai.dto.GeminiFileMetadata;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class GeminiBatchExecutorService {
    private final GeminiBatchClient geminiBatchClient;
    private final JsonlGenerationService jsonlGenerationService;
    private final JobPersistenceService jobPersistenceService;
    private final SomScoreParser somScoreParser;
    private final ObjectMapper objectMapper;

    public GeminiBatchExecutorService(
            GeminiBatchClient geminiBatchClient,
            JsonlGenerationService jsonlGenerationService,
            JobPersistenceService jobPersistenceService,
            SomScoreParser somScoreParser,
            ObjectMapper objectMapper) {
        this.geminiBatchClient = geminiBatchClient;
        this.jsonlGenerationService = jsonlGenerationService;
        this.jobPersistenceService = jobPersistenceService;
        this.somScoreParser = somScoreParser;
        this.objectMapper = objectMapper;
    }

    @Async
    public CompletableFuture<Void> uploadAndSubmitBatchJob(JobEntity jobEntity) {
        try {
            List<QueryEntity> unprocessedQueryEntities =
                jobPersistenceService.findUnprocessedQueriesByJobId(jobEntity.getId());
            if (unprocessedQueryEntities.isEmpty()) {
                jobPersistenceService.updateJobStatus(jobEntity.getId(), JobStatus.COMPLETED, null);
                return CompletableFuture.completedFuture(null);
            }
            byte[] batchRequestJsonl = jsonlGenerationService.generateBatchRequestJsonl(
                jobEntity.getBrandName(), unprocessedQueryEntities);
            GeminiFileMetadata uploadedFileMetadata =
                geminiBatchClient.uploadJsonlFile(batchRequestJsonl);
            GeminiBatchJob createdBatchJob =
                geminiBatchClient.createBatchJob(uploadedFileMetadata.name());
            jobPersistenceService.updateJobStatusToRunningWithGeminiJobName(
                jobEntity.getId(), createdBatchJob.name());
        } catch (Exception exception) {
            String failureDetail = exception.getMessage();
            if (failureDetail == null || failureDetail.isBlank()) {
                failureDetail = exception.getClass().getName();
            }
            jobPersistenceService.updateJobStatus(
                jobEntity.getId(), JobStatus.FAILED,
                "uploadAndSubmitBatchJob failed: " + exception.getClass().getSimpleName() + ": " + failureDetail);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Async
    public CompletableFuture<Void> pollAndProcessBatchResult(JobEntity jobEntity) {
        try {
            GeminiBatchJob currentBatchJobStatus =
                geminiBatchClient.getBatchJobStatus(jobEntity.getGeminiJobName());
            String batchJobState = currentBatchJobStatus.state();
            if (geminiBatchClient.shouldAwaitNextPoll(batchJobState)) {
                return CompletableFuture.completedFuture(null);
            }
            if (!geminiBatchClient.isTerminalState(batchJobState)) {
                return CompletableFuture.completedFuture(null);
            }
            if (geminiBatchClient.isSucceededState(batchJobState)) {
                String outputFileName = currentBatchJobStatus.outputConfig()
                    .path("predictions").path("fileName").asText();
                String outputFileContent =
                    geminiBatchClient.downloadOutputFileContent(outputFileName);
                processOutputJsonlAndSaveResults(jobEntity, outputFileContent);
                jobPersistenceService.updateJobStatus(jobEntity.getId(), JobStatus.COMPLETED, null);
            } else {
                String stateDescription = batchJobState == null ? "null" : batchJobState;
                jobPersistenceService.updateJobStatus(
                    jobEntity.getId(), JobStatus.FAILED,
                    "Gemini batch job finished with terminal failure state=" + stateDescription);
            }
        } catch (Exception exception) {
            String failureDetail = exception.getMessage();
            if (failureDetail == null || failureDetail.isBlank()) {
                failureDetail = exception.getClass().getName();
            }
            jobPersistenceService.updateJobStatus(
                jobEntity.getId(), JobStatus.FAILED,
                "pollAndProcessBatchResult failed: " + exception.getClass().getSimpleName() + ": " + failureDetail);
        }
        return CompletableFuture.completedFuture(null);
    }

    private void processOutputJsonlAndSaveResults(JobEntity jobEntity, String outputJsonlContent) {
        List<ResultEntity> resultEntitiesToSave = new ArrayList<>();
        List<UUID> successfullyProcessedQueryIds = new ArrayList<>();
        for (String outputLine : outputJsonlContent.split("\n")) {
            if (outputLine.isBlank()) continue;
            try {
                GeminiBatchOutputRecord outputRecord =
                    objectMapper.readValue(outputLine, GeminiBatchOutputRecord.class);
                if (outputRecord.status() != null && outputRecord.status().code() != 0) continue;
                UUID queryId = UUID.fromString(outputRecord.key());
                String aiResponseText = extractAiResponseText(outputRecord);
                SomScoreData parsedSomScoreData = somScoreParser.parse(aiResponseText);
                jobPersistenceService.findQueryById(queryId).ifPresent(queryEntity -> {
                    resultEntitiesToSave.add(buildResultEntity(
                        jobEntity.getId(), queryEntity.getQueryText(),
                        aiResponseText, parsedSomScoreData));
                    successfullyProcessedQueryIds.add(queryId);
                });
            } catch (JsonProcessingException | IllegalArgumentException malformedException) {
            }
        }
        if (!resultEntitiesToSave.isEmpty()) {
            jobPersistenceService.saveAllResultsAndMarkQueriesAsProcessed(
                resultEntitiesToSave, successfullyProcessedQueryIds);
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

    private ResultEntity buildResultEntity(
            UUID jobId,
            String queryText,
            String rawAiResponseJson,
            SomScoreData parsedSomScoreData) {
        ResultEntity resultEntity = new ResultEntity();
        resultEntity.setJobId(jobId);
        resultEntity.setQuery(queryText);
        resultEntity.setRawResponse(rawAiResponseJson);
        resultEntity.setSomScore(
            parsedSomScoreData.confidenceScore() != null ? parsedSomScoreData.confidenceScore() : 0.0);
        resultEntity.setBrandMentioned(
            parsedSomScoreData.brandMentioned() != null ? parsedSomScoreData.brandMentioned() : false);
        resultEntity.setMentionRank(parsedSomScoreData.mentionRank());
        return resultEntity;
    }
}
