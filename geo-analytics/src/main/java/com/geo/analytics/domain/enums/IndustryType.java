package com.geo.analytics.domain.enums;

public enum IndustryType {
    YMYL("YMYL分野"),
    LOCAL("ローカルビジネス"),
    B2B("法人向け"),
    B2C("消費者向け"),
    EC("通販・EC"),
    OTHER("その他");
    private final String label;
    IndustryType(String label) {
        this.label = label;
    }
    public String getLabel() {
        return label;
    }
}
