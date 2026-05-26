package com.geo.analytics.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.geo.analytics.domain.entity.OrganizationUser;
import com.geo.analytics.domain.enums.OrganizationUserRole;
import com.geo.analytics.infrastructure.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class TokenServiceTest {

    private static final String TEST_SECRET =
            "9f2e7c4a8b1d6e3f0a5c9b2d7e1f4a8c0b3d6e9f2a5c8b1d4e7f0a3c6b9d2e5f8a1c4b7d0e3f6a9c2b5d8e1f4a7c0b3d6e9f2a5c8";

    @Test
    void constructorFailsWhenSecretMissing() {
        AppProperties p = new AppProperties();
        assertThatThrownBy(() -> new TokenService(p))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secret");
    }

    @Test
    void constructorFailsWhenSecretTooShort() {
        AppProperties p = new AppProperties();
        AppProperties.Security s = new AppProperties.Security();
        AppProperties.Jwt j = new AppProperties.Jwt();
        j.setSecret("0123456789012345678901234567890");
        s.setJwt(j);
        p.setSecurity(s);
        assertThatThrownBy(() -> new TokenService(p)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void accessTokenContainsExpectedClaims() {
        TokenService tokenService = new TokenService(testAppProperties());
        OrganizationUser user = new OrganizationUser();
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID orgId = UUID.fromString("00000000-0000-4000-8000-000000000001");
        user.setId(userId);
        user.setOrganizationId(orgId);
        user.setRole(OrganizationUserRole.ADMIN);
        UUID sessionId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        String token = tokenService.generateAccessToken(user, sessionId);
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
        assertThat(claims.get("sid", String.class)).isEqualTo(sessionId.toString());
        assertThat(claims.get("org", String.class)).isEqualTo(orgId.toString());
        long expSec = claims.getExpiration().getTime() / 1000;
        long iatSec = claims.getIssuedAt().getTime() / 1000;
        assertThat(expSec - iatSec).isEqualTo(900L);
    }

    @Test
    void refreshTokenContainsExpectedClaims() {
        TokenService tokenService = new TokenService(testAppProperties());
        OrganizationUser user = new OrganizationUser();
        UUID userId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID orgId = UUID.fromString("00000000-0000-4000-8000-000000000002");
        user.setId(userId);
        user.setOrganizationId(orgId);
        user.setRole(OrganizationUserRole.MEMBER);
        UUID sessionId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        String token = tokenService.generateRefreshToken(user, sessionId);
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("sid", String.class)).isEqualTo(sessionId.toString());
        assertThat(claims.get("org", String.class)).isEqualTo(orgId.toString());
        long expSec = claims.getExpiration().getTime() / 1000;
        long iatSec = claims.getIssuedAt().getTime() / 1000;
        assertThat(expSec - iatSec).isEqualTo(2592000L);
    }

    @Test
    void parseAccessTokenRoundTrip() {
        TokenService tokenService = new TokenService(testAppProperties());
        OrganizationUser user = new OrganizationUser();
        UUID userId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID orgId = UUID.fromString("00000000-0000-4000-8000-000000000003");
        user.setId(userId);
        user.setOrganizationId(orgId);
        user.setRole(OrganizationUserRole.VIEWER);
        UUID sessionId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        String token = tokenService.generateAccessToken(user, sessionId);
        TokenService.ParsedAccessToken parsed = tokenService.parseAccessToken(token);
        assertThat(parsed.userId()).isEqualTo(userId);
        assertThat(parsed.organizationId()).isEqualTo(orgId);
        assertThat(parsed.role()).isEqualTo("VIEWER");
        assertThat(parsed.sessionId()).isEqualTo(sessionId);
    }

    @Test
    void parseAccessTokenThrowsWhenExpired() {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        String jwt = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", "ADMIN")
                .claim("sid", UUID.randomUUID().toString())
                .claim("org", UUID.randomUUID().toString())
                .issuedAt(Date.from(Instant.now().minusSeconds(10_000)))
                .expiration(Date.from(Instant.now().minusSeconds(5000)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        TokenService tokenService = new TokenService(testAppProperties());
        assertThatThrownBy(() -> tokenService.parseAccessToken(jwt)).isInstanceOf(ExpiredJwtTokenException.class);
    }

    private static AppProperties testAppProperties() {
        AppProperties p = new AppProperties();
        AppProperties.Security s = new AppProperties.Security();
        AppProperties.Jwt j = new AppProperties.Jwt();
        j.setSecret(TEST_SECRET);
        j.setAccessTokenExpirationSec(900);
        j.setRefreshTokenExpirationSec(2592000);
        s.setJwt(j);
        p.setSecurity(s);
        return p;
    }
}
