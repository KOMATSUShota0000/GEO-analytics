package com.geo.analytics.domain.service;

import com.geo.analytics.domain.model.CompetitorResult;
import java.lang.StrictMath;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析全体の競合シェア（自社 vs 競合）を出す純粋ロジック（#112）。
 *
 * <p>オーナー確定（2026-09-20）: 円グラフは<b>解析全体で1枚</b>、シェアの分母は<b>SoM スコア比</b>。
 * 言及回数比を採らないのは、上位で引用された価値（引用順位ボーナス）が消えるため。
 *
 * <p>Why: 円グラフは画面と PDF の両方が同じ数字を出す必要がある。集計をどちらかの表示側に書くと二重実装に
 * なるため、ドメインの純粋関数として1箇所に置く（.cursorrules 10節 SSOT）。
 */
public final class CompetitorShareAggregator {

    /** Why: 切片が細すぎると読めない。上位に満たない競合は「その他」へ畳む。 */
    static final int MAX_COMPETITOR_SLICES = 5;
    static final String OTHERS_LABEL = "その他";

    private CompetitorShareAggregator() {}

    /**
     * @param ownBrandLabel 自社の表示名
     * @param ownSomScores  クエリごとの自社 SoM。null 要素は 0 として扱わず無視する
     * @param competitorResultsPerQuery クエリごとの競合実測値
     */
    public static List<CompetitorShare> aggregate(
            String ownBrandLabel,
            List<Double> ownSomScores,
            List<List<CompetitorResult>> competitorResultsPerQuery) {
        double ownTotal = 0.0;
        if (ownSomScores != null) {
            for (Double score : ownSomScores) {
                if (score != null && score > 0.0) {
                    ownTotal += score;
                }
            }
        }
        Map<String, Double> competitorTotals = new LinkedHashMap<>();
        if (competitorResultsPerQuery != null) {
            for (List<CompetitorResult> perQuery : competitorResultsPerQuery) {
                if (perQuery == null) {
                    continue;
                }
                for (CompetitorResult result : perQuery) {
                    if (result == null || result.competitorLabel() == null || result.somScore() == null) {
                        continue;
                    }
                    if (result.somScore() <= 0.0) {
                        continue;
                    }
                    competitorTotals.merge(result.competitorLabel(), result.somScore(), Double::sum);
                }
            }
        }
        double grandTotal = ownTotal + competitorTotals.values().stream().mapToDouble(Double::doubleValue).sum();
        if (grandTotal <= 0.0) {
            return List.of();
        }
        List<Map.Entry<String, Double>> ranked = new ArrayList<>(competitorTotals.entrySet());
        ranked.sort(Map.Entry.<String, Double>comparingByValue().reversed());
        List<CompetitorShare> shares = new ArrayList<>(MAX_COMPETITOR_SLICES + 2);
        shares.add(new CompetitorShare(ownBrandLabel, percent(ownTotal, grandTotal), true));
        double othersTotal = 0.0;
        for (int index = 0; index < ranked.size(); index++) {
            if (index < MAX_COMPETITOR_SLICES) {
                shares.add(new CompetitorShare(
                        ranked.get(index).getKey(), percent(ranked.get(index).getValue(), grandTotal), false));
            } else {
                othersTotal += ranked.get(index).getValue();
            }
        }
        if (othersTotal > 0.0) {
            shares.add(new CompetitorShare(OTHERS_LABEL, percent(othersTotal, grandTotal), false));
        }
        return List.copyOf(shares);
    }

    /** Why: 表示は小数第1位まで。JEP 306 準拠の StrictMath で丸め、OS 差で数字が動かないようにする。 */
    private static double percent(double part, double total) {
        return StrictMath.round(part / total * 1000.0) / 10.0;
    }

    /** @param share 0〜100 のパーセント（小数第1位） */
    public record CompetitorShare(String label, double share, boolean self) {}
}
