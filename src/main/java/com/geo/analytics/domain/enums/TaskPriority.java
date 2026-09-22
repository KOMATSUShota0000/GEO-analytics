package com.geo.analytics.domain.enums;

/**
 * 改善タスクの効果の大きさ。
 *
 * <p>Why: 利用者には「S級」ではなく区別の中身（効果の大きさ）で見せる（#139）。表示名は画面側
 * （frontend の taskUtils）にもあり、AI に渡す改善タスク一覧（#141）と同じ言葉に揃える。
 */
public enum TaskPriority {
    S("効果 大"),
    A("効果 中"),
    B("効果 小");

    private final String label;

    TaskPriority(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
