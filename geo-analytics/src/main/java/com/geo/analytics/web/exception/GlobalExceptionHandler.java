package com.geo.analytics.web.exception;

import com.geo.analytics.domain.exception.AiAnalysisTimeoutException;
import com.geo.analytics.domain.exception.InsufficientQuotaException;
import com.geo.analytics.domain.exception.RateLimitExceededException;
import com.geo.analytics.domain.exception.ThresholdExceededException;
import com.geo.analytics.infrastructure.persistence.JsonbSerializationException;
import com.geo.analytics.web.dto.ErrorResponse;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ThresholdExceededException.class)
    public ResponseEntity<ErrorResponse> handleThresholdExceeded(ThresholdExceededException exception) {
        var threshold = exception.getThreshold();
        var message =
                "キーワードの上限を超えています。" + threshold + "件以下のキーワード数にしてください";
        logger.warn("Threshold exceeded threshold={}", threshold);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse("THRESHOLD_EXCEEDED", message, threshold, null, null));
    }

    @ExceptionHandler(InsufficientQuotaException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientQuota(InsufficientQuotaException exception) {
        logger.warn("Insufficient quota plan={} limit={}", exception.getPlanName(), exception.getCurrentLimit());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(
                        "INSUFFICIENT_QUOTA",
                        exception.getMessage(),
                        exception.getCurrentLimit(),
                        exception.getPlanName(),
                        null));
    }

    @ExceptionHandler(AiAnalysisTimeoutException.class)
    public ResponseEntity<ProblemDetail> handleAiAnalysisTimeout(AiAnalysisTimeoutException exception) {
        logger.warn("AI analysis timeout", exception);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        problemDetail.setTitle("AI Analysis Timeout");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(RateLimitExceededException exception) {
        var nanos = Math.max(0L, exception.getProbe().getNanosToWaitForRefill());
        var wait = Duration.ofNanos(nanos);
        var retryAfterSeconds = nanos <= 0L ? 0L : (nanos + 999_999_999L) / 1_000_000_000L;
        var hours = wait.toHours();
        var minutes = wait.minusHours(hours).toMinutes();
        var message = "解析枠の残高が不足しています。回復まで約" + hours + "時間" + minutes + "分です。";
        logger.warn("Rate limit exceeded plan={}", exception.getPlanName());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(
                        "RATE_LIMIT_EXCEEDED",
                        message,
                        exception.getCurrentLimit(),
                        exception.getPlanName(),
                        retryAfterSeconds));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleEntityNotFound(EntityNotFoundException exception) {
        logger.warn("Resource not found", exception);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problemDetail.setTitle("Resource Not Found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleIllegalState(IllegalStateException exception) {
        logger.warn("Invalid state transition", exception);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, exception.getMessage());
        problemDetail.setTitle("Invalid State Transition");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException exception) {
        logger.warn("Invalid argument", exception);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, exception.getMessage());
        problemDetail.setTitle("Invalid Argument");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationFailure(MethodArgumentNotValidException exception) {
        logger.warn("Request validation failed", exception);
        Map<String, String> fieldValidationErrors = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fieldError -> fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "Invalid value",
                        (existingMessage, duplicateMessage) -> existingMessage
                ));
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request validation failed");
        problemDetail.setTitle("Validation Error");
        problemDetail.setProperty("fieldErrors", fieldValidationErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }

    @ExceptionHandler(HttpMessageNotWritableException.class)
    public ResponseEntity<Void> handleHttpMessageNotWritable(HttpMessageNotWritableException exception) {
        logger.debug(exception.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    @ExceptionHandler(JsonbSerializationException.class)
    public ResponseEntity<ProblemDetail> handleJsonbSerializationFailure(JsonbSerializationException exception) {
        logger.error("AI response JSON parse failure", exception);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY, "AI response could not be parsed");
        problemDetail.setTitle("AI Response Parse Failure");
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleResponseStatus(ResponseStatusException exception) {
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
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(status.getReasonPhrase());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpectedException(Exception exception) {
        logger.error("Unhandled exception", exception);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problemDetail.setTitle("Internal Server Error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }
}
