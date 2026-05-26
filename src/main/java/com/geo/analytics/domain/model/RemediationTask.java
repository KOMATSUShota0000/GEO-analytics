package com.geo.analytics.domain.model;

import com.geo.analytics.domain.enums.TaskCategory;
import com.geo.analytics.domain.enums.TaskPriority;
import java.util.UUID;

public record RemediationTask(
        UUID id,
        TaskCategory category,
        TaskPriority priority,
        String title,
        String content,
        double impactScore) {

    public RemediationTask {
        if (id == null) {
            throw new IllegalArgumentException("id");
        }
        if (category == null) {
            throw new IllegalArgumentException("category");
        }
        if (priority == null) {
            throw new IllegalArgumentException("priority");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title");
        }
        if (content == null) {
            throw new IllegalArgumentException("content");
        }
        if (Double.isNaN(impactScore) || Double.isInfinite(impactScore)) {
            throw new IllegalArgumentException("impactScore");
        }
    }
}
