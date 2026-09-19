package com.geo.analytics.domain.enums;

/**
 * 検証に使った材料の出どころ。
 *
 * <p>Why: 実測の AI Overview が取れたクエリと、取れずに LLM 生成の回答で補完したクエリでは、同じ数字でも
 * 根拠の強さが違う（ADR-039）。経路によって材料が変わるのに記録が同じだと後から区別できないため、
 * 行ごとに残す。表示での区別は #93 で行う。
 */
public enum MaterialSource {
    /** 実測の AI Overview 本文を材料にした。 */
    MEASURED,
    /** AI Overview が取れず、LLM が生成した回答文を材料にした。 */
    ESTIMATED
}
