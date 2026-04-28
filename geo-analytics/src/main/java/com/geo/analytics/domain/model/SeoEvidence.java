package com.geo.analytics.domain.model;

import java.time.Instant;
import java.util.Optional;

/**
 * RAG 証拠としてディベート等に渡す検索行の精鋭サブセット。
 */
public record SeoEvidence(
        String url,
        String title,
        String snippet,
        double priorityScore,
        Optional<Instant> publishedAt,
        String relevanceCategory) {}
