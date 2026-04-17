package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.LoginRequest;
import com.geo.analytics.domain.entity.OrganizationUser;
import com.geo.analytics.domain.exception.AccountDisabledException;
import com.geo.analytics.domain.exception.CredentialsRevokedException;
import com.geo.analytics.domain.exception.SessionRevokedException;
import com.geo.analytics.domain.exception.TenantSuspendedException;
import com.geo.analytics.infrastructure.repository.OrganizationUserRepository;
import com.geo.analytics.infrastructure.security.TokenService;
import com.geo.analytics.infrastructure.tenant.TenantContext;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import java.lang.ScopedValue;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    public record AuthTokenPair(String accessToken, String refreshToken) {}

    private final AuthenticationManager authenticationManager;
    private final OrganizationUserRepository organizationUserRepository;
    private final SessionManagementService sessionManagementService;
    private final TokenService tokenService;

    public AuthService(
            AuthenticationManager authenticationManager,
            OrganizationUserRepository organizationUserRepository,
            SessionManagementService sessionManagementService,
            TokenService tokenService) {
        this.authenticationManager = authenticationManager;
        this.organizationUserRepository = organizationUserRepository;
        this.sessionManagementService = sessionManagementService;
        this.tokenService = tokenService;
    }

    public AuthTokenPair login(LoginRequest request) {
        OrganizationUser user = organizationUserRepository
                .findByEmailAndDeletedAtIsNull(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        TenantContext loginScope = new TenantContext(user.getOrganizationId(), null, null);
        return ScopedValue.where(TenantContextHolder.CONTEXT, loginScope)
                .call(
                        () -> {
                            authenticationManager.authenticate(
                                    new UsernamePasswordAuthenticationToken(
                                            request.getEmail(), request.getPassword()));
                            UUID sessionId = sessionManagementService.createNewSession(user.getId());
                            String accessToken = tokenService.generateAccessToken(user, sessionId);
                            String refreshToken = tokenService.generateRefreshToken(user, sessionId);
                            return new AuthTokenPair(accessToken, refreshToken);
                        });
    }

    /**
     * リフレッシュ時のセッション検証とユーザー再読込を単一トランザクションにまとめる。
     * コントローラ経由のリポジトリ直接呼び出しはトランザクション境界が付かず RLS インターセプタに Naked Query として検知される。
     */
    @Transactional(readOnly = true)
    public String issueAccessTokenAfterRefresh(TokenService.ParsedRefreshToken parsed) {
        // TODO: [Phase X] 組織エンティティに suspended フラグを追加し、ここで判定する
        boolean isTenantSuspended = false;
        if (isTenantSuspended) {
            throw new TenantSuspendedException();
        }

        if (sessionManagementService.findActiveSessionBySessionId(parsed.sessionId()).isEmpty()) {
            throw new SessionRevokedException();
        }

        OrganizationUser activeUser =
                organizationUserRepository
                        .findById(parsed.userId())
                        .filter(u -> u.getDeletedAt() == null)
                        .orElseThrow(() -> new AccountDisabledException());

        // TODO: [Phase X] TokenService から iat を取得し、ユーザーの passwordChangedAt と比較する
        boolean isCredentialsRevoked = false;
        if (isCredentialsRevoked) {
            throw new CredentialsRevokedException();
        }

        return tokenService.generateAccessToken(activeUser, parsed.sessionId());
    }
}
