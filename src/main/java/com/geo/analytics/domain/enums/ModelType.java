package com.geo.analytics.domain.enums;

public enum ModelType {
    GEMINI,
    CHATGPT,
    CLAUDE;
    public int quotaMultiplier() {
        return switch (this) {
            case GEMINI -> 1;
            case CHATGPT -> 2;
            case CLAUDE -> 3;
        };
    }
}
