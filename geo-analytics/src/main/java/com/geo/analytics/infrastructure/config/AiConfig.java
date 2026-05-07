package com.geo.analytics.infrastructure.config;

import com.geo.analytics.application.port.AiVerificationPort;
import com.geo.analytics.application.port.ModelTypedAiVerificationPort;
import com.geo.analytics.application.service.AiVerificationRouter;
import com.geo.analytics.application.service.JobPersistenceService;
import com.geo.analytics.application.service.JobStreamRegistryService;
import com.geo.analytics.application.service.SomScoreParser;
import com.geo.analytics.domain.enums.ModelType;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.service.EntityNormalizer;
import com.geo.analytics.domain.service.GeoVisibilityCalculatorService;
import com.geo.analytics.domain.service.InformationTheoryBasedAggregator;
import com.geo.analytics.domain.service.JapaneseNlpService;
import com.geo.analytics.domain.service.DomainAnalysisAiModelNames;
import com.geo.analytics.infrastructure.ai.ConsultantOutputSchema;
import com.geo.analytics.infrastructure.ai.DebateDirectorOutputSchema;
import com.geo.analytics.infrastructure.ai.DomainAnalysisOutputSchema;
import com.geo.analytics.infrastructure.ai.CompetitorFilterOutputSchema;
import com.geo.analytics.infrastructure.ai.GeoOnboardingOutputSchema;
import com.geo.analytics.infrastructure.ai.RemediationTaskOutputSchema;
import com.geo.analytics.infrastructure.ai.RubricAuditOutputSchema;
import com.geo.analytics.infrastructure.ai.TargetAttributesOutputSchema;
import com.geo.analytics.infrastructure.ai.TaskToneContentSchema;
import com.geo.analytics.infrastructure.ai.DeepSeekAdapter;
import com.geo.analytics.infrastructure.ai.ForwardingModelAdapter;
import com.geo.analytics.infrastructure.ai.GeminiVerificationAdapter;
import com.geo.analytics.infrastructure.ai.LlmModelNames;
import com.geo.analytics.infrastructure.ai.StrictSchemaValidator;
import com.geo.analytics.infrastructure.api.SerpApiAdapter;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Semaphore;

@Configuration
public class AiConfig {
    public static final String AI_VERIFICATION_CONCURRENCY_LIMITER = "aiVerificationConcurrencyLimiter";
    public static final String GEMINI_STREAMING_STANDARD = "geminiStreamingStandard";
    public static final String GEMINI_STREAMING_PRO = "geminiStreamingPro";
    public static final String GEMINI_KEYWORD_SUGGESTION_CHAT_MODEL = "geminiKeywordSuggestionChatModel";
    public static final String GEMINI_GBVS_CHAT = "geminiGbvsChat";
    public static final String GEMINI_GEO_ONBOARDING_CHAT_MODEL = "geminiGeoOnboardingChatModel";
    public static final String GEMINI_TARGET_ATTRIBUTES_MODEL = "geminiTargetAttributesModel";
    public static final String GEMINI_COMPETITOR_FILTER_MODEL = "geminiCompetitorFilterModel";
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
    @Qualifier(GEMINI_KEYWORD_SUGGESTION_CHAT_MODEL)
    public ChatLanguageModel keywordSuggestionChatLanguageModel() {
        String configuredGeminiModelName = appProperties.getAi().getGemini().getModelName();
        var resolvedModel = configuredGeminiModelName == null || configuredGeminiModelName.isBlank()
            ? LlmModelNames.GEMINI_31_PRO
            : configuredGeminiModelName;
        return GoogleAiGeminiChatModel.builder()
            .apiKey(appProperties.getAi().getGemini().getApiKey())
            .modelName(resolvedModel)
            .temperature(0.2)
            .timeout(Duration.ofSeconds(180))
            .maxOutputTokens(8192)
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

    @Bean
    @Qualifier(GEMINI_COMPETITOR_FILTER_MODEL)
    public ChatLanguageModel geminiCompetitorFilterModel() {
        return GoogleAiGeminiChatModel.builder()
                .apiKey(appProperties.getAi().getGemini().getApiKey())
                .modelName(LlmModelNames.GEMINI_25_FLASH)
                .temperature(0.2)
                .timeout(Duration.ofSeconds(120))
                .maxOutputTokens(4096)
                .responseFormat(CompetitorFilterOutputSchema.competitorFilterResponseFormat())
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
                .maxOutputTokens(4096)
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

    @Bean
    @Qualifier(GEMINI_STREAMING_STANDARD)
    public GoogleAiGeminiStreamingChatModel geminiStreamingStandard() {
        return GoogleAiGeminiStreamingChatModel.builder()
            .apiKey(appProperties.getAi().getGemini().getApiKey())
            .modelName(LlmModelNames.GEMINI_31_PRO)
            .temperature(0.0)
            .timeout(Duration.ofSeconds(300))
            .responseFormat(ConsultantOutputSchema.responseFormat(SubscriptionPlan.STANDARD))
            .build();
    }

    @Bean
    @Qualifier(GEMINI_STREAMING_PRO)
    public GoogleAiGeminiStreamingChatModel geminiStreamingPro() {
        return GoogleAiGeminiStreamingChatModel.builder()
            .apiKey(appProperties.getAi().getGemini().getApiKey())
            .modelName(LlmModelNames.GEMINI_31_PRO)
            .temperature(0.0)
            .timeout(Duration.ofSeconds(300))
            .responseFormat(ConsultantOutputSchema.responseFormat(SubscriptionPlan.PRO))
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
            JobStreamRegistryService jobStreamRegistryService,
            @Qualifier(GEMINI_STREAMING_STANDARD) GoogleAiGeminiStreamingChatModel geminiStreamingStandard,
            @Qualifier(GEMINI_STREAMING_PRO) GoogleAiGeminiStreamingChatModel geminiStreamingPro,
            EntityNormalizer entityNormalizer,
            JapaneseNlpService japaneseNlpService,
            DeepSeekAdapter deepSeekAdapter,
            StrictSchemaValidator strictSchemaValidator,
            GeoVisibilityCalculatorService geoVisibilityCalculatorService,
            JobPersistenceService jobPersistenceService,
            SerpApiAdapter serpApiAdapter) {
        return new GeminiVerificationAdapter(
                geminiGbvsChatModel,
                somScoreParser,
                jobStreamRegistryService,
                geminiStreamingStandard,
                geminiStreamingPro,
                entityNormalizer,
                japaneseNlpService,
                deepSeekAdapter,
                strictSchemaValidator,
                geoVisibilityCalculatorService,
                jobPersistenceService,
                serpApiAdapter);
    }

    @Bean
    public AiVerificationPort aiVerificationPort(
            GeminiVerificationAdapter geminiVerificationAdapter,
            InformationTheoryBasedAggregator informationTheoryBasedAggregator,
            @Qualifier(AI_VERIFICATION_CONCURRENCY_LIMITER) Semaphore aiVerificationConcurrencyLimiter) {
        List<ModelTypedAiVerificationPort> adapters = List.of(
                geminiVerificationAdapter,
                new ForwardingModelAdapter(ModelType.CHATGPT, geminiVerificationAdapter),
                new ForwardingModelAdapter(ModelType.CLAUDE, geminiVerificationAdapter));
        return new AiVerificationRouter(adapters, informationTheoryBasedAggregator, aiVerificationConcurrencyLimiter);
    }
}
