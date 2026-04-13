package com.geo.analytics.web.controller;

import com.geo.analytics.application.dto.LoginRequest;
import com.geo.analytics.application.dto.LoginResponse;
import com.geo.analytics.application.service.AuthService;
import com.geo.analytics.application.service.AuthService.AuthTokenPair;
import com.geo.analytics.domain.entity.OrganizationUser;
import com.geo.analytics.infrastructure.repository.OrganizationUserRepository;
import com.geo.analytics.infrastructure.repository.UserSessionRepository;
import com.geo.analytics.infrastructure.security.JwtTokenException;
import com.geo.analytics.infrastructure.security.RefreshTokenCookieFactory;
import com.geo.analytics.infrastructure.security.TokenService;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
    private final UserSessionRepository userSessionRepository;
    private final OrganizationUserRepository organizationUserRepository;

    public AuthController(
            AuthService authService,
            RefreshTokenCookieFactory refreshTokenCookieFactory,
            TokenService tokenService,
            UserSessionRepository userSessionRepository,
            OrganizationUserRepository organizationUserRepository) {
        this.authService = authService;
        this.refreshTokenCookieFactory = refreshTokenCookieFactory;
        this.tokenService = tokenService;
        this.userSessionRepository = userSessionRepository;
        this.organizationUserRepository = organizationUserRepository;
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
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        final TokenService.ParsedRefreshToken parsed;
        try {
            parsed = tokenService.parseRefreshToken(raw);
        } catch (JwtTokenException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        TenantContextHolder.set(parsed.organizationId(), null);
        try {
            if (userSessionRepository
                    .findBySessionId(parsed.sessionId())
                    .filter(s -> s.getDeletedAt() == null)
                    .isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            Optional<OrganizationUser> userOpt =
                    organizationUserRepository.findById(parsed.userId()).filter(u -> u.getDeletedAt() == null);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            String accessToken = tokenService.generateAccessToken(userOpt.get(), parsed.sessionId());
            return ResponseEntity.ok(new LoginResponse(accessToken));
        } finally {
            TenantContextHolder.clear();
        }
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
