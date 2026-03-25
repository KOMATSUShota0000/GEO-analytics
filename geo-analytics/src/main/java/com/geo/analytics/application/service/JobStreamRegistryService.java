package com.geo.analytics.application.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.infrastructure.config.StreamingExecutorConfig;
import com.geo.analytics.web.dto.StreamErrorPayload;
import com.geo.analytics.web.dto.VerifyStreamEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
@Service
public class JobStreamRegistryService {
    private static final Logger log = LoggerFactory.getLogger(JobStreamRegistryService.class);
    private static final long SSE_TIMEOUT_MILLIS = 600_000L;
    private static final long HEARTBEAT_INTERVAL_SECONDS = 30L;
    private final ConcurrentHashMap<UUID, JobSseRegistration> registrationsByJobId = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final ExecutorService streamDeliveryExecutor;
    private final ScheduledExecutorService heartbeatScheduler;
    public JobStreamRegistryService(
            ObjectMapper objectMapper,
            @Qualifier(StreamingExecutorConfig.STREAM_DELIVERY_VIRTUAL_EXECUTOR) ExecutorService streamDeliveryExecutor,
            @Qualifier(StreamingExecutorConfig.JOB_SSE_HEARTBEAT_SCHEDULER) ScheduledExecutorService heartbeatScheduler) {
        this.objectMapper = objectMapper;
        this.streamDeliveryExecutor = streamDeliveryExecutor;
        this.heartbeatScheduler = heartbeatScheduler;
    }
    public SseEmitter register(UUID jobId) {
        SseEmitter sseEmitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        JobSseRegistration newRegistration = new JobSseRegistration(sseEmitter);
        JobSseRegistration previousRegistration = registrationsByJobId.put(jobId, newRegistration);
        if (previousRegistration != null) {
            previousRegistration.closeQuietly();
        }
        ScheduledFuture<?> heartbeatFuture = heartbeatScheduler.scheduleAtFixedRate(
            () -> sendHeartbeat(jobId, sseEmitter),
            HEARTBEAT_INTERVAL_SECONDS,
            HEARTBEAT_INTERVAL_SECONDS,
            TimeUnit.SECONDS);
        newRegistration.setHeartbeatFuture(heartbeatFuture);
        Runnable detachRunnable = () -> detachRegistration(jobId, sseEmitter);
        sseEmitter.onCompletion(detachRunnable);
        sseEmitter.onTimeout(detachRunnable);
        sseEmitter.onError(throwable -> detachRunnable.run());
        return sseEmitter;
    }
    public void sendDelta(UUID jobId, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        dispatchChunk(jobId, new VerifyStreamEvent("delta", text, null));
    }
    public void sendDelta(UUID jobId, UUID queryId, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String queryIdString = queryId != null ? queryId.toString() : null;
        dispatchChunk(jobId, new VerifyStreamEvent("delta", text, queryIdString));
    }
    public void emitDone(UUID jobId, UUID queryId, String fullText) {
        String queryIdString = queryId != null ? queryId.toString() : null;
        String payload = fullText != null ? fullText : "";
        dispatchChunk(jobId, new VerifyStreamEvent("done", payload, queryIdString));
    }
    public void complete(UUID jobId) {
        JobSseRegistration registration = registrationsByJobId.remove(jobId);
        if (registration != null) {
            registration.cancelHeartbeat();
            try {
                SseEmitter sseEmitter = registration.sseEmitter();
                synchronized (sseEmitter) {
                    sseEmitter.complete();
                }
            } catch (Exception exception) {
                log.debug("sse complete failed jobId={}", jobId, exception);
            }
        }
    }
    public void failWithError(UUID jobId, Throwable throwable) {
        String message = throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getName();
        streamDeliveryExecutor.execute(() -> {
            JobSseRegistration registration = registrationsByJobId.get(jobId);
            if (registration == null) {
                return;
            }
            try {
                String jsonPayload = objectMapper.writeValueAsString(new StreamErrorPayload(message));
                SseEmitter sseEmitter = registration.sseEmitter();
                synchronized (sseEmitter) {
                    sseEmitter.send(SseEmitter.event().name("error").data(jsonPayload));
                }
            } catch (Exception exception) {
                log.warn("sse error event send failed jobId={}", jobId, exception);
            }
            if (registrationsByJobId.remove(jobId, registration)) {
                registration.cancelHeartbeat();
                try {
                    SseEmitter sseEmitter = registration.sseEmitter();
                    synchronized (sseEmitter) {
                        sseEmitter.complete();
                    }
                } catch (Exception exception) {
                    log.debug("sse complete after error failed jobId={}", jobId, exception);
                }
            }
        });
    }
    private void sendHeartbeat(UUID jobId, SseEmitter sseEmitter) {
        streamDeliveryExecutor.execute(() -> {
            JobSseRegistration registration = registrationsByJobId.get(jobId);
            if (registration == null || registration.sseEmitter() != sseEmitter || registration.isClosed()) {
                return;
            }
            try {
                synchronized (sseEmitter) {
                    sseEmitter.send(SseEmitter.event().comment("heartbeat"));
                }
            } catch (Exception exception) {
                log.debug("sse heartbeat failed jobId={}", jobId, exception);
                detachRegistration(jobId, sseEmitter);
            }
        });
    }
    private void detachRegistration(UUID jobId, SseEmitter sseEmitter) {
        registrationsByJobId.computeIfPresent(jobId, (key, registration) -> {
            if (registration.sseEmitter() != sseEmitter) {
                return registration;
            }
            registration.cancelHeartbeat();
            return null;
        });
    }
    private void dispatchChunk(UUID jobId, VerifyStreamEvent verifyStreamEvent) {
        streamDeliveryExecutor.execute(() -> {
            JobSseRegistration registration = registrationsByJobId.get(jobId);
            if (registration == null || registration.isClosed()) {
                return;
            }
            SseEmitter sseEmitter = registration.sseEmitter();
            try {
                String jsonPayload = objectMapper.writeValueAsString(verifyStreamEvent);
                synchronized (sseEmitter) {
                    sseEmitter.send(SseEmitter.event().name("chunk").data(jsonPayload));
                }
            } catch (Exception exception) {
                log.warn("sse chunk send failed jobId={}", jobId, exception);
                detachRegistration(jobId, sseEmitter);
            }
        });
    }
    private static final class JobSseRegistration {
        private final SseEmitter sseEmitter;
        private volatile ScheduledFuture<?> heartbeatFuture;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private JobSseRegistration(SseEmitter sseEmitter) {
            this.sseEmitter = sseEmitter;
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
        private void closeQuietly() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            cancelHeartbeat();
            try {
                synchronized (sseEmitter) {
                    sseEmitter.complete();
                }
            } catch (Exception ignored) {
            }
        }
    }
}
