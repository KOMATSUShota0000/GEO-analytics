package com.geo.analytics.domain.model;

/**
 * クォータ／クレジットは「キーワード1件につき保留・確定ともに同一の固定値」とし、出力長やモデル構成による変動を持たせない。
 */
public final class QuotaCreditCalculator {
    /** キーワード1件あたりに事前にロックするクレジット（成功時も確定消費はこの値と同一）。 */
    public static final int DEPOSIT_PER_KEYWORD = 10;

    private QuotaCreditCalculator() {}

    public static long refundAfterDeposit(long deposit, long billedCredits) {
        long d = deposit - billedCredits;
        return d > 0L ? d : 0L;
    }
}
