package com.geo.analytics.infrastructure.security;

import com.geo.analytics.infrastructure.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * ログインコードを保存用のハッシュに変える。
 *
 * <p>6桁のコードは100万通りしかなく、素の SHA-256 ではデータベースが漏れたときに一瞬で逆算される。
 * サーバーだけが持つ鍵で HMAC を取り、データベースだけでは逆算できないようにする。
 * ユーザーIDも混ぜ、同じコードでもユーザーごとに別のハッシュになるようにする。
 */
@Component
public class LoginCodeHasher {

    private static final String ALGORITHM = "HmacSHA256";
    // Why: JWT の署名鍵をそのまま使い回さず、用途名で派生した鍵にする。鍵の用途が混ざらないようにするため。
    private static final byte[] KEY_PURPOSE = "geo-analytics/login-code/v1".getBytes(StandardCharsets.UTF_8);

    private final SecretKeySpec key;

    public LoginCodeHasher(AppProperties appProperties) {
        String secret = appProperties.getSecurity() != null && appProperties.getSecurity().getJwt() != null
                ? appProperties.getSecurity().getJwt().getSecret()
                : null;
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.security.jwt.secret is required");
        }
        this.key = new SecretKeySpec(mac(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM), KEY_PURPOSE), ALGORITHM);
    }

    public String hash(UUID userId, String code) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(code, "code");
        byte[] message = (userId + ":" + code).getBytes(StandardCharsets.UTF_8);
        return HexFormat.of().formatHex(mac(key, message));
    }

    private static byte[] mac(SecretKeySpec key, byte[] message) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(message);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is unavailable", e);
        }
    }
}
