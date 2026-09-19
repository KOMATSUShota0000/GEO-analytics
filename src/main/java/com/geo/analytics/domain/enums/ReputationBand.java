package com.geo.analytics.domain.enums;

/**
 * 評判スコアの帯（#62）。
 *
 * <p>Why: クエリごとの評判は、数値だけだと 62点と 68点の違いが読み手に伝わらない。一覧では帯で示し、
 * 解析全体では平均値を数値で示す（オーナー確定 2026-09-19）。
 */
public enum ReputationBand {
    HIGH,
    MEDIUM,
    LOW
}
