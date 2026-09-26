package com.geo.analytics.application.service;

import com.geo.analytics.domain.entity.OrganizationUser;
import com.geo.analytics.domain.exception.AccountDisabledException;
import com.geo.analytics.domain.exception.SessionRevokedException;
import com.geo.analytics.domain.exception.TenantSuspendedException;
import com.geo.analytics.infrastructure.repository.OrganizationUserRepository;
import com.geo.analytics.infrastructure.security.TokenService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    public record AuthTokenPair(String accessToken, String refreshToken) {}

    private final OrganizationUserRepository organizationUserRepository;
    private final SessionManagementService sessionManagementService;
    private final TokenService tokenService;

    public AuthService(
            OrganizationUserRepository organizationUserRepository,
            SessionManagementService sessionManagementService,
            TokenService tokenService) {
        this.organizationUserRepository = organizationUserRepository;
        this.sessionManagementService = sessionManagementService;
        this.tokenService = tokenService;
    }

    /**
     * 本人確認が済んだユーザーの新しいセッションを作り、アクセストークンとリフレッシュトークンを発行する。
     * コードでのログイン（{@link LoginCodeService}）から呼ぶ。呼び出し側でユーザーの組織を束縛すること。
     */
    public AuthTokenPair issueTokens(OrganizationUser user) {
        UUID sessionId = sessionManagementService.createNewSession(user.getId());
        String accessToken = tokenService.generateAccessToken(user, sessionId);
        String refreshToken = tokenService.generateRefreshToken(user, sessionId);
        return new AuthTokenPair(accessToken, refreshToken);
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

        return tokenService.generateAccessToken(activeUser, parsed.sessionId());
    }
}
