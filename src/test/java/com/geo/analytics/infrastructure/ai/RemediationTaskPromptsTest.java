package com.geo.analytics.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.domain.enums.RubricCriterionId;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * #139: 改善タスクの根拠欄に {@code criterionId=... self_status=NO} がそのまま出ていた。
 *
 * <p>AI は入力の見出しを出力へ写すため、入力の段階で画面と同じ日本語の言葉にしておく。
 */
class RemediationTaskPromptsTest {

    @Test
    void ギャップは日本語の項目名と状態で渡し内部IDや判定値を含めない() {
        String payload = RemediationTaskPrompts.userPayload(List.of(
                new RemediationTaskPrompts.GapContext("PRICE_AND_CONSTRAINTS", "NO", "", "初期費用 5万円〜"),
                new RemediationTaskPrompts.GapContext("ENTITY_BIOGRAPHY", "PARTIAL", "2007年に上海へ進出", null)));

        assertThat(payload)
                .contains("診断項目名: 詳細な料金体系と制約")
                .contains("自社サイトの状態: 記載なし")
                .contains("競合サイトの該当箇所: 「初期費用 5万円〜」")
                .contains("診断項目名: 具体的な経歴・バイオグラフィー")
                .contains("自社サイトの状態: 一部のみ記載あり")
                .contains("自社サイトの該当箇所: 「2007年に上海へ進出」")
                .contains("競合サイトの該当箇所: （なし）")
                .doesNotContain("criterionId", "self_status", "PRICE_AND_CONSTRAINTS", "PARTIAL", "=NO");
    }

    @Test
    void 改善タスクの対象になる10項目はすべて日本語名を持つ() {
        for (RubricCriterionId id : RubricCriterionId.llmCriteria()) {
            assertThat(RemediationTaskPrompts.criterionLabel(id.name()))
                    .as(id.name())
                    .isNotEqualTo(id.name());
        }
    }

    @Test
    void 未知の項目IDはそのまま返し落とさない() {
        assertThat(RemediationTaskPrompts.criterionLabel("UNKNOWN_ITEM")).isEqualTo("UNKNOWN_ITEM");
    }
}
