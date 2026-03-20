package com.geo.analytics.infrastructure.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.infrastructure.ai.dto.GeminiBatchJob;
import com.geo.analytics.infrastructure.ai.dto.GeminiBatchJobListResponse;
import com.geo.analytics.infrastructure.ai.dto.GeminiFileMetadata;
import com.geo.analytics.infrastructure.ai.dto.GeminiFileUploadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class GeminiBatchClient {
    private static final Logger logger = LoggerFactory.getLogger(GeminiBatchClient.class);
    private static final String GEMINI_API_BASE_URL = "https://generativelanguage.googleapis.com";
    private static final String GEMINI_MODEL_ID = "gemini-2.5-flash";
    private static final String JOB_STATE_SUCCEEDED = "JOB_STATE_SUCCEEDED";
    private static final String JOB_STATE_FAILED = "JOB_STATE_FAILED";
    private static final String JOB_STATE_CANCELLED = "JOB_STATE_CANCELLED";
    private static final String JOB_STATE_PENDING = "JOB_STATE_PENDING";
    private static final String BATCH_STATE_PENDING = "BATCH_STATE_PENDING";
    private static final String BATCH_STATE_SUCCEEDED = "BATCH_STATE_SUCCEEDED";
    private static final String BATCH_STATE_FAILED = "BATCH_STATE_FAILED";
    private static final String BATCH_STATE_CANCELLED = "BATCH_STATE_CANCELLED";

    private final RestClient restClient;
    private final String geminiApiKey;
    private final ObjectMapper objectMapper;

    public GeminiBatchClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.ai.gemini.api-key}") String geminiApiKey) {
        this.restClient = restClientBuilder.baseUrl(GEMINI_API_BASE_URL).build();
        this.objectMapper = objectMapper;
        this.geminiApiKey = geminiApiKey;
    }

    public GeminiFileMetadata uploadJsonlFile(byte[] jsonlContent) {
        logger.info("uploadJsonlFile started - payload size: {} bytes", jsonlContent.length);
        String boundary = "geo_analytics_boundary_" + System.currentTimeMillis();
        byte[] multipartBody = buildMultipartRelatedBody(boundary, jsonlContent);
        String uploadUrl = GEMINI_API_BASE_URL + "/upload/v1beta/files?key=" + maskApiKey(geminiApiKey);
        logger.info("uploadJsonlFile - sending POST to {}", uploadUrl);
        try {
            GeminiFileUploadResponse uploadResponse = restClient.post()
                .uri(URI.create(GEMINI_API_BASE_URL + "/upload/v1beta/files?key=" + geminiApiKey))
                .contentType(new MediaType("multipart", "related", Map.of("boundary", boundary)))
                .header("X-Goog-Upload-Protocol", "multipart")
                .body(multipartBody)
                .retrieve()
                .body(GeminiFileUploadResponse.class);
            if (uploadResponse == null || uploadResponse.file() == null) {
                logger.error("uploadJsonlFile - Gemini File API returned null response");
                throw new GeminiBatchApiException("Gemini File API returned null during JSONL upload");
            }
            logger.info("uploadJsonlFile - success, file name: {}, uri: {}",
                uploadResponse.file().name(), uploadResponse.file().uri());
            return uploadResponse.file();
        } catch (HttpClientErrorException httpClientErrorException) {
            logger.error("uploadJsonlFile - HTTP client error: status={}, body={}",
                httpClientErrorException.getStatusCode(),
                httpClientErrorException.getResponseBodyAsString(),
                httpClientErrorException);
            throw new GeminiBatchApiException(
                "File upload failed with HTTP " + httpClientErrorException.getStatusCode()
                    + ": " + httpClientErrorException.getResponseBodyAsString(),
                httpClientErrorException);
        } catch (HttpServerErrorException httpServerErrorException) {
            logger.error("uploadJsonlFile - HTTP server error: status={}, body={}",
                httpServerErrorException.getStatusCode(),
                httpServerErrorException.getResponseBodyAsString(),
                httpServerErrorException);
            throw new GeminiBatchApiException(
                "File upload failed with HTTP " + httpServerErrorException.getStatusCode()
                    + ": " + httpServerErrorException.getResponseBodyAsString(),
                httpServerErrorException);
        } catch (GeminiBatchApiException geminiBatchApiException) {
            throw geminiBatchApiException;
        } catch (Exception exception) {
            logger.error("uploadJsonlFile - unexpected error: {}", exception.getMessage(), exception);
            throw new GeminiBatchApiException("Unexpected error during JSONL upload: " + exception.getMessage(), exception);
        }
    }

    public GeminiBatchJob createBatchJob(String uploadedInputFileName) {
        logger.info("createBatchJob started - input file: {}", uploadedInputFileName);
        Map<String, Object> batchCreateRequestBody = Map.of(
            "batch", Map.of(
                "input_config", Map.of("file_name", uploadedInputFileName)
            )
        );
        String batchCreateUrl = GEMINI_API_BASE_URL
            + "/v1beta/models/" + GEMINI_MODEL_ID
            + ":batchGenerateContent?key=" + geminiApiKey;
        logger.info("createBatchJob - sending POST to /v1beta/models/{}:batchGenerateContent, body: {}",
            GEMINI_MODEL_ID, batchCreateRequestBody);
        try {
            GeminiBatchJob createdBatchJob = restClient.post()
                .uri(URI.create(batchCreateUrl))
                .contentType(MediaType.APPLICATION_JSON)
                .body(batchCreateRequestBody)
                .retrieve()
                .body(GeminiBatchJob.class);
            if (createdBatchJob == null) {
                logger.error("createBatchJob - Gemini Batch API returned null response");
                throw new GeminiBatchApiException("Gemini Batch API returned null when creating batch job");
            }
            logger.info("createBatchJob - success, job name: {}, state: {}",
                createdBatchJob.name(), createdBatchJob.state());
            return createdBatchJob;
        } catch (HttpClientErrorException httpClientErrorException) {
            logger.error("createBatchJob - HTTP client error: status={}, body={}",
                httpClientErrorException.getStatusCode(),
                httpClientErrorException.getResponseBodyAsString(),
                httpClientErrorException);
            throw new GeminiBatchApiException(
                "Batch job creation failed with HTTP " + httpClientErrorException.getStatusCode()
                    + ": " + httpClientErrorException.getResponseBodyAsString(),
                httpClientErrorException);
        } catch (HttpServerErrorException httpServerErrorException) {
            logger.error("createBatchJob - HTTP server error: status={}, body={}",
                httpServerErrorException.getStatusCode(),
                httpServerErrorException.getResponseBodyAsString(),
                httpServerErrorException);
            throw new GeminiBatchApiException(
                "Batch job creation failed with HTTP " + httpServerErrorException.getStatusCode()
                    + ": " + httpServerErrorException.getResponseBodyAsString(),
                httpServerErrorException);
        } catch (GeminiBatchApiException geminiBatchApiException) {
            throw geminiBatchApiException;
        } catch (Exception exception) {
            logger.error("createBatchJob - unexpected error: {}", exception.getMessage(), exception);
            throw new GeminiBatchApiException(
                "Unexpected error during batch job creation: " + exception.getMessage(), exception);
        }
    }

    public GeminiBatchJob getBatchJobStatus(String geminiBatchJobName) {
        logger.info("getBatchJobStatus - polling job: {}", geminiBatchJobName);
        String statusUrl = GEMINI_API_BASE_URL + "/v1beta/" + geminiBatchJobName + "?key=" + geminiApiKey;
        try {
            String rawBatchJobStatusJson = restClient.get()
                .uri(URI.create(statusUrl))
                .retrieve()
                .body(String.class);
            if (rawBatchJobStatusJson == null || rawBatchJobStatusJson.isBlank()) {
                logger.error("getBatchJobStatus - Gemini Batch API returned empty body for job: {}", geminiBatchJobName);
                throw new GeminiBatchApiException(
                    "Gemini Batch API returned empty body when polling job: " + geminiBatchJobName);
            }
            logger.info("getBatchJobStatus - raw JSON response: {}", rawBatchJobStatusJson);
            GeminiBatchJob batchJobStatus =
                objectMapper.readValue(rawBatchJobStatusJson, GeminiBatchJob.class);
            logger.info("getBatchJobStatus - job: {}, state: {}", batchJobStatus.name(), batchJobStatus.state());
            return batchJobStatus;
        } catch (JsonProcessingException jsonProcessingException) {
            logger.error("getBatchJobStatus - failed to parse JSON for job {}: {}",
                geminiBatchJobName, jsonProcessingException.getMessage(), jsonProcessingException);
            throw new GeminiBatchApiException(
                "Failed to parse batch job status JSON for job: " + geminiBatchJobName,
                jsonProcessingException);
        } catch (HttpClientErrorException httpClientErrorException) {
            logger.error("getBatchJobStatus - HTTP client error: status={}, body={}",
                httpClientErrorException.getStatusCode(),
                httpClientErrorException.getResponseBodyAsString(),
                httpClientErrorException);
            throw new GeminiBatchApiException(
                "Batch job status check failed with HTTP " + httpClientErrorException.getStatusCode(),
                httpClientErrorException);
        } catch (GeminiBatchApiException geminiBatchApiException) {
            throw geminiBatchApiException;
        } catch (Exception exception) {
            logger.error("getBatchJobStatus - unexpected error for job {}: {}", geminiBatchJobName, exception.getMessage(), exception);
            throw new GeminiBatchApiException(
                "Unexpected error during job status poll: " + exception.getMessage(), exception);
        }
    }

    public String downloadOutputFileContent(String outputFileName) {
        logger.info("downloadOutputFileContent started - file: {}", outputFileName);
        String downloadUrl = GEMINI_API_BASE_URL + "/v1beta/" + outputFileName + "?key=" + geminiApiKey + "&alt=media";
        try {
            String rawContent = restClient.get()
                .uri(URI.create(downloadUrl))
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_PLAIN_VALUE)
                .retrieve()
                .body(String.class);
            if (rawContent == null || rawContent.isBlank()) {
                logger.error("downloadOutputFileContent - received empty content for file: {}", outputFileName);
                throw new GeminiBatchApiException(
                    "Gemini File API returned empty content for output file: " + outputFileName);
            }
            logger.info("downloadOutputFileContent - success, received {} chars", rawContent.length());
            return rawContent;
        } catch (HttpClientErrorException httpClientErrorException) {
            logger.error("downloadOutputFileContent - HTTP client error: status={}, body={}",
                httpClientErrorException.getStatusCode(),
                httpClientErrorException.getResponseBodyAsString(),
                httpClientErrorException);
            throw new GeminiBatchApiException(
                "Output file download failed with HTTP " + httpClientErrorException.getStatusCode(),
                httpClientErrorException);
        } catch (GeminiBatchApiException geminiBatchApiException) {
            throw geminiBatchApiException;
        } catch (Exception exception) {
            logger.error("downloadOutputFileContent - unexpected error: {}", exception.getMessage(), exception);
            throw new GeminiBatchApiException(
                "Unexpected error during output file download: " + exception.getMessage(), exception);
        }
    }

    public List<GeminiBatchJob> listBatchJobs() {
        String listUrl = GEMINI_API_BASE_URL + "/v1beta/batches?key=" + geminiApiKey;
        try {
            GeminiBatchJobListResponse listResponse = restClient.get()
                .uri(URI.create(listUrl))
                .retrieve()
                .body(GeminiBatchJobListResponse.class);
            List<GeminiBatchJob> batchJobs = (listResponse != null && listResponse.batches() != null)
                ? listResponse.batches()
                : Collections.emptyList();
            logger.info("listBatchJobs - active batch jobs count: {}", batchJobs.size());
            batchJobs.forEach(job ->
                logger.info("listBatchJobs - name: {}, state: {}", job.name(), job.state()));
            return batchJobs;
        } catch (HttpClientErrorException httpClientErrorException) {
            logger.error("listBatchJobs - HTTP client error: status={}, body={}",
                httpClientErrorException.getStatusCode(),
                httpClientErrorException.getResponseBodyAsString(),
                httpClientErrorException);
            throw new GeminiBatchApiException(
                "List batch jobs failed with HTTP " + httpClientErrorException.getStatusCode(),
                httpClientErrorException);
        } catch (Exception exception) {
            logger.error("listBatchJobs - unexpected error: {}", exception.getMessage(), exception);
            throw new GeminiBatchApiException(
                "Unexpected error during batch job listing: " + exception.getMessage(), exception);
        }
    }

    public void cancelBatchJob(String geminiBatchJobName) {
        String cancelUrl = GEMINI_API_BASE_URL + "/v1beta/" + geminiBatchJobName + ":cancel?key=" + geminiApiKey;
        try {
            restClient.post()
                .uri(URI.create(cancelUrl))
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .toBodilessEntity();
            logger.info("cancelBatchJob - cancel request sent for job: {}", geminiBatchJobName);
        } catch (HttpClientErrorException httpClientErrorException) {
            logger.error("cancelBatchJob - HTTP client error: status={}, body={}",
                httpClientErrorException.getStatusCode(),
                httpClientErrorException.getResponseBodyAsString(),
                httpClientErrorException);
            throw new GeminiBatchApiException(
                "Cancel request failed with HTTP " + httpClientErrorException.getStatusCode(),
                httpClientErrorException);
        } catch (Exception exception) {
            logger.error("cancelBatchJob - unexpected error for job {}: {}", geminiBatchJobName, exception.getMessage(), exception);
            throw new GeminiBatchApiException(
                "Unexpected error during batch job cancellation: " + exception.getMessage(), exception);
        }
    }

    public boolean shouldAwaitNextPoll(String batchJobState) {
        if (batchJobState == null || batchJobState.isBlank()) {
            return true;
        }
        String trimmedState = batchJobState.trim();
        if ("PENDING".equalsIgnoreCase(trimmedState)) {
            return true;
        }
        return JOB_STATE_PENDING.equals(trimmedState) || BATCH_STATE_PENDING.equals(trimmedState);
    }

    public boolean isTerminalState(String batchJobState) {
        if (batchJobState == null || batchJobState.isBlank()) {
            return false;
        }
        String trimmedState = batchJobState.trim();
        return List.of(
            JOB_STATE_SUCCEEDED,
            JOB_STATE_FAILED,
            JOB_STATE_CANCELLED,
            BATCH_STATE_SUCCEEDED,
            BATCH_STATE_FAILED,
            BATCH_STATE_CANCELLED
        ).contains(trimmedState);
    }

    public boolean isSucceededState(String batchJobState) {
        if (batchJobState == null || batchJobState.isBlank()) {
            return false;
        }
        String trimmedState = batchJobState.trim();
        return JOB_STATE_SUCCEEDED.equals(trimmedState) || BATCH_STATE_SUCCEEDED.equals(trimmedState);
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    private byte[] buildMultipartRelatedBody(String boundary, byte[] fileContent) {
        String fileMetadataJson =
            "{\"file\":{\"displayName\":\"batch-requests.jsonl\",\"mimeType\":\"text/plain\"}}";
        String multipartBodyText = "--" + boundary + "\r\n"
            + "Content-Type: application/json; charset=utf-8\r\n\r\n"
            + fileMetadataJson + "\r\n"
            + "--" + boundary + "\r\n"
            + "Content-Type: text/plain\r\n\r\n"
            + new String(fileContent, StandardCharsets.UTF_8) + "\r\n"
            + "--" + boundary + "--\r\n";
        return multipartBodyText.getBytes(StandardCharsets.UTF_8);
    }
}
