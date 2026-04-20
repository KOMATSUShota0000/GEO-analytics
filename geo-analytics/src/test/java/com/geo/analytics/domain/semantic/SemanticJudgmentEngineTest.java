package com.geo.analytics.domain.semantic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.geo.analytics.domain.semantic.SemanticJudgmentEngine.AiBrandMentionResult;
import com.geo.analytics.domain.semantic.SemanticJudgmentEngine.SemanticJudgmentAi;
import com.geo.analytics.domain.semantic.SemanticJudgmentEngine.SemanticJudgmentOutcome;
import org.junit.jupiter.api.Test;

class SemanticJudgmentEngineTest {

    @Test
    void shouldReturnSuccessWrappingAiResultWhenMentionedTrueAndScoreInRange() {
        SemanticJudgmentAi ai = mock(SemanticJudgmentAi.class);
        AiBrandMentionResult aiResult = new AiBrandMentionResult(true, 0.85);
        when(ai.judge(anyString())).thenReturn(aiResult);
        SemanticJudgmentEngine engine = new SemanticJudgmentEngine(ai);
        SemanticJudgmentOutcome outcome = engine.evaluate("\u30ad\u30e4\u30ce\u30f3");
        assertThat(outcome).isInstanceOf(SemanticJudgmentOutcome.Success.class);
        AiBrandMentionResult bound =
                switch (outcome) {
                    case SemanticJudgmentOutcome.Success success -> success.result();
                    case SemanticJudgmentOutcome.Failure failure -> throw new AssertionError(failure.reason());
                };
        assertThat(bound).isEqualTo(aiResult);
    }

    @Test
    void shouldReturnSuccessWrappingAiResultWhenMentionedFalseAndZeroScore() {
        SemanticJudgmentAi ai = mock(SemanticJudgmentAi.class);
        AiBrandMentionResult aiResult = new AiBrandMentionResult(false, 0.0);
        when(ai.judge(anyString())).thenReturn(aiResult);
        SemanticJudgmentEngine engine = new SemanticJudgmentEngine(ai);
        SemanticJudgmentOutcome outcome = engine.evaluate("Sony");
        assertThat(outcome).isInstanceOf(SemanticJudgmentOutcome.Success.class);
        AiBrandMentionResult bound =
                switch (outcome) {
                    case SemanticJudgmentOutcome.Success success -> success.result();
                    case SemanticJudgmentOutcome.Failure failure -> throw new AssertionError(failure.reason());
                };
        assertThat(bound).isEqualTo(aiResult);
    }

    @Test
    void shouldReturnFailureWhenConfidenceScoreIsNegative() {
        SemanticJudgmentAi ai = mock(SemanticJudgmentAi.class);
        when(ai.judge(anyString())).thenReturn(new AiBrandMentionResult(true, -0.1));
        SemanticJudgmentEngine engine = new SemanticJudgmentEngine(ai);
        SemanticJudgmentOutcome outcome = engine.evaluate("x");
        assertThat(outcome)
                .isInstanceOf(SemanticJudgmentOutcome.Failure.class)
                .isEqualTo(new SemanticJudgmentOutcome.Failure("confidenceScore out of range"));
    }

    @Test
    void shouldReturnFailureWhenConfidenceScoreExceedsOne() {
        SemanticJudgmentAi ai = mock(SemanticJudgmentAi.class);
        when(ai.judge(anyString())).thenReturn(new AiBrandMentionResult(true, 1.05));
        SemanticJudgmentEngine engine = new SemanticJudgmentEngine(ai);
        SemanticJudgmentOutcome outcome = engine.evaluate("x");
        assertThat(outcome)
                .isInstanceOf(SemanticJudgmentOutcome.Failure.class)
                .isEqualTo(new SemanticJudgmentOutcome.Failure("confidenceScore out of range"));
    }

    @Test
    void shouldReturnFailureWhenConfidenceScoreIsNaN() {
        SemanticJudgmentAi ai = mock(SemanticJudgmentAi.class);
        when(ai.judge(anyString())).thenReturn(new AiBrandMentionResult(false, Double.NaN));
        SemanticJudgmentEngine engine = new SemanticJudgmentEngine(ai);
        SemanticJudgmentOutcome outcome = engine.evaluate("x");
        assertThat(outcome)
                .isInstanceOf(SemanticJudgmentOutcome.Failure.class)
                .isEqualTo(new SemanticJudgmentOutcome.Failure("confidenceScore out of range"));
    }

    @Test
    void shouldReturnFailureWithExceptionDetailsWhenJudgeThrowsRuntimeException() {
        SemanticJudgmentAi ai = mock(SemanticJudgmentAi.class);
        when(ai.judge(anyString())).thenThrow(new RuntimeException("timeout"));
        SemanticJudgmentEngine engine = new SemanticJudgmentEngine(ai);
        SemanticJudgmentOutcome outcome = engine.evaluate("x");
        assertThat(outcome)
                .isInstanceOf(SemanticJudgmentOutcome.Failure.class)
                .isEqualTo(new SemanticJudgmentOutcome.Failure("java.lang.RuntimeException:timeout"));
    }

    @Test
    void shouldReturnFailureWhenAiReturnsNull() {
        SemanticJudgmentAi ai = mock(SemanticJudgmentAi.class);
        when(ai.judge(anyString())).thenReturn(null);
        SemanticJudgmentEngine engine = new SemanticJudgmentEngine(ai);
        SemanticJudgmentOutcome outcome = engine.evaluate("x");
        assertThat(outcome)
                .isInstanceOf(SemanticJudgmentOutcome.Failure.class)
                .isEqualTo(new SemanticJudgmentOutcome.Failure("null"));
    }
}
