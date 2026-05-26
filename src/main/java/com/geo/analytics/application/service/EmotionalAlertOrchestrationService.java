package com.geo.analytics.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.geo.analytics.application.dto.EmotionalAlertFacts;
import com.geo.analytics.application.dto.EmotionalAlertSynthesis;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.ProjectEntity;
import com.geo.analytics.domain.enums.EmotionalAlertLevel;
import com.geo.analytics.domain.enums.IndustryType;
import com.geo.analytics.domain.service.EmotionalAlertLevelRule;
import com.geo.analytics.infrastructure.ai.GeminiEmotionalAlertAdapter;
import com.geo.analytics.infrastructure.repository.ProjectRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EmotionalAlertOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(EmotionalAlertOrchestrationService.class);
    private static final String FALLBACK_MESSAGE_JA =
            "ひとことアドバイスを自動生成できませんでした。表示されているスコアと改善項目をご確認ください。";

    private final JobPersistenceService jobPersistenceService;
    private final JobAnalysisBenchmarkAssembler jobAnalysisBenchmarkAssembler;
    private final ProjectRepository projectRepository;
    private final GeminiEmotionalAlertAdapter geminiEmotionalAlertAdapter;
    private final ObjectMapper objectMapper;

    public EmotionalAlertOrchestrationService(
            JobPersistenceService jobPersistenceService,
            JobAnalysisBenchmarkAssembler jobAnalysisBenchmarkAssembler,
            ProjectRepository projectRepository,
            GeminiEmotionalAlertAdapter geminiEmotionalAlertAdapter,
            ObjectMapper objectMapper) {
        this.jobPersistenceService = jobPersistenceService;
        this.jobAnalysisBenchmarkAssembler = jobAnalysisBenchmarkAssembler;
        this.projectRepository = projectRepository;
        this.geminiEmotionalAlertAdapter = geminiEmotionalAlertAdapter;
        this.objectMapper = objectMapper;
    }

    public void materializeAndPersist(UUID jobId) {
        if (jobId == null) {
            return;
        }
        try {
            JobEntity job = jobPersistenceService.findJobById(jobId);
            JobAnalysisBenchmarkAssembler.BenchmarkAttach bench = jobAnalysisBenchmarkAssembler.attach(job);
            Double factScore = bench.factBasedScore();
            if (factScore == null || Double.isNaN(factScore.doubleValue())) {
                return;
            }
            UUID projectId = job.getProjectId();
            ProjectEntity project =
                    projectId != null ? projectRepository.findById(projectId).orElse(null) : null;
            IndustryType industry =
                    project != null && project.getIndustryType() != null
                            ? project.getIndustryType()
                            : IndustryType.OTHER;
            String brand = job.getBrandName() != null ? job.getBrandName() : "";
            EmotionalAlertLevel level = EmotionalAlertLevelRule.classify(factScore);
            EmotionalAlertFacts facts =
                    new EmotionalAlertFacts(level, factScore, bench.rubricGaps(), industry, brand);
            String message;
            boolean usedFallback;
            if (projectId == null) {
                message = FALLBACK_MESSAGE_JA;
                usedFallback = true;
            } else {
                try {
                    message = geminiEmotionalAlertAdapter.synthesizeMessage(projectId, facts);
                    usedFallback = false;
                } catch (Throwable throwable) {
                    log.warn("emotional alert llm skipped jobId={}", jobId, throwable);
                    message = FALLBACK_MESSAGE_JA;
                    usedFallback = true;
                }
            }
            EmotionalAlertSynthesis synthesis = new EmotionalAlertSynthesis(level, message, usedFallback);
            String json = toPersistedJson(synthesis);
            jobPersistenceService.saveJobEmotionalAlert(jobId, json);
        } catch (Throwable throwable) {
            log.warn("emotional alert persist skipped jobId={}", jobId, throwable);
        }
    }

    private String toPersistedJson(EmotionalAlertSynthesis synthesis) throws Exception {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("level", synthesis.level().name());
        node.put("message", synthesis.message());
        node.put("used_fallback", synthesis.usedFallback());
        return objectMapper.writeValueAsString(node);
    }
}
