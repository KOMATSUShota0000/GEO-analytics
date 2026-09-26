package com.geo.analytics.domain.exception;

/**
 * ログインコードで入れなかった場合。
 *
 * <p>誤り・期限切れ・使用済み・失敗回数の超過・未登録のアドレスを区別しない（#144 確定事項11）。
 * 理由や残り回数を分けると、登録の有無が外から分かってしまうため。
 */
public class LoginCodeRejectedException extends RuntimeException {

    public LoginCodeRejectedException() {
        super("コードが正しくないか、期限が切れています。");
    }
}
