package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.SelectedCompetitor;
import com.geo.analytics.domain.enums.IndustryType;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 合成（参照）競合の生成。実在競合が不足する文脈で、GEO Readiness 相対評価の
 * 「基準点」を提供する。
 *
 * <p>reasoning は固定テンプレではなく、(1) 発生理由 (2) 序数ごとの参照ティア
 * を反映して動的生成する。合成である事実を明示し実在競合を装わない（誤魔化さない）。
 */
@Component
public class SyntheticSelectedCompetitorFactory {

    /** 合成競合が必要になった理由。reasoning の原因句に反映される。 */
    public enum SyntheticPadReason {
        NO_CANDIDATES("競合候補が抽出できなかったため、"),
        INSUFFICIENT_REAL("AIフィルタで選定された実競合が規定数に満たなかったため、"),
        FILTER_UNAVAILABLE("競合フィルタAIが一時的に利用できなかったため、");

        private final String causeClause;

        SyntheticPadReason(String causeClause) {
            this.causeClause = causeClause;
        }

        public String causeClause() {
            return causeClause;
        }
    }

    private enum ReferenceTier {
        SUPERIOR("優位参照モデル", "当該業種で高いGEO Readinessを示す上位水準の参照点"),
        MEDIAN("中央値参照モデル", "業種中央値水準でAI推奨ポテンシャルの基準となる参照点"),
        IMPROVABLE("改善余地参照モデル", "AI可視性ランクに改善余地が大きいベースライン参照点");

        private final String tierName;
        private final String tierDesc;

        ReferenceTier(String tierName, String tierDesc) {
            this.tierName = tierName;
            this.tierDesc = tierDesc;
        }

        static ReferenceTier forOrdinal(int ordinal) {
            int idx = Math.floorMod(ordinal, values().length);
            return values()[idx];
        }
    }

    public List<SelectedCompetitor> threeShortReasoningPlaceholders(
            IndustryType industry, String tradeAreaLabel, SyntheticPadReason reason) {
        List<SelectedCompetitor> list = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            list.add(singleFilterPadPlaceholder(industry, tradeAreaLabel, i, reason));
        }
        return List.copyOf(list);
    }

    public SelectedCompetitor singleFilterPadPlaceholder(
            IndustryType industry, String tradeAreaLabel, int ordinal, SyntheticPadReason reason) {
        String label = industry != null ? industry.getLabel() : IndustryType.OTHER.getLabel();
        String areaPart =
                tradeAreaLabel == null || tradeAreaLabel.isBlank() ? "対象商圏" : tradeAreaLabel.trim();
        SyntheticPadReason safeReason = reason != null ? reason : SyntheticPadReason.NO_CANDIDATES;
        ReferenceTier tier = ReferenceTier.forOrdinal(ordinal);
        String suffix = String.valueOf((char) ('A' + Math.floorMod(ordinal, 26)));

        String name = areaPart + "における" + label + "の" + tier.tierName + suffix;
        String reasoning = areaPart + "における" + label + "市場の" + tier.tierName + "。"
                + safeReason.causeClause()
                + "実在競合ではなくGEO Readiness相対評価の基準点として配置した（"
                + tier.tierDesc + "）。";
        return new SelectedCompetitor(name, null, null, null, reasoning, true);
    }
}
