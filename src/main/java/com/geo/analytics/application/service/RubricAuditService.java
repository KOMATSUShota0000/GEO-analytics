package com.geo.analytics.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.credit.CreditReservation;
import com.geo.analytics.application.dto.RubricAuditResult;
import com.geo.analytics.application.dto.RubricItemAudit;
import com.geo.analytics.domain.enums.RubricCriterionId;
import com.geo.analytics.infrastructure.ai.LlmWebsiteTextClip;
import com.geo.analytics.infrastructure.ai.RubricAuditPrompts;
import com.geo.analytics.infrastructure.config.AiConfig;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
public class RubricAuditService {
    private static final Logger log = LoggerFactory.getLogger(RubricAuditService.class);

    /**
     * LLM出力のトークン切れ・JSON破損に対するメソッド内リトライ回数。
     * {@code @CreditReservation} はメソッド単位のため、リトライしてもチケット消費は1回。
     */
    private static final int MAX_ATTEMPTS = 2;

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
        return executeAudit(projectId, websiteText, null);
    }

    public RubricAuditResult executeAudit(UUID projectId, String websiteText, String jobContextBlock) {
        if (websiteText == null) {
            throw new IllegalArgumentException("websiteText");
        }
        String trimmed = websiteText.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("websiteText");
        }
        String clipped = LlmWebsiteTextClip.clipWebsiteText(trimmed);
        return self.auditWithCreditReservation(projectId, clipped, jobContextBlock);
    }

    @CreditReservation
    public RubricAuditResult auditWithCreditReservation(
            UUID projectId, String websiteText, String jobContextBlock) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            // chat 成功後に確保し、parse 失敗時の診断ログに使う。chat 自体が落ちた場合は null。
            Object finishReason = null;
            try {
                var response = rubricAuditChatModel.chat(ChatRequest.builder()
                        .messages(
                                SystemMessage.from(RubricAuditPrompts.systemInstruction()),
                                UserMessage.from(
                                        RubricAuditPrompts.userPayload(websiteText, jobContextBlock)))
                        .build());
                finishReason = response.finishReason();
                String rawJson = response.aiMessage().text();
                RubricAuditResult parsed = objectMapper.readValue(rawJson, RubricAuditResult.class);
                validateTenDistinctCriteria(parsed);
                return parsed;
            } catch (Exception ex) {
                // トークン切れ（finishReason=MAX_TOKENS）等で出力JSONが破損するとここに来る。
                // finishReason は診断のため WARN に残し、最終試行でなければ1回だけ再試行する。
                lastFailure = (ex instanceof RuntimeException re) ? re : new IllegalStateException(ex);
                log.warn(
                        "rubric_audit_attempt_failed attempt={}/{} projectId={} finishReason={} reason={}",
                        attempt,
                        MAX_ATTEMPTS,
                        projectId,
                        finishReason,
                        ex.toString());
            }
        }
        throw lastFailure;
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
