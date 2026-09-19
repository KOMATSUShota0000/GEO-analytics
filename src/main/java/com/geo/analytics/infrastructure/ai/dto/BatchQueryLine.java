package com.geo.analytics.infrastructure.ai.dto;

import java.util.UUID;

/**
 * バッチ投入1行分のクエリ。
 *
 * @param aiOverviewText 実測の AI Overview 本文。取れなかったクエリは null（材料は推定になる。#94）
 */
public record BatchQueryLine(UUID queryId, String queryText, String aiOverviewText) {}
