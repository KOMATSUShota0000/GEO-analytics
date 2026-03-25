package com.geo.analytics.domain.service;

import com.geo.analytics.domain.model.SomRawMetrics;

public final class SomScoreCalculator {
    private static final double LN2=Math.log(2.0);
    private SomScoreCalculator() {}
    public static double calculate(SomRawMetrics m) {
        double v=log2OnePlus(Math.max(0,m.tokenCount()));
        if(!m.isProAnalysis()) {
            return Math.clamp(v*10.0,0.0,70.0);
        }
        double a=m.rankPosition()<=0?0.1:Math.pow(0.5,m.rankPosition()-1);
        double s=1.0+Math.clamp(m.sentimentIntensity(),-1.0,1.0)*0.5;
        return Math.clamp(v*a*s*10.0,0.0,100.0);
    }
    private static double log2OnePlus(int tokenCount) {
        return Math.log1p(tokenCount)/LN2;
    }
}
