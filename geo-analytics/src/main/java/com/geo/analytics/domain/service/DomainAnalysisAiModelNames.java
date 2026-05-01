package com.geo.analytics.domain.service;

/**
 * LangChain4j {@link DomainAnalysisAiService} を Spring に宣言的登録する際の Bean 名（chatModel）を集約する。
 */
public final class DomainAnalysisAiModelNames {

    /** {@link org.springframework.context.annotation.Bean} 名および {@code @AiService(chatModel = ...)} と一致させる。 */
    public static final String GEMINI_DOMAIN_ANALYSIS_CHAT_MODEL = "geminiDomainAnalysisChatModel";

    private DomainAnalysisAiModelNames() {}
}
