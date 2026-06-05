package com.geo.analytics.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.domain.enums.AiRecognitionState;
import com.geo.analytics.domain.model.AiRecognitionSummary;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiRecognitionAggregatorTest {

    @Test
    void emptyOrAllNull_yieldsUnknownWithZeroCounts() {
        AiRecognitionSummary a = AiRecognitionAggregator.aggregate(List.of());
        assertThat(a.dominant()).isEqualTo(AiRecognitionState.UNKNOWN);
        assertThat(a.evaluatedCount()).isZero();

        var withNulls = new java.util.ArrayList<AiRecognitionState>();
        withNulls.add(null);
        withNulls.add(null);
        AiRecognitionSummary b = AiRecognitionAggregator.aggregate(withNulls);
        assertThat(b.evaluatedCount()).isZero();
        assertThat(b.dominant()).isEqualTo(AiRecognitionState.UNKNOWN);
    }

    @Test
    void nullCollection_isSafe() {
        AiRecognitionSummary a = AiRecognitionAggregator.aggregate(null);
        assertThat(a.evaluatedCount()).isZero();
        assertThat(a.dominant()).isEqualTo(AiRecognitionState.UNKNOWN);
    }

    @Test
    void countsAreTallied_andNullsExcluded() {
        var states = new java.util.ArrayList<AiRecognitionState>();
        states.add(AiRecognitionState.RECOGNIZED_CORRECTLY);
        states.add(AiRecognitionState.RECOGNIZED_CORRECTLY);
        states.add(AiRecognitionState.MISIDENTIFIED);
        states.add(AiRecognitionState.UNKNOWN);
        states.add(null);
        AiRecognitionSummary a = AiRecognitionAggregator.aggregate(states);
        assertThat(a.recognizedCount()).isEqualTo(2);
        assertThat(a.misidentifiedCount()).isEqualTo(1);
        assertThat(a.unknownCount()).isEqualTo(1);
        assertThat(a.evaluatedCount()).isEqualTo(4);
    }

    @Test
    void anyMisidentified_yieldsMisidentified_evenIfMajorityRecognized() {
        // オーナー方針(B): 多数が正しく認識でも、取り違えが1件でもあれば警告として MISIDENTIFIED。
        // 例: 正しく認識8・取り違え1・知らない3。
        var states = new java.util.ArrayList<AiRecognitionState>();
        for (int i = 0; i < 8; i++) {
            states.add(AiRecognitionState.RECOGNIZED_CORRECTLY);
        }
        states.add(AiRecognitionState.MISIDENTIFIED);
        for (int i = 0; i < 3; i++) {
            states.add(AiRecognitionState.UNKNOWN);
        }
        var summary = AiRecognitionAggregator.aggregate(states);
        assertThat(summary.dominant()).isEqualTo(AiRecognitionState.MISIDENTIFIED);
        // 内訳件数は実数のまま保持（フロントでニュアンス表示できる）。
        assertThat(summary.recognizedCount()).isEqualTo(8);
        assertThat(summary.misidentifiedCount()).isEqualTo(1);
        assertThat(summary.unknownCount()).isEqualTo(3);
    }

    @Test
    void noMisidentified_someRecognized_yieldsRecognized() {
        // 取り違えなし・1件でも正しく認識 → RECOGNIZED_CORRECTLY（未認識が混じっていても可）。
        var states = List.of(
                AiRecognitionState.RECOGNIZED_CORRECTLY,
                AiRecognitionState.RECOGNIZED_CORRECTLY,
                AiRecognitionState.UNKNOWN,
                AiRecognitionState.UNKNOWN);
        assertThat(AiRecognitionAggregator.aggregate(states).dominant())
                .isEqualTo(AiRecognitionState.RECOGNIZED_CORRECTLY);
    }

    @Test
    void onlyUnknown_yieldsUnknown() {
        var states = List.of(AiRecognitionState.UNKNOWN, AiRecognitionState.UNKNOWN);
        assertThat(AiRecognitionAggregator.aggregate(states).dominant())
                .isEqualTo(AiRecognitionState.UNKNOWN);
    }

    @Test
    void misidentifiedWithUnknownButNoRecognized_yieldsMisidentified() {
        // 取り違えが1件でもあれば、正しく認識が0でも MISIDENTIFIED を警告。
        var states = List.of(
                AiRecognitionState.MISIDENTIFIED,
                AiRecognitionState.UNKNOWN,
                AiRecognitionState.UNKNOWN);
        assertThat(AiRecognitionAggregator.aggregate(states).dominant())
                .isEqualTo(AiRecognitionState.MISIDENTIFIED);
    }
}
