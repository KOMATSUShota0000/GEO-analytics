package com.geo.analytics.domain.enums;

/**
 * 改善タスクにかかる時間の区別。
 *
 * <p>Why: 利用者には「Spike/Slab」ではなく区別の中身（かかる時間）で見せる（#139）。表示名は画面側
 * （frontend の taskUtils）にもあり、AI に渡す改善タスク一覧（#141）と同じ言葉に揃える。
 */
public enum TaskCategory {
    SPIKE("すぐ直せる"),
    SLAB("時間がかかる");

    private final String label;

    TaskCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
