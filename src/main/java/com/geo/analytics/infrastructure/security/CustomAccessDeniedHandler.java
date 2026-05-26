package com.geo.analytics.infrastructure.security;

import com.geo.analytics.domain.exception.ForbiddenApiException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(CustomAccessDeniedHandler.class);

    private final SecurityExceptionResponseHandler securityExceptionResponseHandler;

    public CustomAccessDeniedHandler(SecurityExceptionResponseHandler securityExceptionResponseHandler) {
        this.securityExceptionResponseHandler = securityExceptionResponseHandler;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        log.warn("Access denied: {}", accessDeniedException.getMessage());
        securityExceptionResponseHandler.handle(request, response, new ForbiddenApiException());
    }
}
