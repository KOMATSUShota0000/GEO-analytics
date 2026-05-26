package com.geo.analytics.infrastructure.security;

import com.geo.analytics.domain.entity.OrganizationUser;
import com.geo.analytics.infrastructure.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

    private static final int MIN_SECRET_UTF8_BYTES = 32;

    public record ParsedAccessToken(UUID userId, UUID organizationId, String role, UUID sessionId) {}

    public record ParsedRefreshToken(UUID userId, UUID organizationId, UUID sessionId) {}

    private final AppProperties appProperties;
    private final SecretKey signingKey;

    public TokenService(AppProperties appProperties) {
        this.appProperties = appProperties;
        this.signingKey = buildSigningKey(appProperties);
    }

    private static SecretKey buildSigningKey(AppProperties appProperties) {
        String secret = null;
        if (appProperties.getSecurity() != null) {
            secret = appProperties.getSecurity().getJwt().getSecret();
        }
        if (secret == null || secret.isEmpty()) {
            throw new IllegalStateException("app.security.jwt.secret is required");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_UTF8_BYTES) {
            throw new IllegalStateException("app.security.jwt.secret must be at least 32 UTF-8 bytes");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(OrganizationUser user, UUID sessionId) {
        AppProperties.Jwt jwt = appProperties.getSecurity().getJwt();
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .claim("sid", sessionId.toString())
                .claim("org", user.getOrganizationId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(jwt.getAccessTokenExpirationSec())))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public String generateRefreshToken(OrganizationUser user, UUID sessionId) {
        AppProperties.Jwt jwt = appProperties.getSecurity().getJwt();
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("sid", sessionId.toString())
                .claim("org", user.getOrganizationId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(jwt.getRefreshTokenExpirationSec())))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public ParsedAccessToken parseAccessToken(String token) {
        try {
            Claims claims =
                    Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
            return mapAccessClaims(claims);
        } catch (ExpiredJwtException e) {
            throw new ExpiredJwtTokenException(e);
        } catch (JwtException e) {
            throw new InvalidJwtTokenException("Invalid access token", e);
        }
    }

    public ParsedRefreshToken parseRefreshToken(String token) {
        try {
            Claims claims =
                    Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
            return mapRefreshClaims(claims);
        } catch (ExpiredJwtException e) {
            throw new ExpiredJwtTokenException(e);
        } catch (JwtException e) {
            throw new InvalidJwtTokenException("Invalid refresh token", e);
        }
    }

    private static ParsedAccessToken mapAccessClaims(Claims claims) {
        try {
            UUID userId = UUID.fromString(claims.getSubject());
            String orgRaw = claims.get("org", String.class);
            String role = claims.get("role", String.class);
            String sidRaw = claims.get("sid", String.class);
            if (orgRaw == null || role == null || sidRaw == null) {
                throw new InvalidJwtTokenException("Missing required claims");
            }
            return new ParsedAccessToken(userId, UUID.fromString(orgRaw), role, UUID.fromString(sidRaw));
        } catch (InvalidJwtTokenException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new InvalidJwtTokenException("Malformed access token claims", e);
        }
    }

    private static ParsedRefreshToken mapRefreshClaims(Claims claims) {
        try {
            UUID userId = UUID.fromString(claims.getSubject());
            String orgRaw = claims.get("org", String.class);
            String sidRaw = claims.get("sid", String.class);
            if (orgRaw == null || sidRaw == null) {
                throw new InvalidJwtTokenException("Missing required claims");
            }
            return new ParsedRefreshToken(userId, UUID.fromString(orgRaw), UUID.fromString(sidRaw));
        } catch (InvalidJwtTokenException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new InvalidJwtTokenException("Malformed refresh token claims", e);
        }
    }
}
