package com.geo.analytics.domain.enums;
public enum PricingPlan {
    FREE(5L, 60L),
    STANDARD(30L, 600L),
    PRO(100L, 3000L),
    ENTERPRISE(500L, 20000L);
    private final long burstCapacity;
    private final long sustainedCapacity;
    PricingPlan(long burstCapacity, long sustainedCapacity) {
        this.burstCapacity = burstCapacity;
        this.sustainedCapacity = sustainedCapacity;
    }
    public long burstCapacity() {
        return burstCapacity;
    }
    public long sustainedCapacity() {
        return sustainedCapacity;
    }
}
