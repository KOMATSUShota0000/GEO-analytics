package com.geo.analytics.application.port;

import com.geo.analytics.application.dto.SgeMentionResult;

public interface SgeMeasurementPort {
    SgeMentionResult checkSgeMention(String query, String brandName);
}
