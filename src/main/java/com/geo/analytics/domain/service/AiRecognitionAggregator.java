package com.geo.analytics.domain.service;

import com.geo.analytics.domain.enums.AiRecognitionState;
import com.geo.analytics.domain.model.AiRecognitionSummary;
import java.util.Collection;

/**
 * クエリ単位の {@link AiRecognitionState} をジョブ単位へ集約する純粋ロジック（V13 Sprint4a-2）。
 *
 * <p>これは<strong>スコア非算入</strong>のレポート用エビデンスの集約であり、出力をスコア計算へ渡してはならない。
 * null（未判定）は件数から除外する。
 */
public final class AiRecognitionAggregator {

    private AiRecognitionAggregator() {}

    public static AiRecognitionSummary aggregate(Collection<AiRecognitionState> states) {
        int recognized = 0;
        int misidentified = 0;
        int unknown = 0;
        if (states != null) {
            for (AiRecognitionState state : states) {
                if (state == null) {
                    continue;
                }
                switch (state) {
                    case RECOGNIZED_CORRECTLY -> recognized++;
                    case MISIDENTIFIED -> misidentified++;
                    case UNKNOWN -> unknown++;
                }
            }
        }
        int evaluated = recognized + misidentified + unknown;
        return new AiRecognitionSummary(
                resolveDominant(recognized, misidentified, unknown, evaluated),
                recognized, misidentified, unknown, evaluated);
    }

    /**
     * 代表ステートを決める（オーナー方針 2026-06-05）。GEOでは「AIに誤認識される」見逃しを避けたいため、
     * <strong>取り違えが1件でもあれば MISIDENTIFIED を警告として表に出す</strong>。取り違えが無く、1件でも
     * 正しく認識されていれば RECOGNIZED_CORRECTLY。全て未認識（または評価0件）なら UNKNOWN。
     * 多数決ではない理由: 多数が正しくても1件の誤認識を埋もれさせない（内訳件数で実数は別途見せる）。
     */
    private static AiRecognitionState resolveDominant(int recognized, int misidentified, int unknown, int evaluated) {
        if (evaluated == 0) {
            return AiRecognitionState.UNKNOWN;
        }
        if (misidentified > 0) {
            return AiRecognitionState.MISIDENTIFIED;
        }
        if (recognized > 0) {
            return AiRecognitionState.RECOGNIZED_CORRECTLY;
        }
        return AiRecognitionState.UNKNOWN;
    }
}
