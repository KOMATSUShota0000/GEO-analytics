package com.geo.analytics.application.service;

import com.geo.analytics.domain.entity.LoginCode;
import com.geo.analytics.infrastructure.repository.LoginCodeRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;
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

    /**
     * 入力されたコードのハッシュを照合し、合っていれば使用済みにする。合わなければ失敗回数を1つ増やす。
     *
     * <p>行を {@code FOR UPDATE} で押さえてから判定と更新を1トランザクションで行う。同じコードを同時に送られても
     * 成功は1回だけにし、並行して送られても失敗回数の上限を超えて試せないようにするため。
     *
     * <p>行が無いとき（未登録のアドレスを架空のユーザーIDで照合したときを含む）も、登録済みと同じ
     * 検索と更新の2文を実行する。実行する文の数で応答時間に差が出ないようにするため。
     */
    @Transactional
    public boolean consume(UUID userId, String candidateHash, Instant now, int maxFailedAttempts) {
        Optional<LoginCode> row = loginCodeRepository.findForUpdate(userId);
        if (row.isPresent() && isUsable(row.get(), now, maxFailedAttempts) && sameHash(row.get().getCodeHash(), candidateHash)) {
            loginCodeRepository.markConsumed(userId, now);
            return true;
        }
        loginCodeRepository.recordFailure(userId);
        return false;
    }

    private static boolean isUsable(LoginCode code, Instant now, int maxFailedAttempts) {
        return code.getConsumedAt() == null && now.isBefore(code.getExpiresAt()) && code.getFailedAttempts() < maxFailedAttempts;
    }

    // Why: 一致した文字数で所要時間が変わらないよう、先頭から比べて途中で抜ける String#equals は使わない。
    private static boolean sameHash(String stored, String candidate) {
        return MessageDigest.isEqual(stored.getBytes(StandardCharsets.US_ASCII), candidate.getBytes(StandardCharsets.US_ASCII));
    }
}
