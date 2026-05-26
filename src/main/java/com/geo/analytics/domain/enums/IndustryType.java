package com.geo.analytics.domain.enums;

public enum IndustryType {
    YMYL("YMYL分野", "病院 クリニック"),
    LOCAL("ローカルビジネス", "店舗"),
    B2B("法人向け", "企業"),
    B2C("消費者向け", "サービス"),
    EC("通販・EC", "通販"),
    OTHER("その他", "");

    private final String label;
    private final String searchLabel;

    IndustryType(String label, String searchLabel) {
        this.label = label;
        this.searchLabel = searchLabel;
    }

    public String getLabel() {
        return label;
    }

    public String getSearchLabel() {
        return searchLabel;
    }
}
