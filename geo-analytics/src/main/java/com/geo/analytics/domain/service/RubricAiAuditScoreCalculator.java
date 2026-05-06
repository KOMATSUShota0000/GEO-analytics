package com.geo.analytics.domain.service;

import com.geo.analytics.application.dto.RubricAuditResult;
import com.geo.analytics.application.dto.RubricItemAudit;
import com.geo.analytics.domain.enums.RubricVerdictStatus;
import java.lang.StrictMath;
import java.util.List;

public final class RubricAiAuditScoreCalculator {
    private static final double RAW_YES = 2.5d;
    private static final double RAW_PARTIAL = 1.0d;
    private static final double SCALE_TO_FIFTY = smDiv(50.0d, 25.0d);

    private RubricAiAuditScoreCalculator() {}

    private static double smAdd(double a, double b) {
        return StrictMath.fma(1.0d, a, b);
    }

    private static double smMul(double a, double b) {
        return StrictMath.fma(a, b, 0.0d);
    }

    private static double smDiv(double a, double b) {
        return StrictMath.fma(a, 1.0d / b, 0.0d);
    }

    public static double scoreAiAudit(RubricAuditResult result) {
        if (result == null) {
            return 0.0d;
        }
        List<RubricItemAudit> items = result.items();
        double sum = 0.0d;
        for (int i = 0; i < items.size(); i++) {
            RubricItemAudit item = items.get(i);
            RubricVerdictStatus st = item.status();
            if (st == RubricVerdictStatus.YES) {
                sum = smAdd(sum, RAW_YES);
            } else if (st == RubricVerdictStatus.PARTIAL) {
                sum = smAdd(sum, RAW_PARTIAL);
            }
        }
        return smMul(sum, SCALE_TO_FIFTY);
    }
}
