package com.geo.analytics.domain.ai;

public enum DebatePersona {
    ANALYST(0.1, "情報の番人"),
    SKEPTIC(0.4, "毒舌な競合"),
    INNOVATOR(0.8, "思考代弁者"),
    DIRECTOR(0.2, "目利き・オーケストレーター");
    private final double recommendedTemperature;
    private final String label;
    DebatePersona(double recommendedTemperature, String label) {
        this.recommendedTemperature = recommendedTemperature;
        this.label = label;
    }
    public double getRecommendedTemperature() {
        return recommendedTemperature;
    }
    public String getLabel() {
        return label;
    }
}
