package com.geo.analytics.domain.model;

import com.geo.analytics.domain.enums.AiRecognitionState;

/**
 * ジョブ内の全クエリの {@link AiRecognitionState} を集約した、レポート用の定性サマリ（V13 Sprint4a-2）。
 *
 * <p>{@code dominant} は代表ステート、各 *Count は内訳件数。スコアには一切influenceしない表示専用の値。
 * フロントは件数を使って「大半で正しく認識／一部で取り違え」等のニュアンス表現ができる。
 */
public record AiRecognitionSummary(
        AiRecognitionState dominant,
        int recognizedCount,
        int misidentifiedCount,
        int unknownCount,
        int evaluatedCount) {}
