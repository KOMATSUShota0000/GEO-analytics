package com.geo.analytics.domain.phase10;

public final class ScoreCombinerBuffer {

    private long deltaCount;
    private double deltaSum;
    private final int capacity;
    private int currentSize;

    public ScoreCombinerBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException();
        }
        this.capacity = capacity;
    }

    public void add(boolean isPurged, boolean isSpike, double slabScore) {
        if (isPurged) {
            return;
        }
        if (isSpike) {
            deltaCount++;
            currentSize++;
            return;
        }
        if (!Double.isFinite(slabScore) || slabScore < 0.0d) {
            throw new IllegalArgumentException();
        }
        deltaCount++;
        currentSize++;
        deltaSum += slabScore;
    }

    public boolean isFull() {
        return currentSize >= capacity;
    }

    public void reset() {
        deltaCount = 0L;
        deltaSum = 0.0d;
        currentSize = 0;
    }

    public long deltaCount() {
        return deltaCount;
    }

    public double deltaSum() {
        return deltaSum;
    }
}
