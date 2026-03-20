package com.geo.analytics.infrastructure.ai;

import com.geo.analytics.application.dto.SomScoreData;
import com.geo.analytics.application.dto.VerificationRequest;
import com.geo.analytics.application.dto.VerificationResponse;
import com.geo.analytics.application.port.AiVerificationPort;
import com.geo.analytics.infrastructure.persistence.JsonbOperations;
import dev.langchain4j.model.chat.ChatLanguageModel;

public class GeminiVerificationAdapter implements AiVerificationPort {
    private final ChatLanguageModel chatLanguageModel;
    private final JsonbOperations jsonbOperations;

    public GeminiVerificationAdapter(ChatLanguageModel chatLanguageModel, JsonbOperations jsonbOperations) {
        this.chatLanguageModel = chatLanguageModel;
        this.jsonbOperations = jsonbOperations;
    }

    @Override
    public VerificationResponse verify(VerificationRequest verificationRequest) {
        String prompt = buildPrompt(verificationRequest);
        String rawAiResponseJson = chatLanguageModel.generate(prompt);
        SomScoreData parsedSomScoreData = jsonbOperations.deserialize(rawAiResponseJson, SomScoreData.class);
        return new VerificationResponse(
            rawAiResponseJson,
            parsedSomScoreData.confidenceScore() != null ? parsedSomScoreData.confidenceScore() : 0.0,
            parsedSomScoreData.brandMentioned() != null ? parsedSomScoreData.brandMentioned() : false,
            parsedSomScoreData.mentionRank()
        );
    }

    private String buildPrompt(VerificationRequest verificationRequest) {
        String brandName = verificationRequest.brandName();
        String userQuery = verificationRequest.query();
        String crawledContent = verificationRequest.crawledContent();
        if (crawledContent != null && !crawledContent.isBlank()) {
            String sourceUrl = verificationRequest.url() != null ? verificationRequest.url() : "";
            String hash = verificationRequest.contentHash() != null ? verificationRequest.contentHash() : "";
            return """
                You are an AI brand visibility analyzer.
                Brand under evaluation: %s
                User query: %s
                以下の抽出されたWebページテキストに基づき、ユーザーのクエリに対してブランドがどう言及されているか（あるいは欠落しているか）を評価せよ。
                Source URL: %s
                Content SHA-256: %s
                Extracted page text:
                %s
                Respond ONLY with valid JSON matching this exact schema with no additional text:
                {"response":"<natural language answer to the query>","brandMentioned":<true|false>,"mentionRank":<integer 1-10 if mentioned, null if not>,"confidenceScore":<float 0.0-1.0>}
                """.formatted(brandName, userQuery, sourceUrl, hash, crawledContent);
        }
        return """
            You are an AI brand visibility analyzer.
            Brand under evaluation: %s
            User query: %s
            Respond ONLY with valid JSON matching this exact schema with no additional text:
            {"response":"<natural language answer to the query>","brandMentioned":<true|false>,"mentionRank":<integer 1-10 if mentioned, null if not>,"confidenceScore":<float 0.0-1.0>}
            """.formatted(brandName, userQuery);
    }
}
