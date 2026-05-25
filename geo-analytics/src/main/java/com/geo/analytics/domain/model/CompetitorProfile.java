package com.geo.analytics.domain.model;

/**
 * 競合1件の表示用プロファイル。
 *
 * <p>合成（参照基準点）競合は実在しないため websiteUrl を持たず synthetic=true となる。
 * 既存の competitorUrls（URL文字列のみ）では合成かどうかを区別できずフロントが
 * 「空URL」を描画してしまうため、名称・URL・合成フラグをまとめて伝える。
 */
public record CompetitorProfile(String name, String websiteUrl, boolean synthetic) {
}
