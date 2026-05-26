package com.geo.analytics.domain.model;

import com.geo.analytics.domain.matching.RobustAuditMathUtil;
import java.lang.StrictMath;

public final class VisibilityStageMapper {
    private VisibilityStageMapper() {}

    public record StageDefinition(int stage, String bandLabel, String narrative, double progressRate) {}

    private static final double[] CITATION_BOUNDARIES = {1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144};

    public static StageDefinition define(Integer visibilityStage) {
        return define(visibilityStage, null);
    }

    public static StageDefinition define(Integer visibilityStage, Integer aiCitationPosition) {
        var s = visibilityStage == null ? 10 : visibilityStage;
        s = StrictMath.max(1, StrictMath.min(10, s));
        var t = 11 - s;
        var progress = progressRate(t, aiCitationPosition);
        if (t <= 2) {
            return new StageDefinition(
                s,
                "基礎認識・インジェスト",
                "AIモデルがエンティティを認識し始めた状態",
                progress);
        }
        if (t <= 4) {
            return new StageDefinition(
                s,
                "文脈理解・分類",
                "限定的な質問や指名検索で言及される状態",
                progress);
        }
        if (t <= 6) {
            return new StageDefinition(
                s,
                "信頼構築・部分引用",
                "まとめ記事等で選択肢として羅列される状態",
                progress);
        }
        if (t <= 8) {
            return new StageDefinition(
                s,
                "競合優位・主情報源化",
                "トップ3の推奨ブランドとして言及される状態",
                progress);
        }
        return new StageDefinition(
            s,
            "第一想起・グローバル可視性",
            "特定の課題において単独のベストアンサーとして推薦される状態",
            progress);
    }

    private static double progressRate(int stage, Integer aiCitationPosition) {
        if (aiCitationPosition == null || aiCitationPosition <= 0) {
            return 0.0;
        }
        double rD = aiCitationPosition;
        double lo = CITATION_BOUNDARIES[10 - stage];
        double hi = CITATION_BOUNDARIES[11 - stage];
        if (lo <= 0.0 || hi <= 0.0 || lo >= hi) {
            return 0.0;
        }
        if (rD < lo) {
            return 1.0;
        }
        if (rD > hi) {
            return 0.0;
        }
        double denominator = StrictMath.log(hi) - StrictMath.log(lo);
        if (StrictMath.abs(denominator) < RobustAuditMathUtil.EPSILON) {
            return 0.0;
        }
        double numerator = StrictMath.log(hi) - StrictMath.log(rD);
        double p = numerator / denominator;
        if (p < 0.0) {
            return 0.0;
        }
        if (p > 1.0) {
            return 1.0;
        }
        return p;
    }
}
