package com.geo.analytics.domain.exception;

import java.time.Duration;

/**
 * ログインコードの送信回数の上限に当たった場合（#147）。
 *
 * <p>画面（AUTH-4）が「60秒待ち」と「回数の上限」で文言を変えられるよう、種類を持たせる（#147）。
 * 登録されていないアドレスでも同じように当たるので、種類を返しても登録の有無は分からない。
 */
public class LoginCodeSendLimitedException extends RuntimeException {

    public enum Kind {
        RESEND_TOO_SOON,
        SEND_LIMIT
    }

    private final Kind kind;
    private final Duration retryAfter;

    public LoginCodeSendLimitedException(Kind kind, Duration retryAfter) {
        super(kind == Kind.RESEND_TOO_SOON
                ? "続けて送ることはできません。少し待ってからもう一度お試しください。"
                : "送信回数の上限に達しました。しばらく待ってからもう一度お試しください。");
        this.kind = kind;
        this.retryAfter = retryAfter;
    }

    public Kind getKind() {
        return kind;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
