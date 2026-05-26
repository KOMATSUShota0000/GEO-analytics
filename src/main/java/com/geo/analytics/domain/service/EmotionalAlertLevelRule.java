package com.geo.analytics.domain.service;

import com.geo.analytics.domain.enums.EmotionalAlertLevel;

public final class EmotionalAlertLevelRule {

    private EmotionalAlertLevelRule() {}

    public static EmotionalAlertLevel classify(Double score) {
        if (score == null || Double.isNaN(score.doubleValue())) {
            return EmotionalAlertLevel.WARNING;
        }
        double v = score.doubleValue();
        if (Double.compare(v, 45.0d) < 0) {
            return EmotionalAlertLevel.DANGER;
        }
        if (Double.compare(v, 75.0d) < 0) {
            return EmotionalAlertLevel.WARNING;
        }
        return EmotionalAlertLevel.INFO;
    }
}
