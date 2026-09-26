package com.geo.analytics.application.service;

import com.geo.analytics.infrastructure.repository.LoginCodeRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ログインコードの保存。
 *
 * <p>RLS の組織IDはトランザクション内でしか接続へ渡らない（{@code RlsConnectionInterceptor}）。
 * リポジトリ単体の {@code @Transactional} ではトランザクション開始前に検査され遮断されるため、
 * サービス層でトランザクションを張ってから呼ぶ。呼び出し側で組織の {@code TenantIdentity} を束縛すること。
 */
@Service
public class LoginCodeStore {

    private final LoginCodeRepository loginCodeRepository;

    public LoginCodeStore(LoginCodeRepository loginCodeRepository) {
        this.loginCodeRepository = loginCodeRepository;
    }

    @Transactional
    public void replace(UUID userId, UUID organizationId, String codeHash, Instant issuedAt, Instant expiresAt) {
        loginCodeRepository.upsert(userId, organizationId, codeHash, issuedAt, expiresAt);
    }
}
