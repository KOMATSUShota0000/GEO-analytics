package com.geo.analytics.infrastructure.ai;

import com.geo.analytics.application.dto.VerificationRequest;
import com.geo.analytics.application.dto.VerificationResponse;
import com.geo.analytics.application.port.ModelTypedAiVerificationPort;
import com.geo.analytics.domain.enums.ModelType;

import java.util.LinkedHashMap;

public final class ForwardingModelAdapter implements ModelTypedAiVerificationPort {
    private final ModelType modelType;
    private final GeminiVerificationAdapter delegate;

    public ForwardingModelAdapter(ModelType modelType, GeminiVerificationAdapter delegate) {
        this.modelType = modelType;
        this.delegate = delegate;
    }

    @Override
    public VerificationResponse verify(VerificationRequest verificationRequest) {
        var v = delegate.verify(verificationRequest);
        return new VerificationResponse(
                modelType,
                v.rawResponseJson(),
                v.somScore(),
                v.brandMentioned(),
                v.mentionRank(),
                v.overallScore(),
                v.tokenCount(),
                v.aiCitationPosition(),
                v.sentimentIntensity(),
                v.resolvedEntityLabel(),
                v.visibilityStage(),
                v.modifiedZScore(),
                v.calculationVersion(),
                v.competitorResults(),
                new LinkedHashMap<>(),
                v.gbvsNormalizedScore());
    }

    @Override
    public ModelType modelType() {
        return modelType;
    }
}
