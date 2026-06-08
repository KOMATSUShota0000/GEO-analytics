package com.geo.analytics.infrastructure.config;

import com.geo.analytics.application.port.AiVerificationPort;
import com.geo.analytics.application.service.AiVerificationRouter;
import com.geo.analytics.application.service.JobPersistenceService;
import com.geo.analytics.application.service.SomScoreParser;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.service.EntityNormalizer;
import com.geo.analytics.domain.service.JapaneseNlpService;
import com.geo.analytics.domain.service.DomainAnalysisAiModelNames;
import com.geo.analytics.infrastructure.ai.ConsultantOutputSchema;
import com.geo.analytics.infrastructure.ai.DebateDirectorOutputSchema;
import com.geo.analytics.infrastructure.ai.DomainAnalysisOutputSchema;
import com.geo.analytics.infrastructure.ai.GeoOnboardingOutputSchema;
import com.geo.analytics.infrastructure.ai.RemediationTaskOutputSchema;
import com.geo.analytics.infrastructure.ai.RubricAuditOutputSchema;
import com.geo.analytics.infrastructure.ai.TargetAttributesOutputSchema;
import com.geo.analytics.infrastructure.ai.TaskToneContentSchema;
import com.geo.analytics.infrastructure.ai.GeminiVerificationAdapter;
import com.geo.analytics.infrastructure.ai.LlmModelNames;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.Semaphore;

@Configuration
public class AiConfig {
    public static final String AI_VERIFICATION_CONCURRENCY_LIMITER = "aiVerificationConcurrencyLimiter";
    public static final String GEMINI_GBVS_CHAT = "geminiGbvsChat";
    public static final String GEMINI_GEO_ONBOARDING_CHAT_MODEL = "geminiGeoOnboardingChatModel";
    public static final String GEMINI_TARGET_ATTRIBUTES_MODEL = "geminiTargetAttributesModel";
    public static final String GEMINI_DEBATE_ANALYST = "geminiDebateAnalyst";
    public static final String GEMINI_DEBATE_INNOVATOR = "geminiDebateInnovator";
    public static final String GEMINI_DEBATE_SKEPTIC = "geminiDebateSkeptic";
    public static final String GEMINI_DEBATE_DIRECTOR = "geminiDebateDirector";
    /** 検閲専用 Flash（プロンプトインジェクション・ガード）。 */
    public static final String GEMINI_PROMPT_INJECTION_GUARD = "geminiPromptInjectionGuardModel";
    public static final String GEMINI_RUBRIC_AUDIT = "geminiRubricAuditModel";
    public static final String GEMINI_REMEDIATION_TASKS = "geminiRemediationTasksModel";
    public static final String GEMINI_TASK_TONE_REGENERATION = "geminiTaskToneRegenerationModel";
    public static final String GEMINI_25_FLASH = "gemini25Flash";

    private final AppProperties appProperties;

    public AiConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Bean
    @Qualifier(GEMINI_GBVS_CHAT)
    public ChatLanguageModel geminiGbvsChatModel() {
        return GoogleAiGeminiChatModel.builder()
            .apiKey(appProperties.getAi().getGemini().getApiKey())
            .modelName(LlmModelNames.GEMINI_31_PRO)
            .temperature(0.0)
            .timeout(Duration.ofSeconds(300))
            .build();
    }

    @Bean
    @Qualifier(GEMINI_GEO_ONBOARDING_CHAT_MODEL)
    public ChatLanguageModel geminiGeoOnboardingChatModel() {
        return GoogleAiGeminiChatModel.builder()
            .apiKey(appProperties.getAi().getGemini().getApiKey())
            .modelName(LlmModelNames.GEMINI_25_FLASH)
            .temperature(0.2)
            .timeout(Duration.ofSeconds(120))
            .maxOutputTokens(4096)
            .responseFormat(GeoOnboardingOutputSchema.geoOnboardingResponseFormat())
            .build();
    }

    @Bean
    @Qualifier(GEMINI_TARGET_ATTRIBUTES_MODEL)
    public ChatLanguageModel geminiTargetAttributesModel() {
        return GoogleAiGeminiChatModel.builder()
                .apiKey(appProperties.getAi().getGemini().getApiKey())
                .modelName(LlmModelNames.GEMINI_25_FLASH)
                .temperature(0.2)
                .timeout(Duration.ofSeconds(120))
                .maxOutputTokens(4096)
                .responseFormat(TargetAttributesOutputSchema.targetAttributesResponseFormat())
                .build();
    }

    @Bean(DomainAnalysisAiModelNames.GEMINI_DOMAIN_ANALYSIS_CHAT_MODEL)
    public ChatLanguageModel geminiDomainAnalysisChatModel() {
        return GoogleAiGeminiChatModel.builder()
                .apiKey(appProperties.getAi().getGemini().getApiKey())
                .modelName(LlmModelNames.GEMINI_25_FLASH)
                .temperature(0.2)
                .timeout(Duration.ofSeconds(120))
                .maxOutputTokens(8192)
                .responseFormat(DomainAnalysisOutputSchema.domainAnalysisResponseFormat())
                .build();
    }

    @Bean
    @Qualifier(GEMINI_DEBATE_ANALYST)
    public ChatLanguageModel geminiDebateAnalyst() {
        return GoogleAiGeminiChatModel.builder()
            .apiKey(appProperties.getAi().getGemini().getApiKey())
            .modelName(LlmModelNames.GEMINI_25_FLASH)
            .temperature(0.1)
            .timeout(Duration.ofSeconds(120))
            .maxOutputTokens(8192)
            .build();
    }

