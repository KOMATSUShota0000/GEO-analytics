package com.geo.analytics.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.credit.CreditReservation;
import com.geo.analytics.domain.entity.AuditHistoryEntity;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.exception.TaskLockedException;
import com.geo.analytics.domain.enums.TaskPriority;
import com.geo.analytics.domain.model.RemediationPriorityLevel;
import com.geo.analytics.domain.model.RemediationTask;
import com.geo.analytics.domain.model.Tone;
import com.geo.analytics.infrastructure.ai.TaskTonePrompts;
import com.geo.analytics.infrastructure.config.AiConfig;
import com.geo.analytics.infrastructure.repository.AuditHistoryRepository;
import com.geo.analytics.infrastructure.tenant.TenantPlanScope;
import com.geo.analytics.web.dto.RemediationTaskResponse;
import com.geo.analytics.web.dto.TaskToneRegenerateResponse;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskRegenerationService {

    private static final TypeReference<List<RemediationTask>> REMEDIATION_LIST_TYPE =
            new TypeReference<>() {};

    private final TaskRegenerationService self;
    private final TaskRegenerationRateLimiter taskRegenerationRateLimiter;
    private final JobPersistenceService jobPersistenceService;
    private final JobAnalysisBenchmarkAssembler jobAnalysisBenchmarkAssembler;
    private final AuditHistoryRepository auditHistoryRepository;
    private final ChatLanguageModel taskToneChatModel;
    private final ObjectMapper objectMapper;

    public TaskRegenerationService(
            @Lazy TaskRegenerationService self,
            TaskRegenerationRateLimiter taskRegenerationRateLimiter,
            JobPersistenceService jobPersistenceService,
            JobAnalysisBenchmarkAssembler jobAnalysisBenchmarkAssembler,
            AuditHistoryRepository auditHistoryRepository,
            @Qualifier(AiConfig.GEMINI_TASK_TONE_REGENERATION) ChatLanguageModel taskToneChatModel,
            ObjectMapper objectMapper) {
        this.self = self;
        this.taskRegenerationRateLimiter = taskRegenerationRateLimiter;
        this.jobPersistenceService = jobPersistenceService;
        this.jobAnalysisBenchmarkAssembler = jobAnalysisBenchmarkAssembler;
        this.auditHistoryRepository = auditHistoryRepository;
        this.taskToneChatModel = taskToneChatModel;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TaskToneRegenerateResponse regenerate(UUID jobId, UUID taskId, Tone tone) {
        taskRegenerationRateLimiter.acquireOrThrow();
        JobEntity job = jobPersistenceService.findJobById(jobId);
        UUID projectId = Objects.requireNonNull(job.getProjectId(), "projectId");
        UUID workspaceId = job.getWorkspaceId();
        return TenantPlanScope.executeWithTenant(workspaceId, () -> doRegenerate(job, taskId, tone, projectId));
    }

    private TaskToneRegenerateResponse doRegenerate(JobEntity job, UUID taskId, Tone tone, UUID projectId) {
        UUID jobId = Objects.requireNonNull(job.getId());
        List<AuditHistoryEntity> audits = auditHistoryRepository.findByJobId(jobId);
        AuditHistoryEntity latest = pickLatest(audits);
        if (latest == null) {
            throw new EntityNotFoundException("remediation");
        }
        String json = latest.getJobRecommendedActionsJson();
        if (json == null || json.isBlank()) {
            throw new EntityNotFoundException("remediation");
        }
        List<RemediationTask> tasks;
        try {
            tasks = new ArrayList<>(objectMapper.readValue(json, REMEDIATION_LIST_TYPE));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        int index = -1;
        for (int i = 0; i < tasks.size(); i++) {
            RemediationTask row = tasks.get(i);
            if (row != null && Objects.equals(row.id(), taskId)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            throw new EntityNotFoundException("task");
        }
        RemediationTask old = tasks.get(index);
        Double factBasedScore = jobAnalysisBenchmarkAssembler.attach(job).factBasedScore();
        if (isTaskLockedFromScore(factBasedScore, old.priority())) {
            throw new TaskLockedException();
        }
        String newContent = self.invokeLlmWithCreditReservation(projectId, tone, old.title(), old.content());
        RemediationTask next = new RemediationTask(
                old.id(), old.category(), old.priority(), old.title(), newContent, old.impactScore());
        tasks.set(index, next);
        try {
            latest.setJobRecommendedActionsJson(objectMapper.writeValueAsString(tasks));
            auditHistoryRepository.save(latest);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        RemediationTaskResponse response = RemediationTaskResponse.from(next);
        if (response == null) {
            throw new IllegalStateException("response");
        }
        return new TaskToneRegenerateResponse(response);
    }

    private static boolean isTaskLockedFromScore(Double factBasedScore, TaskPriority priority) {
        Double threshold = RemediationPriorityLevel.forPriority(priority).requiredScoreThreshold();
        if (threshold != null && threshold <= 0.0) {
            return false;
        }
        if (factBasedScore == null || Double.isNaN(factBasedScore.doubleValue())) {
            return true;
        }
        if (threshold == null) {
            return true;
        }
        return factBasedScore.doubleValue() < threshold.doubleValue();
    }

    private static AuditHistoryEntity pickLatest(List<AuditHistoryEntity> audits) {
        if (audits == null || audits.isEmpty()) {
            return null;
        }
        return audits.stream()
                .filter(Objects::nonNull)
                .max(Comparator.comparing(
                                AuditHistoryEntity::getAuditDate, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(
                                AuditHistoryEntity::getCreatedAt,
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    @CreditReservation(amount = 10L, settleNote = "task_regenerate")
    public String invokeLlmWithCreditReservation(
            UUID projectId, Tone tone, String title, String currentContent) {
        String payload = taskToneChatModel
                .chat(ChatRequest.builder()
                        .messages(
                                SystemMessage.from(TaskTonePrompts.SYSTEM),
                                UserMessage.from(TaskTonePrompts.userMessage(tone, title, currentContent)))
                        .build())
                .aiMessage()
                .text();
        ToneContentPayload parsed;
        try {
            parsed = objectMapper.readValue(payload, ToneContentPayload.class);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        if (parsed.content() == null) {
            throw new IllegalStateException("content");
        }
        return parsed.content();
    }

    private record ToneContentPayload(String content) {}
}
