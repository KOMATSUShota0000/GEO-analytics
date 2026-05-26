package com.geo.analytics.application.dto;

import java.util.List;

public record GiantFilterRawResult(List<Item> selected) {

    public record Item(
            String name,
            String websiteUrl,
            Double rating,
            Integer reviewCount,
            String selectionReason) {
    }
}
