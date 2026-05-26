package com.geo.analytics.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.infrastructure.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

class RefreshTokenCookieFactoryTest {

    private static final String TEST_SECRET =
            "9f2e7c4a8b1d6e3f0a5c9b2d7e1f4a8c0b3d6e9f2a5c8b1d4e7f0a3c6b9d2e5f8a1c4b7d0e3f6a9c2b5d8e1f4a7c0b3d6e9f2a5c8";

    @Test
    void buildSetsHttpOnlyPathSameSiteMaxAgeAndInsecureByDefault() {
        RefreshTokenCookieFactory factory = new RefreshTokenCookieFactory(appProperties(false));
        ResponseCookie cookie = factory.build("tok-value");
        assertThat(cookie.getName()).isEqualTo(RefreshTokenCookieFactory.REFRESH_TOKEN_COOKIE_NAME);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isFalse();
        assertThat(cookie.getPath()).isEqualTo("/api/auth/refresh");
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(2592000L);
        assertThat(cookie.getValue()).isEqualTo("tok-value");
    }

    @Test
    void buildSetsSecureWhenConfigured() {
        RefreshTokenCookieFactory factory = new RefreshTokenCookieFactory(appProperties(true));
        ResponseCookie cookie = factory.build("x");
        assertThat(cookie.isSecure()).isTrue();
    }

    private static AppProperties appProperties(boolean cookieSecure) {
        AppProperties p = new AppProperties();
        AppProperties.Security s = new AppProperties.Security();
        AppProperties.Jwt j = new AppProperties.Jwt();
        j.setSecret(TEST_SECRET);
        j.setRefreshTokenExpirationSec(2592000);
        j.setCookieSecure(cookieSecure);
        s.setJwt(j);
        p.setSecurity(s);
        return p;
    }
}
