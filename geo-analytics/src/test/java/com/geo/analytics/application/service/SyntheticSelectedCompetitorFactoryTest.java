package com.geo.analytics.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.application.dto.SelectedCompetitor;
import com.geo.analytics.application.service.SyntheticSelectedCompetitorFactory.SyntheticPadReason;
import com.geo.analytics.domain.enums.IndustryType;
import java.util.List;
import org.junit.jupiter.api.Test;

class SyntheticSelectedCompetitorFactoryTest {

    private final SyntheticSelectedCompetitorFactory factory = new SyntheticSelectedCompetitorFactory();

    @Test
    void threeReasonings_areMutuallyDistinct_byReferenceTier() {
        List<SelectedCompetitor> list = factory.threeShortReasoningPlaceholders(
                IndustryType.OTHER, "東京都", SyntheticPadReason.NO_CANDIDATES);

        assertThat(list).hasSize(3);
        String r0 = list.get(0).reasoning();
        String r1 = list.get(1).reasoning();
        String r2 = list.get(2).reasoning();
        assertThat(r0).isNotEqualTo(r1);
        assertThat(r1).isNotEqualTo(r2);
        assertThat(r0).isNotEqualTo(r2);
        assertThat(r0).contains("優位参照モデル");
        assertThat(r1).contains("中央値参照モデル");
        assertThat(r2).contains("改善余地参照モデル");
    }

    @Test
    void reasoning_reflectsPadReasonCauseClause() {
        SelectedCompetitor noCand = factory.singleFilterPadPlaceholder(
                IndustryType.OTHER, "大阪", 0, SyntheticPadReason.NO_CANDIDATES);
        SelectedCompetitor insufficient = factory.singleFilterPadPlaceholder(
                IndustryType.OTHER, "大阪", 0, SyntheticPadReason.INSUFFICIENT_REAL);
        SelectedCompetitor unavailable = factory.singleFilterPadPlaceholder(
                IndustryType.OTHER, "大阪", 0, SyntheticPadReason.FILTER_UNAVAILABLE);

        assertThat(noCand.reasoning()).contains("競合候補が抽出できなかったため");
        assertThat(insufficient.reasoning()).contains("実競合が規定数に満たなかったため");
        assertThat(unavailable.reasoning()).contains("競合フィルタAIが一時的に利用できなかったため");
    }

    @Test
    void reasoning_alwaysDeclaresSyntheticNature() {
        SelectedCompetitor c = factory.singleFilterPadPlaceholder(
                IndustryType.OTHER, "名古屋", 1, SyntheticPadReason.INSUFFICIENT_REAL);

        assertThat(c.synthetic()).isTrue();
        assertThat(c.reasoning()).contains("実在競合ではなく");
        assertThat(c.reasoning()).contains("GEO Readiness相対評価の基準点");
    }

    @Test
    void blankArea_fallsBackToDefaultLabel() {
        SelectedCompetitor c = factory.singleFilterPadPlaceholder(
                IndustryType.OTHER, "  ", 0, SyntheticPadReason.NO_CANDIDATES);

        assertThat(c.reasoning()).contains("対象商圏");
        assertThat(c.name()).contains("対象商圏");
    }

    @Test
    void nullReasonAndNullIndustry_handledSafely() {
        SelectedCompetitor c = factory.singleFilterPadPlaceholder(null, null, 0, null);

        assertThat(c.synthetic()).isTrue();
        assertThat(c.reasoning()).isNotBlank();
        assertThat(c.reasoning()).contains("実在競合ではなく");
    }

    @Test
    void oldFixedTemplateText_isGone() {
        SelectedCompetitor c = factory.singleFilterPadPlaceholder(
                IndustryType.OTHER, "福岡", 2, SyntheticPadReason.NO_CANDIDATES);

        assertThat(c.reasoning()).doesNotContain("GEO Readiness評価用の参照モデルとして配置した。");
    }
}
