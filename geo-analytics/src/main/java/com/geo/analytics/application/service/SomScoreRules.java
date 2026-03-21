package com.geo.analytics.application.service;

public final class SomScoreRules {
    private SomScoreRules() {
    }

    public static double computeFromCitationRank(Integer mentionRank, Boolean brandMentioned) {
        if (!Boolean.TRUE.equals(brandMentioned)) {
            return 0.0;
        }
        if (mentionRank == null || mentionRank <= 0) {
            return 0.0;
        }
        if (mentionRank == 1) {
            return 1.0;
        }
        if (mentionRank == 2) {
            return 0.5;
        }
        return 0.2;
    }
}
