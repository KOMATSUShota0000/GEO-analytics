package com.geo.analytics.domain.model;

import com.geo.analytics.domain.enums.TaskPriority;

public record RemediationPriorityLevel(Integer level, Double requiredScoreThreshold) {

    public static RemediationPriorityLevel forPriority(TaskPriority p) {
        return switch (p) {
            case B -> new RemediationPriorityLevel(1, 0.0);
            case A -> new RemediationPriorityLevel(2, 60.0);
            case S -> new RemediationPriorityLevel(3, 80.0);
        };
    }
}
