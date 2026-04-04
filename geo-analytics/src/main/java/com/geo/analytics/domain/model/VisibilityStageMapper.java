package com.geo.analytics.domain.model;

public final class VisibilityStageMapper {
    private VisibilityStageMapper() {}

    public record StageDefinition(int stage, String bandLabel, String narrative, double progressRate) {}

    public static StageDefinition define(Integer visibilityStage) {
        return define(visibilityStage, null);
    }

    public static StageDefinition define(Integer visibilityStage, Integer rankPosition) {
        var s = visibilityStage == null ? 1 : visibilityStage;
        s = Math.clamp(s, 1, 10);
        var progress = progressRate(s, rankPosition);
        if (s <= 2) {
            return new StageDefinition(
                s,
                "基礎認識・インジェスト",
                "AIモデルがエンティティを認識し始めた状態",
                progress);
        }
        if (s <= 4) {
            return new StageDefinition(
                s,
                "文脈理解・分類",
                "限定的な質問や指名検索で言及される状態",
                progress);
        }
        if (s <= 6) {
            return new StageDefinition(
                s,
                "信頼構築・部分引用",
                "まとめ記事等で選択肢として羅列される状態",
                progress);
        }
        if (s <= 8) {
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

    private static double progressRate(int stage, Integer rankPosition) {
        if (rankPosition == null || rankPosition <= 0) {
            return 0.0;
        }
        double r = rankPosition;
        double currentBoundary = currentBoundary(stage);
        double nextBoundary = nextBoundary(stage);
        double denominator = StrictMath.log(nextBoundary) - StrictMath.log(currentBoundary);
        if (StrictMath.abs(denominator) < 1.0E-10) {
            return 0.0;
        }
        double numerator = StrictMath.log(nextBoundary) - StrictMath.log(r);
        double p = numerator / denominator;
        if (p < 0.0) {
            return 0.0;
        }
        if (p > 1.0) {
            return 1.0;
        }
        return p;
    }

    private static double currentBoundary(int stage) {
        return switch (stage) {
            case 10 -> 1.0;
            case 9 -> 2.0;
            case 8 -> 3.0;
            case 7 -> 5.0;
            case 6 -> 10.0;
            case 5 -> 20.0;
            case 4 -> 30.0;
            case 3 -> 50.0;
            case 2 -> 80.0;
            default -> 120.0;
        };
    }

    private static double nextBoundary(int stage) {
        return switch (stage) {
            case 10 -> 2.0;
            case 9 -> 3.0;
            case 8 -> 5.0;
            case 7 -> 10.0;
            case 6 -> 20.0;
            case 5 -> 30.0;
            case 4 -> 50.0;
            case 3 -> 80.0;
            case 2 -> 120.0;
            default -> 200.0;
        };
    }
}
