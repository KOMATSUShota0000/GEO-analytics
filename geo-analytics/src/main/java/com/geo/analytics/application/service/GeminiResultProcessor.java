package com.geo.analytics.application.service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.ConsultantOutputData;
import com.geo.analytics.application.dto.SomScoreData;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.ProjectEntity;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.model.SomRawMetrics;
import com.geo.analytics.domain.service.EntityNormalizer;
import com.geo.analytics.domain.service.SomScoreCalculator;
import com.geo.analytics.infrastructure.ai.GeminiBatchApiException;
import com.geo.analytics.infrastructure.ai.dto.GeminiBatchOutputRecord;
import com.geo.analytics.infrastructure.persistence.JsonbOperations;
import com.geo.analytics.infrastructure.persistence.JsonbSerializationException;
import com.geo.analytics.infrastructure.repository.ProjectRepository;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import com.geo.analytics.infrastructure.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
@Service
public class GeminiResultProcessor {
    private static final Logger log = LoggerFactory.getLogger(GeminiResultProcessor.class);
    private static final int OUTPUT_LINE_LOG_MAX_CHARS = 2000;
    private final JobPersistenceService jobPersistenceService;
    private final ObjectMapper objectMapper;
    private final SomScoreParser somScoreParser;
    private final JsonbOperations jsonbOperations;
    private final ProjectRepository projectRepository;
    public GeminiResultProcessor(
            JobPersistenceService jobPersistenceService,
            ObjectMapper objectMapper,
            SomScoreParser somScoreParser,
            JsonbOperations jsonbOperations,
            ProjectRepository projectRepository) {
        this.jobPersistenceService = jobPersistenceService;
        this.objectMapper = objectMapper;
        this.somScoreParser = somScoreParser;
        this.jsonbOperations = jsonbOperations;
        this.projectRepository = projectRepository;
    }
    @Transactional
    public void processOutputJsonlAndUpsertResults(JobEntity jobEntity, String outputJsonlContent) {
        SubscriptionPlan plan = Objects.requireNonNullElse(jobEntity.getSubscriptionPlan(), SubscriptionPlan.STANDARD);
        List<String> competitorHosts = loadCompetitorHosts(jobEntity);
        boolean isProPlan = plan == SubscriptionPlan.PRO;
        String mainBrand = jobEntity.getBrandName();
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
                ConsultantOutputData consultantOutputData = somScoreParser.parseConsultantOutput(aiResponseText);
                SomScoreData metrics = consultantOutputData.toSomScoreData();
                int tc = metrics.tokenCount() != null ? metrics.tokenCount() : 0;
                int rp = metrics.rankPosition() != null ? metrics.rankPosition() : 0;
                double si = metrics.sentimentIntensity() != null ? metrics.sentimentIntensity() : 0.0;
                String ext = consultantOutputData.extractedBrandMention();
                String rawName = ext != null && !ext.isBlank() ? ext : mainBrand;
                String resolved = EntityNormalizer.resolve(rawName, mainBrand, competitorHosts, isProPlan);
                boolean isProAnalysis = isProPlan;
                SomRawMetrics rawMetrics = new SomRawMetrics(tc, rp, si, isProAnalysis);
                double somScore = SomScoreCalculator.calculate(rawMetrics);
                boolean brand = tc > 0 || rp > 0;
                int overall = (int) Math.round(Math.clamp(somScore, 0.0, 100.0));
                jobPersistenceService.findQueryById(queryId).ifPresent(queryEntity ->
                    jobPersistenceService.upsertAuditHistoryForJobQuery(
                        jobEntity.getId(),
                        queryId,
                        queryEntity.getQueryText(),
                        jsonbOperations.serialize(consultantOutputData),
                        somScore,
                        brand,
                        rp,
                        overall,
                        resolved,
                        tc,
                        rp,
                        si));
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
    private List<String> loadCompetitorHosts(JobEntity jobEntity) {
        UUID projectId = jobEntity.getProjectId();
        if (projectId == null) {
            return List.of();
        }
        UUID wid = Objects.requireNonNullElse(jobEntity.getWorkspaceId(), DefaultTenantIds.WORKSPACE_ID);
        return TenantContext.executeWithTenant(wid, () -> projectRepository.findById(projectId)
            .map(ProjectEntity::getCompetitorUrls)
            .orElse(List.of())
            .stream()
            .map(EntityNormalizer::hostLabelFromUrl)
            .filter(s -> !s.isBlank())
            .toList());
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
