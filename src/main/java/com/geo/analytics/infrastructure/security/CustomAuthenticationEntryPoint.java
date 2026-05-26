package com.geo.analytics.infrastructure.security;

import com.geo.analytics.domain.exception.UnauthenticatedApiException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(CustomAuthenticationEntryPoint.class);

    private final SecurityExceptionResponseHandler securityExceptionResponseHandler;

    public CustomAuthenticationEntryPoint(SecurityExceptionResponseHandler securityExceptionResponseHandler) {
        this.securityExceptionResponseHandler = securityExceptionResponseHandler;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException, ServletException {
        log.debug("Authentication required: {}", authException.getMessage());
        securityExceptionResponseHandler.handle(request, response, new UnauthenticatedApiException());
    }
}
