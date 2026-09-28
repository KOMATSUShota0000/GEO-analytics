package com.geo.analytics.domain.exception;

/**
 * Stripe の決済ページを開けなかった場合（#167）。
 *
 * <p>設定の問題は何度押しても通らないため、「時間をおいて再度」と案内する一時的な失敗と
 * 画面の文言を分けられるよう、種類を持たせる。どの設定が足りないかはサーバーのログにだけ出す。
 */
public class CheckoutUnavailableException extends RuntimeException {

    public enum Kind {
        NOT_CONFIGURED,
        TEMPORARY
    }

    private final Kind kind;

    public CheckoutUnavailableException(Kind kind) {
        this(kind, null);
    }

    public CheckoutUnavailableException(Kind kind, Throwable cause) {
        super(kind == Kind.NOT_CONFIGURED
                ? "オンラインでのお申し込みを受け付けられません。"
                : "決済ページを開けませんでした。少し時間をおいて、もう一度お試しください。", cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
