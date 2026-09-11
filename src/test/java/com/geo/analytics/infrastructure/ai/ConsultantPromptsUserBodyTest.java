package com.geo.analytics.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.infrastructure.ai.dto.BatchQueryLine;
import org.junit.jupiter.api.Test;

/**
 * ユーザーターン本文の SSOT 化（#70）が挙動を変えていないことを固定する。
 * 期待値は集約前の各経路が生成していた文字列の組み立て規則そのもの。
 */
class ConsultantPromptsUserBodyTest {

    private static final String BRAND = "ACME";
    private static final String QUERY = "価格";
    private static final String CTX = "【事業概要】テスト事業";

    @Test
    void 材料が無ければクエリのみの文面を選ぶ() {
        String body = ConsultantPrompts.userBody(BRAND, QUERY, null, 1.0, null, null);

        assertThat(body).isEqualTo(ConsultantPrompts.userTextBrandQueryOnly(BRAND, QUERY));
    }

    @Test
    void 材料が空白だけでもクエリのみの文面を選ぶ() {
        String body = ConsultantPrompts.userBody(BRAND, QUERY, "   ", 1.0, null, null);

        assertThat(body).isEqualTo(ConsultantPrompts.userTextBrandQueryOnly(BRAND, QUERY));
    }

    @Test
    void 材料があればクロール本文の文面を選ぶ() {
        String body = ConsultantPrompts.userBody(BRAND, QUERY, "サイト本文", 0.5, "Schema.org: 実装あり", null);

        assertThat(body)
                .isEqualTo(ConsultantPrompts.userTextBrandQueryWithWebsiteExtract(
                        BRAND, QUERY, "サイト本文", 0.5, "Schema.org: 実装あり"));
    }

    @Test
    void ジョブ文脈は本文の前に空行2つで前置きされる() {
        String body = ConsultantPrompts.userBody(BRAND, QUERY, null, 1.0, null, CTX);

        assertThat(body).isEqualTo(CTX + "\n\n" + ConsultantPrompts.userTextBrandQueryOnly(BRAND, QUERY));
    }

    @Test
    void ジョブ文脈が空なら前置きしない() {
        String expected = ConsultantPrompts.userTextBrandQueryOnly(BRAND, QUERY);

        assertThat(ConsultantPrompts.userBody(BRAND, QUERY, null, 1.0, null, "   ")).isEqualTo(expected);
        assertThat(ConsultantPrompts.userBody(BRAND, QUERY, null, 1.0, null, null)).isEqualTo(expected);
    }

    @Test
    void バッチ経路の組み立てがシステム指示とユーザー本文の連結のままであること() {
        var line = new BatchQueryLine(java.util.UUID.randomUUID(), QUERY);

        String combined = GeminiBatchPromptText.combinedPromptText(BRAND, line, SubscriptionPlan.PRO, CTX);

        assertThat(combined)
                .isEqualTo(ConsultantPrompts.systemText(SubscriptionPlan.PRO, BRAND)
                        + "\n\n"
                        + ConsultantPrompts.userBody(BRAND, QUERY, null, 1.0, null, CTX));
    }

    @Test
    void バッチ経路はジョブ文脈が無くても同じ規則で組み立てる() {
        var line = new BatchQueryLine(java.util.UUID.randomUUID(), QUERY);

        String combined = GeminiBatchPromptText.combinedPromptText(BRAND, line, SubscriptionPlan.STANDARD, null);

        assertThat(combined)
                .isEqualTo(ConsultantPrompts.systemText(SubscriptionPlan.STANDARD, BRAND)
                        + "\n\n"
                        + ConsultantPrompts.userTextBrandQueryOnly(BRAND, QUERY));
    }
}
