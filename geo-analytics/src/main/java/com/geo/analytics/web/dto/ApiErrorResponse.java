package com.geo.analytics.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Map;

/**
 * API エラーの統一 JSON 形式（フロントエンドは {@code error_code} で分岐する）。
 *
 * <p>フィールド名は JSON 上で snake_case（{@code error_code}, {@code message}, {@code details}）となる。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ApiErrorResponse(String errorCode, String message, Map<String, Object> details) {

    public static ApiErrorResponse of(String errorCode, String message) {
        return new ApiErrorResponse(errorCode, message, null);
    }

    public static ApiErrorResponse of(String errorCode, String message, Map<String, Object> details) {
        return new ApiErrorResponse(errorCode, message, details);
    }
}
