package com.geo.analytics.web.controller;

import com.geo.analytics.application.dto.LoginRequest;
import com.geo.analytics.application.dto.LoginResponse;
import com.geo.analytics.application.service.AuthService;
import com.geo.analytics.application.service.LoginCodeService;
import com.geo.analytics.application.service.AuthService.AuthTokenPair;
import com.geo.analytics.domain.exception.TokenExpiredException;
import com.geo.analytics.infrastructure.ratelimit.ClientIpResolver;
import com.geo.analytics.infrastructure.security.JwtTokenException;
import com.geo.analytics.infrastructure.security.RefreshTokenCookieFactory;
import com.geo.analytics.infrastructure.security.TokenService;
import com.geo.analytics.infrastructure.tenant.TenantIdentity;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import com.geo.analytics.web.dto.LoginCodeRequest;
import com.geo.analytics.web.dto.LoginCodeVerifyRequest;
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
    private final LoginCodeService loginCodeService;
    private final ClientIpResolver clientIpResolver;

    public AuthController(
            AuthService authService,
            RefreshTokenCookieFactory refreshTokenCookieFactory,
            TokenService tokenService,
            LoginCodeService loginCodeService,
            ClientIpResolver clientIpResolver) {
        this.authService = authService;
        this.refreshTokenCookieFactory = refreshTokenCookieFactory;
        this.tokenService = tokenService;
        this.loginCodeService = loginCodeService;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthTokenPair tokens = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.build(tokens.refreshToken()).toString())
                .body(new LoginResponse(tokens.accessToken()));
    }

    // Why: 登録の有無で応答を変えない（#144 確定事項3）。送ったかどうかは返さず、常に 202 を返す。
    @PostMapping("/auth/code")
    public ResponseEntity<Void> requestLoginCode(
            @Valid @RequestBody LoginCodeRequest request, HttpServletRequest httpRequest) {
        // 送信回数の上限（#147）に当たったときだけ 429 になる。上限は登録の有無に関係なく数えるので、応答から登録の有無は分からない。
        loginCodeService.requestCode(request.email(), clientIpResolver.resolve(httpRequest));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/auth/code/verify")
    public ResponseEntity<LoginResponse> verifyLoginCode(@Valid @RequestBody LoginCodeVerifyRequest request) {
        AuthTokenPair tokens = loginCodeService.verify(request.email(), request.code());
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
        TenantIdentity refreshScope = new TenantIdentity(parsed.organizationId(), null, null);
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
