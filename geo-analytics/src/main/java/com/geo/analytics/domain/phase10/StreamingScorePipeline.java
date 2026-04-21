package com.geo.analytics.domain.phase10;

public interface StreamingScorePipeline {

    void acceptScore(String brandId, boolean isSpike, double slabScore);
}
