package com.geo.analytics.web.controller;

import com.geo.analytics.application.dto.LoginRequest;
import com.geo.analytics.application.dto.LoginResponse;
import com.geo.analytics.application.service.AuthService;
import com.geo.analytics.application.service.AuthService.AuthTokenPair;
import com.geo.analytics.domain.exception.TokenExpiredException;
import com.geo.analytics.infrastructure.security.JwtTokenException;
import com.geo.analytics.infrastructure.security.RefreshTokenCookieFactory;
import com.geo.analytics.infrastructure.security.TokenService;
import com.geo.analytics.infrastructure.tenant.TenantContext;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.lang.ScopedValue;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;
    private final TokenService tokenService;

    public AuthController(
            AuthService authService,
            RefreshTokenCookieFactory refreshTokenCookieFactory,
            TokenService tokenService) {
        this.authService = authService;
        this.refreshTokenCookieFactory = refreshTokenCookieFactory;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthTokenPair tokens = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.build(tokens.refreshToken()).toString())
                .body(new LoginResponse(tokens.accessToken()));
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<LoginResponse> refresh(HttpServletRequest request) {
        String raw = readCookie(request, RefreshTokenCookieFactory.REFRESH_TOKEN_COOKIE_NAME);
        if (raw == null || raw.isBlank()) {
            throw new TokenExpiredException("リフレッシュトークンがありません。");
        }
        final TokenService.ParsedRefreshToken parsed;
        try {
            parsed = tokenService.parseRefreshToken(raw);
        } catch (JwtTokenException e) {
            throw new TokenExpiredException("リフレッシュトークンが無効か期限切れです。", e);
        }
        TenantContext refreshScope = new TenantContext(parsed.organizationId(), null, null);
        return ScopedValue.where(TenantContextHolder.CONTEXT, refreshScope)
                .call(
                        () ->
                                ResponseEntity.ok(
                                        new LoginResponse(authService.issueAccessTokenAfterRefresh(parsed))));
    }

    private static String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
