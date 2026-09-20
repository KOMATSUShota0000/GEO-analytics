package com.geo.analytics.domain.model;

import com.geo.analytics.domain.enums.TaskCategory;
import com.geo.analytics.domain.enums.TaskPriority;
import java.util.UUID;

/**
 * 改善タスク。
 *
 * @param rationale なぜ効くか（GEO 露出・引用への作用）。根拠のない提案を出さないために必須で持たせる（#79）
 * @param evidence  どこが根拠か（ルーブリック監査の所見や AI 回答の該当箇所）。`.cursorrules` の
 *                  Cite Before You Speak と同じ考え方。古いデータには存在しないため null を許容する
 */
public record RemediationTask(
        UUID id,
        TaskCategory category,
        TaskPriority priority,
        String title,
        String content,
        double impactScore,
        String rationale,
        String evidence) {

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
