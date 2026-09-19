package com.geo.analytics.domain.service;

import com.geo.analytics.domain.enums.ReputationBand;
import com.geo.analytics.domain.matching.RobustAuditMathUtil;
import java.lang.StrictMath;

/**
 * AI 回答での語られ方（感情強度）を「評判スコア」へ変換する純粋ロジック（#62）。
 *
 * <p>Why: 感情強度は LLM が出す生値（-1.0〜1.0）のまま、どのスコアにも使われていなかった。オーナー確定
 * （2026-09-19）により、SoM にも権威軸にも混ぜず、**独立した評判スコア**として持つ。SoM は「どれだけ
 * 見えているか」、評判は「どう言われたか」で軸が違うため。
 *
 * <p>生値をそのまま百分率にせず飽和型（tanh）で変換するのは、LLM の生値が 0.85 と 0.90 のように
 * 誤差の範囲で揺れるため。両端を圧縮し、揺れがスコアをほとんど動かさないようにする（`.cursorrules` 7節）。
 */
public final class ReputationScoreCalculator {

    /** 飽和の強さ。1.5 のとき生値 0.9 と 0.85 の差は約1点に収まり、0.4 と 0.7 の差は約12点として残る。 */
    private static final double SATURATION_GAIN = 1.5d;

    /** これ以上を「高」とする。生値でおよそ 0.4 以上（明確に好意的に語られている）。 */
    public static final int HIGH_THRESHOLD = 75;

    /** これ未満を「低」とする。生値でおよそ -0.3 以下（否定的に語られている）。 */
    public static final int LOW_THRESHOLD = 45;

    private ReputationScoreCalculator() {}

    /** 生値（-1.0〜1.0）を 0〜100 の評判スコアへ変換する。 */
    public static int percent(double rawSentimentIntensity) {
        double clamped = StrictMath.max(-1.0d, StrictMath.min(1.0d, rawSentimentIntensity));
        double saturated = RobustAuditMathUtil.tanhStable(SATURATION_GAIN * clamped);
        double percent = 50.0d * (1.0d + saturated);
        return (int) StrictMath.round(StrictMath.max(0.0d, StrictMath.min(100.0d, percent)));
    }

    public static ReputationBand band(int percent) {
        if (percent >= HIGH_THRESHOLD) {
            return ReputationBand.HIGH;
        }
        if (percent < LOW_THRESHOLD) {
            return ReputationBand.LOW;
        }
        return ReputationBand.MEDIUM;
    }
}
