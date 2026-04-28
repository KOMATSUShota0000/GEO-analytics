package com.geo.analytics.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.infrastructure.config.StreamingExecutorConfig;
import com.geo.analytics.web.dto.DebateOnboardingSseEvent;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class OnboardingDebateStreamRegistry {
    private static final Logger log = LoggerFactory.getLogger(OnboardingDebateStreamRegistry.class);
    private static final long SSE_TIMEOUT_MILLIS = 600_000L;
    private static final long HEARTBEAT_INTERVAL_SECONDS = 20L;
    private static final String SSE_EVENT_DEBATE = "debate";

    private final ConcurrentHashMap<OnboardingStreamKey, OnboardingSseRegistration> registrationsByKey =
            new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final ExecutorService streamDeliveryExecutor;
    private final ScheduledExecutorService heartbeatScheduler;

    public OnboardingDebateStreamRegistry(
            ObjectMapper objectMapper,
            @Qualifier(StreamingExecutorConfig.STREAM_DELIVERY_VIRTUAL_EXECUTOR) ExecutorService streamDeliveryExecutor,
            @Qualifier(StreamingExecutorConfig.JOB_SSE_HEARTBEAT_SCHEDULER)
                    ScheduledExecutorService heartbeatScheduler) {
        this.objectMapper = objectMapper;
        this.streamDeliveryExecutor = streamDeliveryExecutor;
        this.heartbeatScheduler = heartbeatScheduler;
    }

    public SseEmitter register(UUID projectId, UUID sessionId) {
        OnboardingStreamKey key = new OnboardingStreamKey(projectId, sessionId);
        SseEmitter sseEmitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        OnboardingSseRegistration newRegistration = new OnboardingSseRegistration(sseEmitter);
        OnboardingSseRegistration previousRegistration = registrationsByKey.put(key, newRegistration);
        if (previousRegistration != null) {
            previousRegistration.interruptWorkerAndClear();
            previousRegistration.closeQuietly();
        }
        ScheduledFuture<?> heartbeatFuture =
                heartbeatScheduler.scheduleAtFixedRate(
                        () -> sendHeartbeat(key, sseEmitter),
                        HEARTBEAT_INTERVAL_SECONDS,
                        HEARTBEAT_INTERVAL_SECONDS,
                        TimeUnit.SECONDS);
        newRegistration.setHeartbeatFuture(heartbeatFuture);
        Runnable detachRunnable = () -> detachRegistration(key, sseEmitter);
        sseEmitter.onCompletion(detachRunnable);
        sseEmitter.onTimeout(detachRunnable);
        sseEmitter.onError(throwable -> detachRunnable.run());
        return sseEmitter;
    }

    /**
     * SSE 登録が存在するときのみ、オンボーディング・ワーカー（仮想）スレッドを紐付ける。切断時に {@link
     * Thread#interrupt()} で協調停止するために使う。
     */
    public void bindWorkerThread(UUID projectId, UUID sessionId, Thread thread) {
        if (thread == null) {
            return;
        }
        OnboardingStreamKey key = new OnboardingStreamKey(projectId, sessionId);
        registrationsByKey.computeIfPresent(
                key,
                (ignored, registration) -> {
                    registration.bindWorkerThread(thread);
                    return registration;
                });
    }

    /**
     * 正常終了時にワーカー参照だけを外す（{@code expectedThread} と一致するときのみ）。interrupt は行わない。
     */
    public void clearWorkerThread(UUID projectId, UUID sessionId, Thread expectedThread) {
        if (expectedThread == null) {
            return;
        }
        OnboardingStreamKey key = new OnboardingStreamKey(projectId, sessionId);
        registrationsByKey.computeIfPresent(
                key,
                (ignored, registration) -> {
                    registration.clearWorkerThreadIf(expectedThread);
                    return registration;
                });
    }

    /**
     * 指定ストリームへ実況イベントを非同期配信する。登録がなければ何もしない。
     */
    public void sendEvent(UUID projectId, UUID sessionId, DebateOnboardingSseEvent event) {
        if (event == null) {
            return;
        }
        OnboardingStreamKey key = new OnboardingStreamKey(projectId, sessionId);
        streamDeliveryExecutor.execute(() -> dispatchDebateEvent(key, event));
    }

    private void dispatchDebateEvent(OnboardingStreamKey key, DebateOnboardingSseEvent event) {
        OnboardingSseRegistration registration = registrationsByKey.get(key);
        if (registration == null || registration.isClosed()) {
            return;
        }
        SseEmitter sseEmitter = registration.sseEmitter();
        try {
            String jsonPayload = objectMapper.writeValueAsString(event);
            registration.runWithSseLock(
                    () -> sseEmitter.send(SseEmitter.event().name(SSE_EVENT_DEBATE).data(jsonPayload)));
        } catch (Exception exception) {
            log.warn("onboarding sse debate send failed projectId={} sessionId={}", key.projectId(), key.sessionId(), exception);
            detachRegistration(key, sseEmitter);
        }
    }

    private void sendHeartbeat(OnboardingStreamKey key, SseEmitter sseEmitter) {
        streamDeliveryExecutor.execute(
                () -> {
                    OnboardingSseRegistration registration = registrationsByKey.get(key);
                    if (registration == null || registration.sseEmitter() != sseEmitter || registration.isClosed()) {
                        return;
                    }
                    try {
                        registration.runWithSseLock(
                                () -> sseEmitter.send(SseEmitter.event().comment("heartbeat")));
                    } catch (Exception exception) {
                        log.debug(
                                "onboarding sse heartbeat failed projectId={} sessionId={}",
                                key.projectId(),
                                key.sessionId(),
                                exception);
                        detachRegistration(key, sseEmitter);
                    }
                });
    }

    private void detachRegistration(OnboardingStreamKey key, SseEmitter sseEmitter) {
        registrationsByKey.computeIfPresent(
                key,
                (ignoredKey, registration) -> {
                    if (registration.sseEmitter() != sseEmitter) {
                        return registration;
                    }
                    registration.interruptWorkerAndClear();
                    registration.cancelHeartbeat();
                    return null;
                });
    }

    public void complete(UUID projectId, UUID sessionId) {
        OnboardingStreamKey key = new OnboardingStreamKey(projectId, sessionId);
        OnboardingSseRegistration registration = registrationsByKey.remove(key);
        if (registration != null) {
            registration.interruptWorkerAndClear();
            registration.cancelHeartbeat();
            try {
                registration.runWithSseLock(() -> registration.sseEmitter().complete());
            } catch (Exception exception) {
                log.debug("onboarding sse complete failed projectId={} sessionId={}", projectId, sessionId, exception);
            }
        }
    }

    private record OnboardingStreamKey(UUID projectId, UUID sessionId) {}

    private static final class OnboardingSseRegistration {
        private final SseEmitter sseEmitter;
        private final ReentrantLock sseLock = new ReentrantLock();
        private volatile ScheduledFuture<?> heartbeatFuture;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final AtomicReference<Thread> workerThread = new AtomicReference<>();

        private OnboardingSseRegistration(SseEmitter sseEmitter) {
            this.sseEmitter = sseEmitter;
        }

        private void bindWorkerThread(Thread thread) {
            workerThread.set(thread);
        }

        private void clearWorkerThreadIf(Thread expected) {
            workerThread.compareAndSet(expected, null);
        }

        private void interruptWorkerAndClear() {
            Thread thread = workerThread.getAndSet(null);
            if (thread != null) {
                thread.interrupt();
            }
        }

        private void setHeartbeatFuture(ScheduledFuture<?> future) {
            this.heartbeatFuture = future;
        }

        private SseEmitter sseEmitter() {
            return sseEmitter;
        }

        private boolean isClosed() {
            return closed.get();
        }

        private void cancelHeartbeat() {
            ScheduledFuture<?> future = heartbeatFuture;
            if (future != null) {
                future.cancel(false);
            }
        }

        private void runWithSseLock(SseIoRunnable runnable) throws Exception {
            sseLock.lock();
            try {
                runnable.run();
            } finally {
                sseLock.unlock();
            }
        }

        private void closeQuietly() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            interruptWorkerAndClear();
            cancelHeartbeat();
            try {
                runWithSseLock(() -> sseEmitter.complete());
            } catch (Exception ignored) {
            }
        }
    }

    @FunctionalInterface
    private interface SseIoRunnable {
        void run() throws Exception;
    }
}
