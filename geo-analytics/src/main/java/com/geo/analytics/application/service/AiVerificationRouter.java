package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.VerificationRequest;
import com.geo.analytics.application.dto.VerificationResponse;
import com.geo.analytics.application.port.AiVerificationPort;
import com.geo.analytics.application.port.ModelTypedAiVerificationPort;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.infrastructure.config.AiConfig;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Objects;
import java.util.concurrent.Semaphore;

/**
 * リクエスト単位で単一のアダプタ（実運用では Gemini のみ）を順次呼び出す。複数モデル並列競合は行わない。
 */
public final class AiVerificationRouter implements AiVerificationPort {
    private final ModelTypedAiVerificationPort verificationAdapter;
    private final Semaphore concurrencyLimiter;

    public AiVerificationRouter(
            ModelTypedAiVerificationPort verificationAdapter,
            @Qualifier(AiConfig.AI_VERIFICATION_CONCURRENCY_LIMITER) Semaphore concurrencyLimiter) {
        this.verificationAdapter = Objects.requireNonNull(verificationAdapter);
        this.concurrencyLimiter = Objects.requireNonNull(concurrencyLimiter);
    }

    @Override
    public VerificationResponse verify(VerificationRequest verificationRequest) {
        SubscriptionPlan plan =
                Objects.requireNonNullElse(verificationRequest.subscriptionPlan(), SubscriptionPlan.STANDARD);
        if (!plan.hasModelAccess(verificationAdapter.modelType())) {
            throw new IllegalStateException("plan does not allow verification model: " + plan);
        }
        try {
            concurrencyLimiter.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        try {
            return verificationAdapter.verify(verificationRequest);
        } finally {
            concurrencyLimiter.release();
        }
    }
}
