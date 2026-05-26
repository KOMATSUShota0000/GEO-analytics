package com.geo.analytics.domain.scoring;

public record ScoreBreakdown(
        double aiAuditScore, double meoTrustScore, double machineReadabilityScore, double geoReadinessScore) {}
