package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.VerificationRequest;
import com.geo.analytics.application.dto.VerificationResponse;
import com.geo.analytics.application.port.AiVerificationPort;
import com.geo.analytics.application.port.ModelTypedAiVerificationPort;
import com.geo.analytics.domain.exception.AiAnalysisTimeoutException;
import com.geo.analytics.domain.service.InformationTheoryBasedAggregator;
import org.springframework.beans.factory.annotation.Qualifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.StructuredTaskScope;

public final class AiVerificationRouter implements AiVerificationPort {
    private final List<ModelTypedAiVerificationPort> adapters;
    private final InformationTheoryBasedAggregator aggregator;
    private final Semaphore concurrencyLimiter;

    public AiVerificationRouter(
            List<ModelTypedAiVerificationPort> adapters,
            InformationTheoryBasedAggregator aggregator,
            @Qualifier("aiVerificationConcurrencyLimiter") Semaphore concurrencyLimiter) {
        this.adapters = List.copyOf(adapters);
        this.aggregator = Objects.requireNonNull(aggregator);
        this.concurrencyLimiter = Objects.requireNonNull(concurrencyLimiter);
    }

    @Override
    public VerificationResponse verify(VerificationRequest verificationRequest) {
        var plan = verificationRequest.subscriptionPlan();
        var active = adapters.stream().filter(a -> plan.hasModelAccess(a.modelType())).toList();
        if (active.isEmpty()) {
            throw new IllegalStateException("no adapters for plan");
        }
        if (active.size() == 1) {
            return active.getFirst().verify(verificationRequest);
        }
        try (StructuredTaskScope<VerificationResponse, Void> scope = StructuredTaskScope.open(
                StructuredTaskScope.Joiner.<VerificationResponse>awaitAll(),
                cf -> cf.withTimeout(Duration.ofSeconds(30))
                        .withThreadFactory(Thread.ofVirtual().name("ai-verify-", 0).factory()))) {
            var tasks = new ArrayList<StructuredTaskScope.Subtask<VerificationResponse>>();
            for (var adapter : active) {
                tasks.add(scope.fork(() -> {
                    concurrencyLimiter.acquire();
                    try {
                        return adapter.verify(verificationRequest);
                    } finally {
                        concurrencyLimiter.release();
                    }
                }));
            }
            try {
                scope.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } catch (StructuredTaskScope.TimeoutException _) {
            }
            var ok = new ArrayList<VerificationResponse>();
            for (var t : tasks) {
                if (t.state() == StructuredTaskScope.Subtask.State.SUCCESS) {
                    try {
                        ok.add(t.get());
                    } catch (Exception _) {
                    }
                }
            }
            if (ok.isEmpty()) {
                throw new AiAnalysisTimeoutException();
            }
            return aggregator.aggregate(ok, verificationRequest);
        }
    }
}
