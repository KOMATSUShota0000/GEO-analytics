package com.geo.analytics.web.dto;

import java.util.ArrayList;
import java.util.List;

public final class RemediationTaskResponseMask {

    private RemediationTaskResponseMask() {
    }

    public static List<RemediationTaskResponse> apply(Double factBasedScore, List<RemediationTaskResponse> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return tasks == null ? List.of() : List.copyOf(tasks);
        }
        ArrayList<RemediationTaskResponse> out = new ArrayList<>(tasks.size());
        for (RemediationTaskResponse task : tasks) {
            if (task == null) {
                out.add(null);
                continue;
            }
            Double threshold = task.requiredScoreThreshold();
            if (threshold != null && threshold <= 0.0) {
                out.add(task);
                continue;
            }
            if (factBasedScore == null || Double.isNaN(factBasedScore.doubleValue())) {
                out.add(maskedCopy(task));
                continue;
            }
            if (threshold == null) {
                out.add(maskedCopy(task));
                continue;
            }
            if (factBasedScore.doubleValue() < threshold.doubleValue()) {
                out.add(maskedCopy(task));
            } else {
                out.add(unmaskedCopy(task));
            }
        }
        return List.copyOf(out);
    }

    private static RemediationTaskResponse maskedCopy(RemediationTaskResponse task) {
        String content = lockMessage(task.requiredScoreThreshold());
        return new RemediationTaskResponse(
                task.id(),
                task.category(),
                task.priority(),
                task.title(),
                content,
                task.impactScore(),
                task.level(),
                task.requiredScoreThreshold(),
                true);
    }

    private static RemediationTaskResponse unmaskedCopy(RemediationTaskResponse task) {
        return new RemediationTaskResponse(
                task.id(),
                task.category(),
                task.priority(),
                task.title(),
                task.content(),
                task.impactScore(),
                task.level(),
                task.requiredScoreThreshold(),
                false);
    }

    private static String lockMessage(Double threshold) {
        return "🔒 基礎スコア " + formatThreshold(threshold) + "点 到達で解放されます";
    }

    private static String formatThreshold(Double threshold) {
        if (threshold == null) {
            return "";
        }
        double v = threshold.doubleValue();
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return String.valueOf(v);
        }
        if (v == Math.floor(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }
}
