package com.geo.analytics.domain.model;

/**
 * 回答本文におけるブランド言及の物理量。LLM の自己申告ではなく Java で数えた値（.cursorrules 12節）。
 *
 * @param mentionCount 言及回数。同じ1箇所を2回数えない
 * @param mentionChars 言及箇所の文字数の合計（正規化後の本文上で数える）
 * @param totalTokens  本文の形態素トークン数
 */
public record BrandMentionMetrics(int mentionCount, int mentionChars, int totalTokens) {

    public static final BrandMentionMetrics NONE = new BrandMentionMetrics(0, 0, 0);

    public BrandMentionMetrics {
        if (mentionCount < 0 || mentionChars < 0 || totalTokens < 0) {
            throw new IllegalArgumentException("metrics must not be negative");
        }
    }

    /**
     * 言及密度＝回数 ÷ 総トークン数。
     *
     * <p>Why: 旧実装は LLM 申告の「文字数」を Sudachi の「トークン数」で割っており単位が合っていなかった（#60）。
     * 分子分母をどちらも「数」に揃える。トークン0件ならダミーで水増しせず 0.0 を返す（論理パディング）。
     */
    public double density() {
        return totalTokens == 0 ? 0.0 : (double) mentionCount / (double) totalTokens;
    }
}
