package com.geo.analytics.domain.semantic;

import dev.langchain4j.service.UserMessage;
import java.util.Objects;

public final class SemanticJudgmentEngine {

    public record AiBrandMentionResult(boolean brandMentioned, double confidenceScore) {
    }

    public sealed interface SemanticJudgmentOutcome permits SemanticJudgmentOutcome.Success, SemanticJudgmentOutcome.Failure {

        record Success(AiBrandMentionResult result) implements SemanticJudgmentOutcome {
        }

        record Failure(String reason) implements SemanticJudgmentOutcome {
        }
    }

    public interface SemanticJudgmentAi {

        AiBrandMentionResult judge(@UserMessage String normalizedText);
    }

    private final SemanticJudgmentAi semanticJudgmentAi;

    public SemanticJudgmentEngine(SemanticJudgmentAi semanticJudgmentAi) {
        this.semanticJudgmentAi = Objects.requireNonNull(semanticJudgmentAi);
    }

    public SemanticJudgmentOutcome evaluate(String normalizedText) {
        try {
            AiBrandMentionResult result = semanticJudgmentAi.judge(normalizedText);
            if (result == null) {
                return new SemanticJudgmentOutcome.Failure("null");
            }
            double score = result.confidenceScore();
            if (Double.isNaN(score) || Double.isInfinite(score) || score < 0.0 || score > 1.0) {
                return new SemanticJudgmentOutcome.Failure("confidenceScore out of range");
            }
            return new SemanticJudgmentOutcome.Success(result);
        } catch (Exception exception) {
            String message = exception.getMessage();
            if (message == null || message.isEmpty()) {
                message = exception.getClass().getName();
            } else {
                message = exception.getClass().getName() + ":" + message;
            }
            return new SemanticJudgmentOutcome.Failure(message);
        }
    }
}
