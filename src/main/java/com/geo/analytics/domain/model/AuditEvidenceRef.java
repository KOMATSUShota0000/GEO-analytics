package com.geo.analytics.domain.model;

/**
 * 監査 JSONB に載せる RAG 参照の軽量表現（スニペット本文は含めない）。
 */
public record AuditEvidenceRef(String url, String title, double priorityScore, String relevanceCategory) {}
