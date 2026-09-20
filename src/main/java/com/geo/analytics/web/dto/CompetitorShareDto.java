package com.geo.analytics.web.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * 競合シェア円グラフの1切片（#112）。
 *
 * @param share 0〜100 のパーセント（小数第1位）。分母は解析全体の SoM 合計（オーナー確定 2026-09-20）
 * @param self  自社の切片か。ホワイトラベルのブランドカラーを当てる側を表示側に判定させないために持つ
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CompetitorShareDto(String label, double share, boolean self) {}
