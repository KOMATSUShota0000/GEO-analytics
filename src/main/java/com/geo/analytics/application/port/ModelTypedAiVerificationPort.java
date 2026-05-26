package com.geo.analytics.application.port;

import com.geo.analytics.domain.enums.ModelType;

public interface ModelTypedAiVerificationPort extends AiVerificationPort {
    ModelType modelType();
}
