package com.geo.analytics.domain.service;

import java.lang.StrictMath;

/**
 * 自社サイト本文へのブランド名の詰め込み（キーワードスタッフィング）を減点する純粋ロジック（#61）。
 *
 * <p>Why: `.cursorrules` 7節が「ブランド言及密度が閾値（例3%）を超えたらパラボラ型減衰でスコアを減点する」
 * と定めるが、配線されていなかった。**適用先は自社サイト本文**であり、AI 回答ではない（オーナー確定 2026-09-19）。
 * 測定の材料が AI 回答へ変わった（#92）あとに AI 回答へ適用すると、「AI がたくさん言及してくれたこと」を
 * 罰することになり、GEO の目的と逆になる。実測でも指名検索の AI 回答は密度4.1%に達する。
 *
 * <p>減衰は放物線で、閾値の直後はほとんど減らず、離れるほど強く効く。詰め込みは程度問題であり、
 * 閾値を1ポイント超えただけで大きく減点するのは実態に合わないため。
 */
public final class StuffingPenaltyCalculator {

    /** これを超えると詰め込みとみなす言及密度（言及回数 ÷ 形態素トークン数）。`.cursorrules` 7節の例示値。 */
    public static final double STUFFING_THRESHOLD = 0.03d;

    /** この密度で減点が最大（係数0）になる。閾値の5倍で、通常の文章では到達し得ない水準。 */
    public static final double ZERO_RETENTION_DENSITY = 0.15d;

    private StuffingPenaltyCalculator() {}

    /**
     * 残存係数（1.0 = 減点なし、0.0 = 全減点）を返す。
     *
     * @param mentionDensity 自社サイト本文でのブランド言及密度。負値や NaN は減点なしとして扱う
     */
    public static double retentionFactor(double mentionDensity) {
        if (Double.isNaN(mentionDensity) || mentionDensity <= STUFFING_THRESHOLD) {
            return 1.0d;
        }
        if (mentionDensity >= ZERO_RETENTION_DENSITY) {
            return 0.0d;
        }
        double excessRatio = (mentionDensity - STUFFING_THRESHOLD) / (ZERO_RETENTION_DENSITY - STUFFING_THRESHOLD);
        double retention = StrictMath.fma(-excessRatio, excessRatio, 1.0d);
        return StrictMath.max(0.0d, StrictMath.min(1.0d, retention));
    }
}
