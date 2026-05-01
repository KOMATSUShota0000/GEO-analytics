package com.geo.analytics.domain.exception;

/**
 * URL からのコンテンツ取得・クリーニング等のスクレイピング契約に失敗した場合に送出される実行時例外。
 */
public class ScrapingException extends RuntimeException {

    public ScrapingException() {
        super();
    }

    public ScrapingException(String message) {
        super(message);
    }

    public ScrapingException(String message, Throwable cause) {
        super(message, cause);
    }

    public ScrapingException(Throwable cause) {
        super(cause);
    }
}
