package com.geo.analytics.domain.metrics;

import java.lang.StrictMath;

public final class EntropyMetricsCalculator {

    public record EntropyMetrics(double effectiveLength, double entropyDensity, int codePointCount) {
    }

    public EntropyMetrics compute(String normalizedText) {
        if (normalizedText == null || normalizedText.isEmpty()) {
            return new EntropyMetrics(0.0, 0.0, 0);
        }
        long weightedSum = 0L;
        int codePointCount = 0;
        for (int i = 0, len = normalizedText.length(); i < len; ) {
            int cp = normalizedText.codePointAt(i);
            i += Character.charCount(cp);
            codePointCount++;
            weightedSum += weightPoints(cp);
        }
        if (codePointCount == 0) {
            return new EntropyMetrics(0.0, 0.0, 0);
        }
        double effectiveLength = (double) weightedSum / 10.0d;
        double invCount = 1.0d / (double) codePointCount;
        double entropyDensity = StrictMath.fma(effectiveLength, invCount, 0.0d);
        return new EntropyMetrics(effectiveLength, entropyDensity, codePointCount);
    }

    private static int weightPoints(int cp) {
        if (cp >= 0x3040 && cp <= 0x309F) {
            return 6;
        }
        if (cp >= 0x30A0 && cp <= 0x30FF) {
            return 6;
        }
        if (cp >= 0xFF65 && cp <= 0xFF9F) {
            return 6;
        }
        if (cp >= 0x3400 && cp <= 0x4DBF) {
            return 10;
        }
        if (cp >= 0x4E00 && cp <= 0x9FFF) {
            return 10;
        }
        if (cp >= 0xF900 && cp <= 0xFAFF) {
            return 10;
        }
        if (cp >= 0x20000 && cp <= 0x2A6DF) {
            return 10;
        }
        if (cp >= 0x2A700 && cp <= 0x2B738) {
            return 10;
        }
        if (cp >= 0x2B740 && cp <= 0x2B81D) {
            return 10;
        }
        if (cp >= 0x2B820 && cp <= 0x2CEA1) {
            return 10;
        }
        if (cp >= 0x2CEB0 && cp <= 0x2EBE0) {
            return 10;
        }
        if (cp >= 0x2F800 && cp <= 0x2FA1D) {
            return 10;
        }
        if (cp >= 0x30000 && cp <= 0x3134F) {
            return 10;
        }
        if (cp >= 0x31350 && cp <= 0x323AF) {
            return 10;
        }
        if (cp >= 0x323B0 && cp <= 0x352AF) {
            return 10;
        }
        return 4;
    }
}
