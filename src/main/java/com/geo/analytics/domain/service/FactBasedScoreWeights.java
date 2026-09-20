package com.geo.analytics.domain.service;

import com.geo.analytics.domain.enums.BusinessModelType;

/**
 * 基礎スコア（0〜100）の配分（#84）。
 *
 * <p>Why: MEO（Google マップのクチコミ）枠25点は、Google マップの実体を持たない業種
 * （法人向け SaaS・オンラインサービス）では常に 0 になる。その結果、旧実装では
 * <b>AI ルーブリック50 ＋ 機械可読性25 ＝ 最大75点</b>が上限となり、業種によって到達できない点数が生まれていた。
 *
 * <p>権威軸は同じ問題に対して {@code GeoVisibilityCalculatorService.authorityLocalMeoSub} で
 * 「非地域業種は MEO 枠をゼロにし、その枠を他へ再配分する」手当を既に持っている。基礎スコア側にも同じ考え方を入れる。
 * 満点を業種別に変える案は採らない。満点が動くと他社比較・経時比較の読みが壊れるため（オーナー確定 2026-09-20）。
 */
public final class FactBasedScoreWeights {

    private static final double AI_BASE = 50.0d;
    private static final double MEO_BASE = 25.0d;
    private static final double MACHINE_READABILITY_BASE = 25.0d;
    private static final double TOTAL = 100.0d;

    private FactBasedScoreWeights() {}

    public static boolean hasLocalPresence(BusinessModelType mode) {
        return mode != BusinessModelType.CORPORATE_SERVICE && mode != BusinessModelType.ONLINE_SERVICE;
    }

    /**
     * MEO 枠を持たない業種では、AI ルーブリックと機械可読性へ比例配分する係数を返す。
     * 地域業種では 1.0（配分を変えない）。
     */
    public static double nonLocalRedistributionFactor(BusinessModelType mode) {
        if (hasLocalPresence(mode)) {
            return 1.0d;
        }
        return StrictMath.fma(TOTAL, 1.0d / (AI_BASE + MACHINE_READABILITY_BASE), 0.0d);
    }

    /** 実測の MEO 点。地域業種はそのまま、非地域業種は枠ごと落とす（0点とは意味が違う）。 */
    public static double meoScoreFor(BusinessModelType mode, double measuredMeoScore) {
        return hasLocalPresence(mode) ? measuredMeoScore : 0.0d;
    }

    static double maxMeoScore() {
        return MEO_BASE;
    }
}
