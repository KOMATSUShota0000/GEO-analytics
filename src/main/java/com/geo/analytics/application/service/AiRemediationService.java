package com.geo.analytics.application.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.credit.CreditReservation;
import com.geo.analytics.domain.entity.AuditHistoryEntity;
import com.geo.analytics.domain.entity.AuditRubricResultEntity;
import com.geo.analytics.domain.enums.RubricVerdictStatus;
import com.geo.analytics.domain.enums.TaskCategory;
import com.geo.analytics.domain.enums.TaskPriority;
import com.geo.analytics.domain.model.RemediationTask;
import com.geo.analytics.infrastructure.ai.RemediationTaskPrompts;
import com.geo.analytics.infrastructure.config.AiConfig;
import com.geo.analytics.infrastructure.repository.AuditHistoryRepository;
import com.geo.analytics.infrastructure.repository.AuditRubricResultRepository;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import jakarta.persistence.EntityNotFoundException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiRemediationService {

    private static final Logger log = LoggerFactory.getLogger(AiRemediationService.class);
    private static final int STACK_TRACE_LIMIT = 20_000;
    private static final long REMEDIATION_CREDIT = 200L;

    private final AiRemediationService self;
    private final ChatLanguageModel remediationChatModel;
    private final ObjectMapper objectMapper;
    private final AuditRubricResultRepository auditRubricResultRepository;
    private final AuditHistoryRepository auditHistoryRepository;

    public AiRemediationService(
            @Lazy AiRemediationService self,
            @Qualifier(AiConfig.GEMINI_REMEDIATION_TASKS) ChatLanguageModel remediationChatModel,
            ObjectMapper objectMapper,
            AuditRubricResultRepository auditRubricResultRepository,
            AuditHistoryRepository auditHistoryRepository) {
        this.self = self;
        this.remediationChatModel = remediationChatModel;
        this.objectMapper = objectMapper;
        this.auditRubricResultRepository = auditRubricResultRepository;
        this.auditHistoryRepository = auditHistoryRepository;
    }

    public List<RemediationTask> generateTasks(
            UUID projectId, UUID auditHistoryId, List<String> gapCriterionIds) {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId");
        }
        if (auditHistoryId == null) {
            throw new IllegalArgumentException("auditHistoryId");
        }
        if (gapCriterionIds == null || gapCriterionIds.isEmpty()) {
            return List.of();
        }
        List<RemediationTaskPrompts.GapContext> contexts = buildGapContexts(auditHistoryId, gapCriterionIds);
        if (contexts.isEmpty()) {
            return List.of();
        }
        List<RemediationTask> tasks = self.invokeLlmWithCreditReservation(projectId, contexts);
        try {
            self.persistTasks(auditHistoryId, tasks);
        } catch (RuntimeException runtimeException) {
            log.error(
                    "ai_remediation_persist_failed projectId={} auditHistoryId={} trace={}",
                    projectId,
                    auditHistoryId,
                    truncateStackTrace(runtimeException));
        }
        return tasks;
    }

    @CreditReservation(amount = REMEDIATION_CREDIT, settleNote = "ai_remediation_tasks")
    public List<RemediationTask> invokeLlmWithCreditReservation(
            UUID projectId, List<RemediationTaskPrompts.GapContext> contexts) {
        try {
            String rawJson = remediationChatModel
                    .chat(ChatRequest.builder()
                            .messages(SystemMessage.from(RemediationTaskPrompts.systemPrompt(contexts)))
                            .build())
                    .aiMessage()
                    .text();
            ParsedRemediationOutput parsed = objectMapper.readValue(rawJson, ParsedRemediationOutput.class);
            return mapToDomain(parsed);
        } catch (RuntimeException runtimeException) {
            log.error(
                    "ai_remediation_invoke_failed projectId={} trace={}",
                    projectId,
                    truncateStackTrace(runtimeException));
            throw runtimeException;
        } catch (Exception exception) {
            log.error(
                    "ai_remediation_invoke_failed projectId={} trace={}",
                    projectId,
                    truncateStackTrace(exception));
            throw new IllegalStateException(exception);
        }
    }

    @Transactional
    public void persistTasks(UUID auditHistoryId, List<RemediationTask> tasks) {
        if (auditHistoryId == null || tasks == null || tasks.isEmpty()) {
            return;
        }
        AuditHistoryEntity history = auditHistoryRepository
                .findById(auditHistoryId)
                .orElseThrow(() -> new EntityNotFoundException("auditHistoryId"));
        try {
            String json = objectMapper.writeValueAsString(tasks);
            history.setJobRecommendedActionsJson(json);
            auditHistoryRepository.save(history);
        } catch (Exception exception) {
            log.error(
                    "ai_remediation_serialize_failed auditHistoryId={} trace={}",
                    auditHistoryId,
                    truncateStackTrace(exception));
            throw new IllegalStateException(exception);
        }
    }

    private List<RemediationTaskPrompts.GapContext> buildGapContexts(
            UUID auditHistoryId, List<String> gapCriterionIds) {
        List<AuditRubricResultEntity> rows;
        try {
            rows = auditRubricResultRepository.findByAuditHistoryId(auditHistoryId);
        } catch (RuntimeException runtimeException) {
            log.error(
                    "ai_remediation_load_failed auditHistoryId={} trace={}",
                    auditHistoryId,
                    truncateStackTrace(runtimeException));
            return List.of();
        }
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, AuditRubricResultEntity> selfByCriterion = new LinkedHashMap<>();
        LinkedHashMap<String, AuditRubricResultEntity> firstYesCompetitorByCriterion = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            AuditRubricResultEntity row = rows.get(i);
            String criterion = row.getCriterionId();
            if (criterion == null) {
                continue;
            }
            if (row.isSelf()) {
                selfByCriterion.putIfAbsent(criterion, row);
            } else if (RubricVerdictStatus.YES.name().equals(row.getVerdict())) {
                firstYesCompetitorByCriterion.putIfAbsent(criterion, row);
            }
        }
        ArrayList<RemediationTaskPrompts.GapContext> contexts = new ArrayList<>(gapCriterionIds.size());
        for (int i = 0; i < gapCriterionIds.size(); i++) {
            String criterion = gapCriterionIds.get(i);
            if (criterion == null || criterion.isBlank()) {
                continue;
            }
            AuditRubricResultEntity selfRow = selfByCriterion.get(criterion);
            AuditRubricResultEntity compRow = firstYesCompetitorByCriterion.get(criterion);
            if (selfRow == null) {
                continue;
            }
            contexts.add(new RemediationTaskPrompts.GapContext(
                    criterion,
                    selfRow.getVerdict(),
                    selfRow.getEvidence(),
                    compRow != null ? compRow.getEvidence() : ""));
        }
        return List.copyOf(contexts);
    }

    private static List<RemediationTask> mapToDomain(ParsedRemediationOutput parsed) {
        if (parsed == null || parsed.tasks() == null || parsed.tasks().isEmpty()) {
            return List.of();
        }
        ArrayList<RemediationTask> out = new ArrayList<>(parsed.tasks().size());
        for (int i = 0; i < parsed.tasks().size(); i++) {
            ParsedRemediationItem item = parsed.tasks().get(i);
            if (item == null) {
                continue;
            }
            TaskCategory category = parseCategory(item.category());
            TaskPriority priority = parsePriority(item.priority());
            String title = item.title() == null ? "" : item.title().trim();
            if (category == null || priority == null || title.isEmpty()) {
                continue;
            }
            String content = item.content() == null ? "" : item.content();
            double impact = clampImpact(item.impactScore());
            out.add(new RemediationTask(UUID.randomUUID(), category, priority, title, content, impact));
        }
        return List.copyOf(out);
    }

    private static double clampImpact(Double raw) {
        if (raw == null || Double.isNaN(raw) || Double.isInfinite(raw)) {
            return 0.0d;
        }
        return StrictMath.max(0.0d, StrictMath.min(1.0d, raw));
    }

    private static TaskCategory parseCategory(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return TaskCategory.valueOf(raw.trim());
        } catch (IllegalArgumentException illegalArgumentException) {
            return null;
        }
    }

    private static TaskPriority parsePriority(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return TaskPriority.valueOf(raw.trim());
        } catch (IllegalArgumentException illegalArgumentException) {
            return null;
        }
    }

    private static String truncateStackTrace(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        String full = stringWriter.toString();
        if (full.length() <= STACK_TRACE_LIMIT) {
            return full;
        }
        return full.substring(0, STACK_TRACE_LIMIT);
    }

    public record ParsedRemediationOutput(@JsonProperty("tasks") List<ParsedRemediationItem> tasks) {}

    public record ParsedRemediationItem(
            @JsonProperty("category") String category,
            @JsonProperty("priority") String priority,
            @JsonProperty("title") String title,
            @JsonProperty("content") String content,
            @JsonProperty("impactScore") Double impactScore) {}
}
