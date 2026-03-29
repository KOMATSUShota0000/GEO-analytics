package com.geo.analytics.application.dto;

public record PdfWhiteLabelInjection(String brandColor, String logoUrl, String brandName, String pdfContextJson) {
    public PdfWhiteLabelInjection(String brandColor, String logoUrl, String brandName) {
        this(brandColor, logoUrl, brandName, "");
    }
}
