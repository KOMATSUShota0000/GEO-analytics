package com.geo.analytics.application.service;

import com.geo.analytics.application.service.AuthService.AuthTokenPair;
import com.geo.analytics.domain.entity.OrganizationUser;
import com.geo.analytics.domain.exception.LoginCodeRejectedException;
import com.geo.analytics.infrastructure.config.AppProperties;
import com.geo.analytics.infrastructure.repository.OrganizationUserRepository;
import com.geo.analytics.infrastructure.security.LoginCodeHasher;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import com.geo.analytics.infrastructure.tenant.TenantIdentity;
import jakarta.mail.MessagingException;
import java.lang.ScopedValue;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * ログインコードを発行してメールで送り（#146）、照合してログインさせる（#151）。
 *
 * <p>登録されているかを外から見分けられないようにする（#144 確定事項3）。応答は常に同じにし、
 * 未登録のアドレスにはメールを送らない。
 */
@Service
public class LoginCodeService {

    private static final Logger log = LoggerFactory.getLogger(LoginCodeService.class);

    private static final int CODE_SPACE = 1_000_000;
    private static final SecureRandom RANDOM = new SecureRandom();
    // Why: SMTP は外部サービス。仮想スレッドは安いが、送信先への同時接続数は絞る（.cursorrules 3節の流量制限）。
    private static final int MAX_CONCURRENT_SENDS = 4;

    private final OrganizationUserRepository organizationUserRepository;
    private final LoginCodeStore loginCodeStore;
    private final LoginCodeHasher loginCodeHasher;
    private final LoginCodeMailer loginCodeMailer;
    private final AuthService authService;
    private final Duration ttl;
    private final int maxFailedAttempts;
    private final Semaphore sendPermits = new Semaphore(MAX_CONCURRENT_SENDS);

    public LoginCodeService(
            OrganizationUserRepository organizationUserRepository,
            LoginCodeStore loginCodeStore,
            LoginCodeHasher loginCodeHasher,
            LoginCodeMailer loginCodeMailer,
            AuthService authService,
            AppProperties appProperties) {
        this.organizationUserRepository = organizationUserRepository;
        this.loginCodeStore = loginCodeStore;
        this.loginCodeHasher = loginCodeHasher;
        this.loginCodeMailer = loginCodeMailer;
        this.authService = authService;
        this.ttl = appProperties.getAuth().getLoginCode().getTtl();
        this.maxFailedAttempts = appProperties.getAuth().getLoginCode().getMaxFailedAttempts();
    }

    /**
     * 登録済みのアドレスならコードを発行して送る。呼び出し側へは結果を返さない。
     *
     * <p>ユーザーの検索は登録の有無にかかわらず同じく行い、保存と送信は別スレッドに回してすぐ戻る。
     * 送信を待ってから戻ると、登録済みのときだけ応答が遅くなり、応答時間で登録の有無が分かってしまうため。
     */
    public void requestCode(String email) {
        String normalized = email == null ? "" : email.strip();
        Optional<OrganizationUser> found =
                organizationUserRepository.findFirstByEmailIgnoreCaseAndDeletedAtIsNullOrderByCreatedAtAsc(normalized);
        if (found.isEmpty()) {
            log.info("登録されていないアドレスへのログインコード要求（メールは送らない）");
            return;
        }
        OrganizationUser user = found.get();
        UUID userId = user.getId();
        UUID organizationId = user.getOrganizationId();
        String to = user.getEmail();
        Thread.ofVirtual()
                .name("login-code-org-" + organizationId + "-user-" + userId)
                .start(() -> issueAndSend(userId, organizationId, to));
    }

    /**
     * コードを照合し、合っていればログインさせる。入れなかった理由は区別せず {@link LoginCodeRejectedException} にする。
     *
     * <p>未登録のアドレスでも、架空のユーザーIDと組織IDで同じ照合（行の検索・更新・ハッシュ計算）を行う。
     * 未登録のときだけ照合を省くと、応答時間で登録の有無が分かってしまうため。
     */
    public AuthTokenPair verify(String email, String code) {
        String normalized = email == null ? "" : email.strip();
        Optional<OrganizationUser> found =
                organizationUserRepository.findFirstByEmailIgnoreCaseAndDeletedAtIsNullOrderByCreatedAtAsc(normalized);
        UUID userId = found.map(OrganizationUser::getId).orElseGet(UUID::randomUUID);
        UUID organizationId = found.map(OrganizationUser::getOrganizationId).orElseGet(UUID::randomUUID);
        String candidateHash = loginCodeHasher.hash(userId, code);
        Instant now = Instant.now();
        boolean accepted = ScopedValue.where(TenantContextHolder.CONTEXT, new TenantIdentity(organizationId, null, userId))
                .call(() -> loginCodeStore.consume(userId, candidateHash, now, maxFailedAttempts));
        if (!accepted || found.isEmpty()) {
            throw new LoginCodeRejectedException();
        }
        OrganizationUser user = found.get();
        log.info("ログインコードでログインしました userId={}", userId);
        return ScopedValue.where(TenantContextHolder.CONTEXT, new TenantIdentity(organizationId, null, null))
                .call(() -> authService.issueTokens(user));
    }

    private void issueAndSend(UUID userId, UUID organizationId, String to) {
        String code = "%06d".formatted(RANDOM.nextInt(CODE_SPACE));
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(ttl);
        try {
            ScopedValue.where(TenantContextHolder.CONTEXT, new TenantIdentity(organizationId, null, userId))
                    .run(() -> loginCodeStore.replace(
                            userId, organizationId, loginCodeHasher.hash(userId, code), issuedAt, expiresAt));
            sendPermits.acquire();
            try {
                loginCodeMailer.send(to, code, expiresAt, ttl);
            } finally {
                sendPermits.release();
            }
            log.info("ログインコードを送信しました userId={}", userId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("ログインコードの送信を中断しました userId={}", userId);
        } catch (MessagingException | RuntimeException e) {
            log.error("ログインコードの送信に失敗しました userId={}", userId, e);
        }
    }
}
