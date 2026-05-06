package com.geo.analytics.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.credit.CreditReservation;
import com.geo.analytics.application.dto.RubricAuditResult;
import com.geo.analytics.application.dto.RubricItemAudit;
import com.geo.analytics.domain.enums.RubricCriterionId;
import com.geo.analytics.infrastructure.ai.RubricAuditPrompts;
import com.geo.analytics.infrastructure.config.AiConfig;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
public class RubricAuditService {
    private final RubricAuditService self;
    private final ChatLanguageModel rubricAuditChatModel;
    private final ObjectMapper objectMapper;

    public RubricAuditService(
            @Lazy RubricAuditService self,
            @Qualifier(AiConfig.GEMINI_RUBRIC_AUDIT) ChatLanguageModel rubricAuditChatModel,
            ObjectMapper objectMapper) {
        this.self = self;
        this.rubricAuditChatModel = rubricAuditChatModel;
        this.objectMapper = objectMapper;
    }

    public RubricAuditResult executeAudit(UUID projectId, String websiteText) {
        if (websiteText == null) {
            throw new IllegalArgumentException("websiteText");
        }
        String trimmed = websiteText.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("websiteText");
        }
        return self.auditWithCreditReservation(projectId, trimmed);
    }

    @CreditReservation
    public RubricAuditResult auditWithCreditReservation(UUID projectId, String websiteText) {
        try {
            String rawJson =
                    rubricAuditChatModel.chat(ChatRequest.builder()
                                    .messages(SystemMessage.from(RubricAuditPrompts.systemPrompt(websiteText)))
                                    .build())
                            .aiMessage()
                            .text();
            RubricAuditResult parsed = objectMapper.readValue(rawJson, RubricAuditResult.class);
            validateTenDistinctCriteria(parsed);
            return parsed;
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void validateTenDistinctCriteria(RubricAuditResult parsed) {
        List<RubricItemAudit> items = parsed.items();
        List<RubricCriterionId> expected = RubricCriterionId.llmCriteria();
        if (items.size() != expected.size()) {
            throw new IllegalStateException("items");
        }
        EnumSet<RubricCriterionId> present = EnumSet.noneOf(RubricCriterionId.class);
        EnumSet<RubricCriterionId> required = EnumSet.copyOf(expected);
        for (RubricItemAudit item : items) {
            RubricCriterionId id = item.criterionId();
            if (id == null || !required.contains(id) || !present.add(id)) {
                throw new IllegalStateException("items");
            }
        }
        if (!present.equals(required)) {
            throw new IllegalStateException("items");
        }
    }
}
