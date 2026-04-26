package com.geo.analytics.application.service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.ConsultantOutputData;
import com.geo.analytics.application.dto.SomScoreData;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.model.QuotaCreditCalculator;
import com.geo.analytics.domain.model.SomRawMetrics;
import com.geo.analytics.domain.service.EntityNormalizer;
import com.geo.analytics.domain.service.GeoVisibilityCalculatorService;
import com.geo.analytics.domain.service.InformationTheoryBasedAggregator;
import com.geo.analytics.domain.service.JapaneseNlpService;
import com.geo.analytics.infrastructure.ai.GeminiBatchApiException;
import com.geo.analytics.infrastructure.ai.dto.GeminiBatchOutputRecord;
import com.geo.analytics.infrastructure.persistence.JsonbOperations;
import com.geo.analytics.infrastructure.persistence.JsonbSerializationException;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import java.lang.StrictMath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
@Service
public class GeminiResultProcessor {
    private static final Logger log = LoggerFactory.getLogger(GeminiResultProcessor.class);
    private static final int OUTPUT_LINE_LOG_MAX_CHARS = 2000;
    private final BatchPersistenceService batchPersistence;
    private final ObjectMapper objectMapper;
    private final SomScoreParser somScoreParser;
    private final JsonbOperations jsonbOperations;
    private final EntityNormalizer entityNormalizer;
    private final JapaneseNlpService japaneseNlpService;
    private final GeoVisibilityCalculatorService geoVisibilityCalculatorService;
    private final InformationTheoryBasedAggregator informationTheoryBasedAggregator;
    private final GapAnalysisService gapAnalysisService;
    private final StrategyInsightService strategyInsightService;
    private final PlanBasedQuotaManager planBasedQuotaManager;
    public GeminiResultProcessor(
            BatchPersistenceService batchPersistence,
            ObjectMapper objectMapper,
            SomScoreParser somScoreParser,
            JsonbOperations jsonbOperations,
            EntityNormalizer entityNormalizer,
            JapaneseNlpService japaneseNlpService,
            GapAnalysisService gapAnalysisService,
            StrategyInsightService strategyInsightService,
            GeoVisibilityCalculatorService geoVisibilityCalculatorService,
            InformationTheoryBasedAggregator informationTheoryBasedAggregator,
            PlanBasedQuotaManager planBasedQuotaManager) {
        this.batchPersistence = batchPersistence;
        this.objectMapper = objectMapper;
        this.somScoreParser = somScoreParser;
        this.jsonbOperations = jsonbOperations;
        this.entityNormalizer = entityNormalizer;
        this.japaneseNlpService = japaneseNlpService;
        this.gapAnalysisService = gapAnalysisService;
        this.strategyInsightService = strategyInsightService;
        this.geoVisibilityCalculatorService = geoVisibilityCalculatorService;
        this.informationTheoryBasedAggregator = informationTheoryBasedAggregator;
        this.planBasedQuotaManager = planBasedQuotaManager;
    }
    @Transactional("batchTransactionManager")
    public void processOutputJsonlAndUpsertResults(JobEntity jobEntity, String outputJsonlContent) {
        SubscriptionPlan plan = Objects.requireNonNullElse(jobEntity.getAppliedPlan(), SubscriptionPlan.STANDARD);
        List<String> competitorHosts = loadCompetitorHosts(jobEntity);
        boolean isProPlan = plan.usesProTierFeatures();
        String mainBrand = jobEntity.getBrandName();
        UUID tid = Objects.requireNonNullElse(jobEntity.getWorkspaceId(), DefaultTenantIds.WORKSPACE_ID);
        Set<UUID> quotaSettled = new HashSet<>();
        var parsedLines = new ArrayList<BatchParsedLine>();
        for (String outputLine : outputJsonlContent.split("\n")) {
            if (outputLine.isBlank()) {
                continue;
            }
            try {
                GeminiBatchOutputRecord outputRecord =
                    objectMapper.readValue(outputLine, GeminiBatchOutputRecord.class);
                if (outputRecord.status() != null && outputRecord.status().code() != 0) {
                    markBatchQuotaRefundOnError(tid, outputRecord.key(), quotaSettled);
                    continue;
                }
                UUID queryId = UUID.fromString(outputRecord.key());
                String aiResponseText = extractAiResponseText(outputRecord);
                ConsultantOutputData consultantOutputData = somScoreParser.parseConsultantOutput(aiResponseText);
                SomScoreData metrics = consultantOutputData.toSomScoreData();
                double si = metrics.sentimentIntensity() != null ? metrics.sentimentIntensity() : 0.0;
                String ext = consultantOutputData.extractedBrandMention();
                String rawName = ext != null && !ext.isBlank() ? ext : mainBrand;
                String nlpSource = consultantOutputData.response() != null
                    ? consultantOutputData.response().strip()
                    : "";
                si = japaneseNlpService.normalizeSentimentCoefficient(nlpSource, si);
                var responseTokens = geoVisibilityCalculatorService.tokenizeResponseForMentions(nlpSource);
                List<String> needles = GeoVisibilityCalculatorService.splitBrandAliasPhrases(mainBrand, rawName);
                int nounCount = geoVisibilityCalculatorService.countNormalizedMentions(responseTokens, needles);
                int responseTokenLength = japaneseNlpService.totalTokenCount(nlpSource);
                double stuffingDensity = 0.0;
                for (String nd : needles) {
                    stuffingDensity = StrictMath.max(stuffingDensity, japaneseNlpService.wordDensity(nlpSource, nd));
                }
                String resolved = entityNormalizer.resolve(rawName, mainBrand, competitorHosts, isProPlan);
                SomRawMetrics rawMetrics = metrics.toRawMetrics(plan, si, responseTokenLength, nounCount, stuffingDensity, 0.3);
                parsedLines.add(new BatchParsedLine(queryId, consultantOutputData, rawMetrics, resolved));
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
        double lAvg = parsedLines.stream()
                .mapToInt(l -> l.rawMetrics().responseTokenLength())
                .average()
                .orElse(0.0);
        List<SomRawMetrics> metricsList =
                parsedLines.stream().map(BatchParsedLine::rawMetrics).toList();
        long plannedQueries = batchPersistence.countQueriesByJobId(jobEntity.getId());
        var gbvsList = informationTheoryBasedAggregator.finalizeGbvsBatchForJob(metricsList, lAvg, plannedQueries);
        for (int idx = 0; idx < parsedLines.size(); idx++) {
            var line = parsedLines.get(idx);
            var gbvs = gbvsList.get(idx);
            var sourceText = line.consultantOutputData().response() != null && !line.consultantOutputData().response().isBlank()
                ? line.consultantOutputData().response()
                : "";
            var boostedSomScore = japaneseNlpService.applyIntensifierBoost(sourceText, gbvs.scorePercent());
            var somScore = StrictMath.max(0.0, StrictMath.min(100.0, boostedSomScore));
            var m = line.rawMetrics();
            boolean brand = m.isSemanticallyMentioned();
            int overall = (int) StrictMath.round(StrictMath.max(0.0, StrictMath.min(100.0, somScore)));
                batchPersistence.findQueryById(line.queryId()).ifPresent(queryEntity -> {
                    UUID wid = Objects.requireNonNullElse(jobEntity.getWorkspaceId(), DefaultTenantIds.WORKSPACE_ID);
                    UUID pid = Objects.requireNonNull(jobEntity.getProjectId());
                    var negAlert = m.sentimentIntensity() < -0.5;
                    var insight = strategyInsightService.fromModifiedZ(gbvs.modifiedZScore());
                    batchPersistence.upsertAuditHistory(
                        jobEntity.getId(), wid, pid,
                        line.queryId(),
                        queryEntity.getQueryText(),
                        jsonbOperations.serialize(line.consultantOutputData()),
                        somScore, gbvs.scorePercent(), brand,
                        m.aiCitationPosition(), overall,
                        line.resolved(), m.tokenCount(),
                        m.aiCitationPosition(), m.sentimentIntensity(),
                        gbvs.visibilityStage(),
                        GeoVisibilityCalculatorService.CALCULATION_VERSION,
                        gbvs.modifiedZScore(), negAlert,
                        insight.diagnosticMessage(),
                        new ArrayList<>(insight.recommendedActions()),
                        null,
                        List.of());
                    if (quotaSettled.add(line.queryId())) {
                        long textLen = (long) queryEntity.getQueryText().length()
                            + (mainBrand != null ? mainBrand.length() : 0);
                        long actual = QuotaCreditCalculator.actualCreditsGeminiBatchLine(textLen);
                        long refund = QuotaCreditCalculator.refundAfterDeposit(
                            QuotaCreditCalculator.DEPOSIT_PER_KEYWORD, actual);
                        if (refund > 0L) {
                            planBasedQuotaManager.addTokens(tid, refund);
                        }
                    }
                });
        }
        for (var qe : batchPersistence.findQueriesByJobId(jobEntity.getId())) {
            if (!quotaSettled.contains(qe.getId())) {
                planBasedQuotaManager.addTokens(
                    tid,
                    QuotaCreditCalculator.refundAfterDeposit(QuotaCreditCalculator.DEPOSIT_PER_KEYWORD, 1L));
            }
        }
        var auditsAfter = batchPersistence.findResultsByJobId(jobEntity.getId());
        var rollup = strategyInsightService.rollupJob(auditsAfter);
        batchPersistence.updateJobStrategyRollup(
            jobEntity.getId(),
            rollup.diagnosticMessage(),
            List.copyOf(rollup.recommendedActions()));
        gapAnalysisService.scheduleForJob(jobEntity.getId());
    }
    private record BatchParsedLine(
        UUID queryId,
        ConsultantOutputData consultantOutputData,
        SomRawMetrics rawMetrics,
        String resolved) {
    }
    private List<String> loadCompetitorHosts(JobEntity jobEntity) {
        UUID projectId = jobEntity.getProjectId();
        if (projectId == null) {
            return List.of();
        }
        return batchPersistence.findCompetitorUrlsByProjectId(projectId)
            .stream()
            .map(EntityNormalizer::hostLabelFromUrl)
            .filter(s -> !s.isBlank())
            .toList();
    }
    private void markBatchQuotaRefundOnError(UUID tid, String key, Set<UUID> quotaSettled) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            UUID qid = UUID.fromString(key);
            if (quotaSettled.add(qid)) {
                planBasedQuotaManager.addTokens(
                    tid,
                    QuotaCreditCalculator.refundAfterDeposit(QuotaCreditCalculator.DEPOSIT_PER_KEYWORD, 1L));
            }
        } catch (IllegalArgumentException ignored) {
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
