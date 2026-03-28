package com.geo.analytics.domain.model;

public final class VisibilityStageMapper {
    private VisibilityStageMapper() {}

    public record StageDefinition(int stage, String bandLabel, String narrative) {}

    public static StageDefinition define(Integer visibilityStage) {
        var s = visibilityStage == null ? 1 : visibilityStage;
        s = Math.clamp(s, 1, 10);
        if (s <= 2) {
            return new StageDefinition(
                s,
                "基礎認識・インジェスト",
                "AIモデルがエンティティを認識し始めた状態");
        }
        if (s <= 4) {
            return new StageDefinition(
                s,
                "文脈理解・分類",
                "限定的な質問や指名検索で言及される状態");
        }
        if (s <= 6) {
            return new StageDefinition(
                s,
                "信頼構築・部分引用",
                "まとめ記事等で選択肢として羅列される状態");
        }
        if (s <= 8) {
            return new StageDefinition(
                s,
                "競合優位・主情報源化",
                "トップ3の推奨ブランドとして言及される状態");
        }
        return new StageDefinition(
            s,
            "第一想起・グローバル可視性",
            "特定の課題において単独のベストアンサーとして推薦される状態");
    }
}
