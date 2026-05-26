package com.geo.analytics.web.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/**
 * 相対評価（ベンチマーク）レスポンス。
 *
 * <ul>
 *   <li>{@code locked} = true: 競合比較は Pro 以上の機能。非対象プランで開示しない
 *   <li>{@code available} = false: 解析未完了等で実データが無い（偽値は返さない）
 *   <li>{@code rows}: 実データに裏付けられた比較行のみ
 * </ul>
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RelativeBenchmarkResponse(
        boolean locked, boolean available, List<RelativeBenchmarkRow> rows) {}
