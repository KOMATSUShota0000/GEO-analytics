package com.geo.analytics.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.domain.exception.ForbiddenApiException;
import com.geo.analytics.domain.exception.SessionRevokedException;
import com.geo.analytics.domain.exception.TokenExpiredException;
import com.geo.analytics.domain.exception.UnauthenticatedApiException;
import com.geo.analytics.web.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * Servlet フィルタや Security の {@code EntryPoint} から {@link GlobalExceptionHandler} と同じ JSON を返すためのブリッジ。
 *
 * <p>まず {@link HandlerExceptionResolver}（{@code @RestControllerAdvice} を含む）に委譲し、
 * ステータスが未設定のときのみ {@link ApiErrorResponse} を直接書き込む。
 */
@Component
public class SecurityExceptionResponseHandler {

    private static final Logger log = LoggerFactory.getLogger(SecurityExceptionResponseHandler.class);

    private final HandlerExceptionResolver handlerExceptionResolver;
    private final ObjectMapper objectMapper;

    public SecurityExceptionResponseHandler(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver,
            ObjectMapper objectMapper) {
        this.handlerExceptionResolver = handlerExceptionResolver;
        this.objectMapper = objectMapper;
    }

    public void handle(HttpServletRequest request, HttpServletResponse response, Exception exception)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        try {
            ModelAndView unused = handlerExceptionResolver.resolveException(request, response, null, exception);
            if (unused != null) {
                log.trace("HandlerExceptionResolver returned ModelAndView for {}", exception.getClass().getName());
            }
        } catch (Exception e) {
            log.warn("HandlerExceptionResolver failed for {}", exception.getClass().getName(), e);
        }
        if (!response.isCommitted() && response.getStatus() < HttpStatus.BAD_REQUEST.value()) {
            writeFallback(response, exception);
        }
    }

    private void writeFallback(HttpServletResponse response, Exception exception) throws IOException {
        ApiErrorResponse body = toApiError(exception);
        response.setStatus(statusFor(exception));
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
        response.flushBuffer();
    }

    private static int statusFor(Exception exception) {
        return switch (exception) {
            case TokenExpiredException _, SessionRevokedException _, UnauthenticatedApiException _ ->
                    HttpStatus.UNAUTHORIZED.value();
            case ForbiddenApiException _ -> HttpStatus.FORBIDDEN.value();
            default -> HttpStatus.INTERNAL_SERVER_ERROR.value();
        };
    }

    private static ApiErrorResponse toApiError(Exception exception) {
        return switch (exception) {
            case TokenExpiredException e -> ApiErrorResponse.of("token_expired", e.getMessage());
            case SessionRevokedException e -> ApiErrorResponse.of("session_revoked", e.getMessage());
            case UnauthenticatedApiException e -> ApiErrorResponse.of("unauthorized", e.getMessage());
            case ForbiddenApiException e -> ApiErrorResponse.of("forbidden", e.getMessage());
            default -> ApiErrorResponse.of("unknown", "予期しないエラーが発生しました。");
        };
    }
}
