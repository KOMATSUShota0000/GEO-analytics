package com.geo.analytics.domain.matching;

import java.lang.StrictMath;

public final class PhoneticBitComparator {

    private PhoneticBitComparator() {
    }

    public static double score(int intA, int intB) {
        int x = intA ^ intB;
        if ((x & 0xFFFF0000) == 0) {
            return StrictMath.abs(1.0);
        }
        if (((intA >>> 16) == (intB & 0xFFFF)) || ((intA & 0xFFFF) == (intB >>> 16))) {
            return StrictMath.abs(0.75);
        }
        if ((x & 0x0000FFFF) == 0) {
            return StrictMath.abs(0.5);
        }
        return StrictMath.abs(0.0);
    }
}
