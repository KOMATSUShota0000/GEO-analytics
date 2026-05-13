package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.SelectedCompetitor;
import com.geo.analytics.domain.enums.IndustryType;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class SyntheticSelectedCompetitorFactory {

    public List<SelectedCompetitor> threeShortReasoningPlaceholders(IndustryType industry, String tradeAreaLabel) {
        String label = industry.getLabel();
        List<SelectedCompetitor> list = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String suffix = String.valueOf((char) ('A' + i));
            String name = tradeAreaLabel + "における" + label + "の標準モデル競合" + suffix;
            String reasoning =
                    tradeAreaLabel + "および" + label + "に整合するGEO Readiness評価用の参照モデルとして配置した。";
            list.add(new SelectedCompetitor(name, null, null, null, reasoning, true));
        }
        return List.copyOf(list);
    }

    public SelectedCompetitor singleFilterPadPlaceholder(IndustryType industry, String tradeAreaLabel, int ordinal) {
        String label = industry.getLabel();
        String areaPart = tradeAreaLabel.isEmpty() ? "対象商圏" : tradeAreaLabel;
        String suffix = String.valueOf((char) ('A' + ordinal));
        String name = areaPart + "における" + label + "の標準モデル競合" + suffix;
        String reasoning =
                areaPart + "および" + label + "に整合するGEO Readiness評価用の参照モデルとして配置した。"
                        + "AI可視性ランクの改善余地とAI推奨ポテンシャルを比較するための仮想競合である。";
        return new SelectedCompetitor(name, null, null, null, reasoning, true);
    }
}
