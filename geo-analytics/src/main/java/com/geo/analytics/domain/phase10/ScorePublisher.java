package com.geo.analytics.domain.phase10;

public interface ScorePublisher {

    void publishSnapshot(
            String brandId,
            double expectedShareOfModel,
            long processedCount,
            long nPlanned,
            double progress);

    void publishObservation(String brandId, boolean isSpike, double slabScore, boolean purged);
}