    @Bean
    @Qualifier(GEMINI_DEBATE_INNOVATOR)
    public ChatLanguageModel geminiDebateInnovator() {
        return GoogleAiGeminiChatModel.builder()
            .apiKey(appProperties.getAi().getGemini().getApiKey())
            .modelName(LlmModelNames.GEMINI_25_FLASH)
            .temperature(0.8)
            .timeout(Duration.ofSeconds(120))
            .maxOutputTokens(8192)
            .build();
    }

    @Bean
    @Qualifier(GEMINI_DEBATE_SKEPTIC)
    public ChatLanguageModel geminiDebateSkeptic() {
        return GoogleAiGeminiChatModel.builder()
            .apiKey(appProperties.getAi().getGemini().getApiKey())
            .modelName(LlmModelNames.GEMINI_25_FLASH)
            .temperature(0.4)
            .timeout(Duration.ofSeconds(120))
            .maxOutputTokens(8192)
            .build();
    }

    @Bean
    @Qualifier(GEMINI_DEBATE_DIRECTOR)
    public ChatLanguageModel geminiDebateDirector() {
        return GoogleAiGeminiChatModel.builder()
                .apiKey(appProperties.getAi().getGemini().getApiKey())
                .modelName(LlmModelNames.GEMINI_25_PRO)
                .temperature(0.2)
                .timeout(Duration.ofSeconds(180))
                .maxOutputTokens(8192)
                .responseFormat(DebateDirectorOutputSchema.debateDirectorResponseFormat())
                .build();
    }

    @Bean
    @Qualifier(GEMINI_RUBRIC_AUDIT)
    public ChatLanguageModel geminiRubricAuditModel() {
        return GoogleAiGeminiChatModel.builder()
                .apiKey(appProperties.getAi().getGemini().getApiKey())
                .modelName(LlmModelNames.GEMINI_25_FLASH)
                .temperature(0.2)
                .timeout(Duration.ofSeconds(120))
                // ルーブリックは10項目＋日本語evidenceを返すため4096では4項目目で
                // 出力が打ち切られJSONが破損する。他の構造化出力モデルと同じ8192に統一。
                .maxOutputTokens(8192)
                .responseFormat(RubricAuditOutputSchema.rubricAuditResponseFormat())
                .build();
    }

    @Bean
    @Qualifier(GEMINI_REMEDIATION_TASKS)
    public ChatLanguageModel geminiRemediationTasksModel() {
        return GoogleAiGeminiChatModel.builder()
                .apiKey(appProperties.getAi().getGemini().getApiKey())
                .modelName(LlmModelNames.GEMINI_25_PRO)
                .temperature(0.3)
                .timeout(Duration.ofSeconds(180))
                .maxOutputTokens(8192)
                .responseFormat(RemediationTaskOutputSchema.remediationResponseFormat())
                .build();
    }

    @Bean
    @Qualifier(GEMINI_TASK_TONE_REGENERATION)
    public ChatLanguageModel geminiTaskToneRegenerationModel() {
        return GoogleAiGeminiChatModel.builder()
                .apiKey(appProperties.getAi().getGemini().getApiKey())
                .modelName(LlmModelNames.GEMINI_25_FLASH)
                .temperature(0.3)
                .timeout(Duration.ofSeconds(120))
                .maxOutputTokens(8192)
                .responseFormat(TaskToneContentSchema.taskToneContentResponseFormat())
                .build();
    }

    @Bean
    @Qualifier(GEMINI_25_FLASH)
    public ChatLanguageModel gemini25FlashChatModel() {
        return GoogleAiGeminiChatModel.builder()
                .apiKey(appProperties.getAi().getGemini().getApiKey())
                .modelName(LlmModelNames.GEMINI_25_FLASH)
                .temperature(0.2)
                .timeout(Duration.ofSeconds(120))
                .maxOutputTokens(4096)
                .build();
    }

    @Bean
    @Qualifier(GEMINI_PROMPT_INJECTION_GUARD)
    public ChatLanguageModel geminiPromptInjectionGuardModel() {
        int sec = appProperties.getAi().getPromptInjectionGuard().getTimeoutSeconds();
        return GoogleAiGeminiChatModel.builder()
                .apiKey(appProperties.getAi().getGemini().getApiKey())
                .modelName(LlmModelNames.GEMINI_25_FLASH)
                .temperature(0.0)
                .timeout(Duration.ofSeconds(sec))
                .maxOutputTokens(10)
                .build();
    }

    @Bean(AI_VERIFICATION_CONCURRENCY_LIMITER)
    public Semaphore aiVerificationConcurrencyLimiter() {
        return new Semaphore(15);
    }

    @Bean
    public GeminiVerificationAdapter geminiVerificationAdapter(
            @Qualifier(GEMINI_GBVS_CHAT) ChatLanguageModel geminiGbvsChatModel,
            SomScoreParser somScoreParser,
            EntityNormalizer entityNormalizer,
            JapaneseNlpService japaneseNlpService,
            JobPersistenceService jobPersistenceService) {
        return new GeminiVerificationAdapter(
                geminiGbvsChatModel,
                somScoreParser,
                entityNormalizer,
                japaneseNlpService,
                jobPersistenceService);
    }

    @Bean
    public AiVerificationPort aiVerificationPort(
            GeminiVerificationAdapter geminiVerificationAdapter,
            @Qualifier(AI_VERIFICATION_CONCURRENCY_LIMITER) Semaphore aiVerificationConcurrencyLimiter) {
        return new AiVerificationRouter(geminiVerificationAdapter, aiVerificationConcurrencyLimiter);
    }
}
