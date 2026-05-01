package com.geo.analytics.application.dto;

import java.util.UUID;

/**
 * クエリ提案からジョブへの変換結果。冪等再試行時は {@code created == false}。
 */
public record ConvertProposalToJobOutcome(UUID jobId, boolean created) {}
