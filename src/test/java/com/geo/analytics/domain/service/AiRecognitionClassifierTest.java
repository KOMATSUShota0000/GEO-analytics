package com.geo.analytics.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.domain.enums.AiRecognitionState;
import org.junit.jupiter.api.Test;

class AiRecognitionClassifierTest {

    private static final String BRAND = "アクメ";

    @Test
    void notMentioned_yieldsUnknown() {
        // 言及なしは取り違えと断定せず UNKNOWN。
        assertThat(AiRecognitionClassifier.classify(false, "アクメ", BRAND))
                .isEqualTo(AiRecognitionState.UNKNOWN);
    }

    @Test
    void blankResolvedOrCanonical_yieldsUnknown() {
        assertThat(AiRecognitionClassifier.classify(true, "  ", BRAND))
                .isEqualTo(AiRecognitionState.UNKNOWN);
        assertThat(AiRecognitionClassifier.classify(true, "アクメ", null))
                .isEqualTo(AiRecognitionState.UNKNOWN);
    }

    @Test
    void exactMatch_yieldsRecognizedCorrectly() {
        assertThat(AiRecognitionClassifier.classify(true, "アクメ", BRAND))
                .isEqualTo(AiRecognitionState.RECOGNIZED_CORRECTLY);
    }

    @Test
    void legalFormAndWhitespaceDiffer_stillRecognized() {
        // 「アクメ株式会社」⊃「アクメ」。法人格・空白の揺れを吸収して同一実体とみなす。
        assertThat(AiRecognitionClassifier.classify(true, "アクメ 株式会社", BRAND))
                .isEqualTo(AiRecognitionState.RECOGNIZED_CORRECTLY);
        assertThat(AiRecognitionClassifier.classify(true, "ACME Inc.", "acme"))
                .isEqualTo(AiRecognitionState.RECOGNIZED_CORRECTLY);
    }

    @Test
    void differentEntity_yieldsMisidentified() {
        // 言及はあるが別実体（同名他社・ハルシネーション）に解決された場合は取り違え。
        assertThat(AiRecognitionClassifier.classify(true, "ベータ商会", BRAND))
                .isEqualTo(AiRecognitionState.MISIDENTIFIED);
    }

    @Test
    void singleCharLabels_doNotFalseMatchByContainment() {
        // 1文字同士は偶然包含の誤判定を避け、完全一致のみ許容する。
        assertThat(AiRecognitionClassifier.classify(true, "A", "AB"))
                .isEqualTo(AiRecognitionState.MISIDENTIFIED);
    }
}
