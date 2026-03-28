package com.geo.analytics.infrastructure.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.geo.analytics.infrastructure.ai.dto.BatchConfig;
import com.geo.analytics.infrastructure.ai.dto.BatchGenerateContentRequest;
import com.geo.analytics.infrastructure.ai.dto.BatchQueryLine;
import com.geo.analytics.infrastructure.ai.dto.GeminiBatchJob;
import com.geo.analytics.infrastructure.ai.dto.InputConfig;
import com.geo.analytics.infrastructure.ai.dto.GeminiBatchJobListResponse;
import com.geo.analytics.domain.enums.SubscriptionPlan;
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
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GeminiBatchClient {
    private static final Logger logger = LoggerFactory.getLogger(GeminiBatchClient.class);
    private static final String GEMINI_API_BASE_URL = "https://generativelanguage.googleapis.com";
    private static final String JOB_STATE_SUCCEEDED = "JOB_STATE_SUCCEEDED";
    private static final String JOB_STATE_FAILED = "JOB_STATE_FAILED";
    private static final String JOB_STATE_CANCELLED = "JOB_STATE_CANCELLED";
    private static final String JOB_STATE_PENDING = "JOB_STATE_PENDING";
    private static final String BATCH_STATE_PENDING = "BATCH_STATE_PENDING";
    private static final String BATCH_STATE_SUCCEEDED = "BATCH_STATE_SUCCEEDED";
    private static final String BATCH_STATE_FAILED = "BATCH_STATE_FAILED";
    private static final String BATCH_STATE_CANCELLED = "BATCH_STATE_CANCELLED";
    private static final String JOB_STATE_SUBMITTED = "JOB_STATE_SUBMITTED";
    private static final String BATCH_STATE_SUBMITTED = "BATCH_STATE_SUBMITTED";

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

    public Path writeBatchRequestJsonlToTempFile(
            String brandName,
            List<BatchQueryLine> queryLines,
            SubscriptionPlan subscriptionPlan) {
        if (queryLines == null || queryLines.isEmpty()) {
            throw new GeminiBatchApiException("batch query lines are empty");
        }
        try {
            Path tempPath = Files.createTempFile("gemini-batch-req-", ".jsonl");
            tempPath.toFile().deleteOnExit();
            try (OutputStream outputStream = Files.newOutputStream(
                tempPath,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
                try (SequenceWriter sequenceWriter = objectMapper.writer().writeValues(outputStream)) {
                    for (BatchQueryLine batchQueryLine : queryLines) {
                        sequenceWriter.write(
                            buildBatchJsonlLineRootMap(brandName, batchQueryLine, subscriptionPlan));
                        outputStream.write('\n');
                    }
                }
            }
            return tempPath;
        } catch (IOException ioException) {
            throw new GeminiBatchApiException("JSONL stream write failed", ioException);
        }
    }

    public GeminiFileMetadata uploadJsonlFile(Path jsonlPath) {
        try {
            byte[] bytes = Files.readAllBytes(jsonlPath);
            return uploadJsonlFile(bytes);
        } catch (IOException ioException) {
            throw new GeminiBatchApiException("Failed to read JSONL file: " + jsonlPath, ioException);
        }
    }

    public GeminiBatchJob createBatchJob(GeminiFileMetadata uploadedFileMetadata) {
        if (uploadedFileMetadata == null) {
            throw new GeminiBatchApiException("uploaded file metadata is null");
        }
        String fileName = uploadedFileMetadata.name();
        if (fileName == null || fileName.isBlank()) {
            throw new GeminiBatchApiException("file name missing in upload response");
        }
        logger.info("createBatchJob started - file name: {}", fileName);
        BatchGenerateContentRequest batchCreateRequestBody = new BatchGenerateContentRequest(
            new BatchConfig("geo-analytics-batch", new InputConfig(fileName)));
        String batchCreateUrl = GEMINI_API_BASE_URL
            + "/v1beta/models/" + LlmModelNames.GEMINI_31_PRO
            + ":batchGenerateContent?key=" + geminiApiKey;
        logger.info("createBatchJob - sending POST to /v1beta/models/{}:batchGenerateContent",
            LlmModelNames.GEMINI_31_PRO);
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

    public String resolveBatchOutputFileName(GeminiBatchJob batchJob) {
        if (batchJob == null) {
            throw new GeminiBatchApiException("batch job is null");
        }
        String fromResponse = readResponsesFilePath(batchJob.response());
        if (fromResponse != null && !fromResponse.isBlank()) {
            return fromResponse;
        }
        String fromMetadataOutput =
            readResponsesFilePath(batchJob.metadata().path("output"));
        if (fromMetadataOutput != null && !fromMetadataOutput.isBlank()) {
            return fromMetadataOutput;
        }
        throw new GeminiBatchApiException("batch responsesFile missing");
    }

    private static String readResponsesFilePath(JsonNode container) {
        if (container == null || container.isNull() || container.isMissingNode()) {
            return null;
        }
        JsonNode node = container.path("responsesFile");
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String text = node.asText();
        if (text.isBlank()) {
            return null;
        }
        return text;
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
        if (outputFileName == null || outputFileName.isBlank()) {
            throw new GeminiBatchApiException("output file name is blank");
        }
        String normalizedName = outputFileName.startsWith("/") ? outputFileName.substring(1) : outputFileName;
        String metadataUrl = GEMINI_API_BASE_URL + "/v1beta/" + normalizedName + "?key=" + geminiApiKey;
        try {
            GeminiFileMetadata metadata = restClient.get()
                .uri(URI.create(metadataUrl))
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .body(GeminiFileMetadata.class);
            if (metadata == null) {
                throw new GeminiBatchApiException("file metadata is null for " + outputFileName);
            }
            String link = null;
            if (metadata.downloadUri() != null && !metadata.downloadUri().isBlank()) {
                link = metadata.downloadUri();
            } else if (metadata.uri() != null && !metadata.uri().isBlank()) {
                link = metadata.uri();
            }
            if (link == null || link.isBlank()) {
                throw new GeminiBatchApiException(
                    "file metadata missing uri and downloadUri for " + outputFileName);
            }
            String contentUrl = appendApiKeyToGenerativeLanguageUrlIfAbsent(link);
            String rawContent = restClient.get()
                .uri(URI.create(contentUrl))
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
        if ("SUBMITTED".equalsIgnoreCase(trimmedState)) {
            return true;
        }
        return JOB_STATE_PENDING.equals(trimmedState)
            || BATCH_STATE_PENDING.equals(trimmedState)
            || JOB_STATE_SUBMITTED.equals(trimmedState)
            || BATCH_STATE_SUBMITTED.equals(trimmedState);
    }

    public boolean isBatchJobActivelyProcessing(String batchJobState) {
        if (batchJobState == null || batchJobState.isBlank()) {
            return false;
        }
        if (shouldAwaitNextPoll(batchJobState)) {
            return false;
        }
        return !isTerminalState(batchJobState);
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

    private Map<String, Object> buildBatchJsonlLineRootMap(
            String brandName,
            BatchQueryLine batchQueryLine,
            SubscriptionPlan subscriptionPlan) {
        Map<String, Object> textPart = new LinkedHashMap<>();
        textPart.put(
            "text",
            ConsultantPrompts.systemText(subscriptionPlan)
                + "\n\n"
                + ConsultantPrompts.userTextBrandQueryOnly(brandName, batchQueryLine.queryText()));
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("parts", List.of(textPart));
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("contents", List.of(content));
        request.put("generationConfig", ConsultantOutputSchema.batchGenerationConfig(subscriptionPlan));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("key", batchQueryLine.queryId().toString());
        root.put("request", request);
        return root;
    }

    private String appendApiKeyToGenerativeLanguageUrlIfAbsent(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        if (url.contains("key=")) {
            return url;
        }
        try {
            URI parsed = URI.create(url);
            String host = parsed.getHost();
            if (host != null && host.endsWith("generativelanguage.googleapis.com")) {
                return url + (url.contains("?") ? "&" : "?") + "key=" + geminiApiKey;
            }
        } catch (Exception e) {
            return url;
        }
        return url;
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
