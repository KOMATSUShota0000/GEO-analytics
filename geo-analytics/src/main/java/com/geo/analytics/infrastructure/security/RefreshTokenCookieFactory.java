package com.geo.analytics.infrastructure.security;

import com.geo.analytics.infrastructure.config.AppProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCookieFactory {

    public static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";

    private final AppProperties appProperties;

    public RefreshTokenCookieFactory(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public ResponseCookie build(String refreshToken) {
        AppProperties.Jwt jwt = appProperties.getSecurity().getJwt();
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(jwt.isCookieSecure())
                .path("/api/auth/refresh")
                .maxAge(Duration.ofSeconds(jwt.getRefreshTokenExpirationSec()))
                .sameSite("Strict")
                .build();
    }
}
