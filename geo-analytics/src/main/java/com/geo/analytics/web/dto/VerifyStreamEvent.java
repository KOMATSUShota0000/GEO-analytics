package com.geo.analytics.web.dto;
public record VerifyStreamEvent(String kind, String text, String queryId) {
    public VerifyStreamEvent(String kind, String text) {
        this(kind, text, null);
    }
}