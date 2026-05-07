package com.geo.analytics.web.exception;

import com.geo.analytics.domain.exception.AccountDisabledException;
import com.geo.analytics.domain.exception.AiAnalysisTimeoutException;
import com.geo.analytics.domain.exception.CredentialsRevokedException;
import com.geo.analytics.domain.exception.ForbiddenApiException;
import com.geo.analytics.domain.exception.InsufficientQuotaException;
import com.geo.analytics.domain.exception.RateLimitExceededException;
import com.geo.analytics.domain.exception.TaskLockedException;
import com.geo.analytics.domain.exception.TaskRegenerationTooManyRequestsException;
import com.geo.analytics.domain.exception.SessionRevokedException;
import com.geo.analytics.domain.exception.SystemMaintenanceException;
import com.geo.analytics.domain.exception.TenantSuspendedException;
import com.geo.analytics.domain.exception.ThresholdExceededException;
import com.geo.analytics.domain.exception.TokenExpiredException;
import com.geo.analytics.domain.exception.UnauthenticatedApiException;
import com.geo.analytics.domain.exception.VersionMismatchException;
import com.geo.analytics.infrastructure.lock.PostgresDistributedLockManager.LockAcquisitionException;
import com.geo.analytics.application.exception.QueryProposalException;
import com.geo.analytics.application.exception.QueryProposalPhase;
import com.geo.analytics.infrastructure.persistence.JsonbSerializationException;
import com.geo.analytics.web.dto.ApiErrorResponse;
import jakarta.persistence.EntityNotFoundException;
import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String UNKNOWN_USER_MESSAGE = "予期しないエラーが発生しました。";

    private static ResponseEntity<ApiErrorResponse> json(HttpStatus status, ApiErrorResponse body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    private static ResponseEntity<ApiErrorResponse> json(HttpStatus status, HttpHeaders headers, ApiErrorResponse body) {
        return ResponseEntity.status(status).headers(headers).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<ApiErrorResponse> handleTokenExpired(TokenExpiredException exception) {
        logger.warn("Token expired: {}", exception.getMessage());
        return json(HttpStatus.UNAUTHORIZED, ApiErrorResponse.of("token_expired", exception.getMessage()));
    }

    @ExceptionHandler(SessionRevokedException.class)
    public ResponseEntity<ApiErrorResponse> handleSessionRevoked(SessionRevokedException exception) {
        logger.warn("Session revoked: {}", exception.getMessage());
        return json(HttpStatus.UNAUTHORIZED, ApiErrorResponse.of("session_revoked", exception.getMessage()));
    }

    @ExceptionHandler(CredentialsRevokedException.class)
    public ResponseEntity<ApiErrorResponse> handleCredentialsRevoked(CredentialsRevokedException exception) {
        logger.warn("Credentials revoked: {}", exception.getMessage());
        return json(HttpStatus.UNAUTHORIZED, ApiErrorResponse.of("credentials_revoked", exception.getMessage()));
    }

    @ExceptionHandler(UnauthenticatedApiException.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthenticatedApi(UnauthenticatedApiException exception) {
        logger.debug("Unauthenticated: {}", exception.getMessage());
        return json(HttpStatus.UNAUTHORIZED, ApiErrorResponse.of("unauthorized", exception.getMessage()));
    }

    @ExceptionHandler(ForbiddenApiException.class)
    public ResponseEntity<ApiErrorResponse> handleForbiddenApi(ForbiddenApiException exception) {
        logger.warn("Forbidden: {}", exception.getMessage());
        return json(HttpStatus.FORBIDDEN, ApiErrorResponse.of("forbidden", exception.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleSpringAccessDenied(AccessDeniedException exception) {
        logger.warn("Access denied", exception);
        return json(HttpStatus.FORBIDDEN, ApiErrorResponse.of("forbidden", "この操作を行う権限がありません。"));
    }

    @ExceptionHandler(AccountDisabledException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountDisabled(AccountDisabledException exception) {
        logger.warn("Account disabled: {}", exception.getMessage());
        return json(HttpStatus.FORBIDDEN, ApiErrorResponse.of("account_disabled", exception.getMessage()));
    }

    @ExceptionHandler(TenantSuspendedException.class)
    public ResponseEntity<ApiErrorResponse> handleTenantSuspended(TenantSuspendedException exception) {
        logger.warn("Tenant suspended: {}", exception.getMessage());
        return json(HttpStatus.FORBIDDEN, ApiErrorResponse.of("tenant_suspended", exception.getMessage()));
    }

    @ExceptionHandler(SystemMaintenanceException.class)
    public ResponseEntity<ApiErrorResponse> handleSystemMaintenance(SystemMaintenanceException exception) {
        logger.warn("Maintenance: {}", exception.getMessage());
        return json(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorResponse.of("maintenance", exception.getMessage()));
    }

    @ExceptionHandler(VersionMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleVersionMismatch(VersionMismatchException exception) {
        logger.warn("Version mismatch: {}", exception.getMessage());
        return json(HttpStatus.UPGRADE_REQUIRED, ApiErrorResponse.of("version_mismatch", exception.getMessage()));
    }

    @ExceptionHandler(LockAcquisitionException.class)
    public ResponseEntity<ApiErrorResponse> handleLockAcquisition(LockAcquisitionException exception) {
        logger.warn("Advisory lock not acquired: {}", exception.getMessage());
        return json(
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorResponse.of("system_busy", "ただいま混み合っています。しばらくしてから再度お試しください。"));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiErrorResponse> handleSecurityException(SecurityException exception) {
        logger.error("Security violation (e.g. RLS)", exception);
        return json(HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorResponse.of("unknown", UNKNOWN_USER_MESSAGE));
    }

    @ExceptionHandler(ThresholdExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleThresholdExceeded(ThresholdExceededException exception) {
        int threshold = exception.getThreshold();
        String message = "キーワードの上限を超えています。" + threshold + "件以下のキーワード数にしてください";
        logger.warn("Threshold exceeded threshold={}", threshold);
        return json(
                HttpStatus.FORBIDDEN,
                ApiErrorResponse.of(
                        "threshold_exceeded",
                        message,
                        Map.of("threshold", threshold)));
    }

    @ExceptionHandler(InsufficientQuotaException.class)
    public ResponseEntity<ApiErrorResponse> handleInsufficientQuota(InsufficientQuotaException exception) {
        logger.warn("Insufficient quota plan={} limit={}", exception.getPlanName(), exception.getCurrentLimit());
        return json(
                HttpStatus.FORBIDDEN,
                ApiErrorResponse.of(
                        "insufficient_quota",
                        exception.getMessage(),
                        Map.of(
                                "current_limit", exception.getCurrentLimit(),
                                "plan_name", exception.getPlanName())));
    }

    @ExceptionHandler(AiAnalysisTimeoutException.class)
    public ResponseEntity<ApiErrorResponse> handleAiAnalysisTimeout(AiAnalysisTimeoutException exception) {
        logger.warn("AI analysis timeout", exception);
        return json(
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorResponse.of("ai_analysis_timeout", exception.getMessage()));
    }

    @ExceptionHandler(TaskLockedException.class)
    public ResponseEntity<ApiErrorResponse> handleTaskLocked(TaskLockedException exception) {
        logger.warn("task_locked");
        return json(
                HttpStatus.FORBIDDEN,
                ApiErrorResponse.of(
                        "task_locked", "このタスクはロックされているため再生成できません"));
    }

    @ExceptionHandler(TaskRegenerationTooManyRequestsException.class)
    public ResponseEntity<ApiErrorResponse> handleTaskRegenerationTooMany(
            TaskRegenerationTooManyRequestsException exception) {
        logger.warn("Task regeneration rate limit: {}", exception.getMessage());
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "60");
        return json(
                HttpStatus.TOO_MANY_REQUESTS,
                headers,
                ApiErrorResponse.of(
                        "too_many_requests",
                        "制限に達しました。1分後に再度お試しください。"));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimitExceeded(RateLimitExceededException exception) {
        long nanos = Math.max(0L, exception.getProbe().getNanosToWaitForRefill());
        Duration wait = Duration.ofNanos(nanos);
        long retryAfterSeconds = nanos <= 0L ? 0L : (nanos + 999_999_999L) / 1_000_000_000L;
        long hours = wait.toHours();
        long minutes = wait.minusHours(hours).toMinutes();
        String message = "解析枠の残高が不足しています。回復まで約" + hours + "時間" + minutes + "分です。";
        logger.warn("Rate limit exceeded plan={}", exception.getPlanName());
        return json(
                HttpStatus.TOO_MANY_REQUESTS,
                ApiErrorResponse.of(
                        "rate_limit_exceeded",
                        message,
                        Map.of(
                                "current_limit", exception.getCurrentLimit(),
                                "plan_name", exception.getPlanName(),
                                "retry_after_seconds", retryAfterSeconds)));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException exception) {
        logger.warn("Authentication failed", exception);
        return json(
                HttpStatus.UNAUTHORIZED,
                ApiErrorResponse.of("invalid_credentials", "認証に失敗しました。"));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleEntityNotFound(EntityNotFoundException exception) {
        logger.warn("Resource not found", exception);
        return json(HttpStatus.NOT_FOUND, ApiErrorResponse.of("not_found", exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException exception) {
        logger.warn("Invalid state transition", exception);
        return json(HttpStatus.CONFLICT, ApiErrorResponse.of("conflict", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
        logger.warn("Invalid argument", exception);
        return json(HttpStatus.BAD_REQUEST, ApiErrorResponse.of("invalid_argument", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationFailure(MethodArgumentNotValidException exception) {
        logger.warn("Request validation failed", exception);
        Map<String, String> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fieldError ->
                                fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "Invalid value",
                        (existingMessage, duplicateMessage) -> existingMessage));
        return json(
                HttpStatus.BAD_REQUEST,
                ApiErrorResponse.of("validation_failed", "入力内容を確認してください。", Map.of("fields", fieldErrors)));
    }

    @ExceptionHandler(HttpMessageNotWritableException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpMessageNotWritable(HttpMessageNotWritableException exception) {
        logger.debug(exception.getMessage());
        return json(
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorResponse.of("serialization_unavailable", "レスポンスの生成に失敗しました。"));
    }

    @ExceptionHandler(JsonbSerializationException.class)
    public ResponseEntity<ApiErrorResponse> handleJsonbSerializationFailure(JsonbSerializationException exception) {
        logger.error("AI response JSON parse failure", exception);
        return json(
                HttpStatus.BAD_GATEWAY,
                ApiErrorResponse.of("ai_response_parse_failure", "AI の応答を解釈できませんでした。"));
    }

    @ExceptionHandler({CannotGetJdbcConnectionException.class, SQLTransientConnectionException.class})
    public ResponseEntity<ApiErrorResponse> handleConnectionPoolExhausted(Exception exception) {
        logger.warn("Database connection pool exhausted", exception);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.RETRY_AFTER, "5");
        return json(
                HttpStatus.SERVICE_UNAVAILABLE,
                headers,
                ApiErrorResponse.of(
                        "database_unavailable",
                        "データベースへの接続が混み合っています。しばらくしてから再度お試しください。"));
    }

    @ExceptionHandler(QueryProposalException.class)
    public ResponseEntity<ApiErrorResponse> handleQueryProposal(QueryProposalException exception) {
        QueryProposalPhase phase = exception.getPhase();
        final HttpStatus status;
        final String errorCode;
        if (phase == QueryProposalPhase.VALIDATION) {
            status = HttpStatus.UNPROCESSABLE_ENTITY;
            errorCode = "query_proposal_validation_failed";
        } else if (phase == QueryProposalPhase.SCRAPING) {
            status = HttpStatus.UNPROCESSABLE_ENTITY;
            errorCode = "query_proposal_scraping_failed";
        } else if (phase == QueryProposalPhase.AI_ANALYSIS) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            errorCode = "query_proposal_ai_failed";
        } else {
            throw new IllegalStateException("Unexpected QueryProposalPhase: " + phase);
        }
        String detail = exception.getDetail();
        if (detail == null || detail.isBlank()) {
            detail = exception.getUserMessage();
        }
        Map<String, Object> details = Map.of("phase", phase.name(), "detail", detail);
        if (phase == QueryProposalPhase.AI_ANALYSIS) {
            logger.error(
                    "Query proposal AI phase failed errorCode={} message={}",
                    errorCode,
                    exception.getUserMessage(),
                    exception);
        } else {
            logger.warn(
                    "Query proposal failed phase={} errorCode={} message={}",
                    phase.name(),
                    errorCode,
                    exception.getUserMessage(),
                    exception.getCause());
        }
        return json(status, ApiErrorResponse.of(errorCode, exception.getUserMessage(), details));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String detail = exception.getReason() != null ? exception.getReason() : status.getReasonPhrase();
        if (status.is5xxServerError()) {
            logger.error("Response status exception status={} detail={}", status.value(), detail, exception);
        } else {
            logger.warn("Response status exception status={} detail={}", status.value(), detail);
        }
        return json(
                status,
                ApiErrorResponse.of(
                        "response_status",
                        detail,
                        Map.of("http_status", status.value())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception exception) {
        logger.error("Unhandled exception", exception);
        return json(HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorResponse.of("unknown", UNKNOWN_USER_MESSAGE));
    }
}
