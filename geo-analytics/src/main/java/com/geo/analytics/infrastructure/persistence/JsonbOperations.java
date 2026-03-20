package com.geo.analytics.infrastructure.persistence;

public interface JsonbOperations {
    String serialize(Object value);

    <T> T deserialize(String rawJson, Class<T> targetType);
}
