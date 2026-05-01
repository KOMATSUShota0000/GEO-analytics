package com.geo.analytics.application.exception;

/**
 * {@link com.geo.analytics.application.service.QueryProposalService} における統合失敗。
 * 呼び出し元向けの短い {@link #getUserMessage()}、検証時の {@link #getDetail()}、デバッグ用の {@link #getCause()} を備える。
 */
public class QueryProposalException extends RuntimeException {

    private final QueryProposalPhase phase;

    private final String userMessage;

    private final String detail;

    public QueryProposalException(QueryProposalPhase phase, String userMessage, Throwable cause) {
        this(phase, userMessage, null, cause);
    }

    public QueryProposalException(QueryProposalPhase phase, String userMessage, String detail, Throwable cause) {
        super(combine(userMessage, detail), cause);
        this.phase = phase;
        this.userMessage = userMessage;
        this.detail = detail;
    }

    private static String combine(String userMessage, String detail) {
        if (detail == null || detail.isBlank()) {
            return userMessage;
        }
        return userMessage + ": " + detail;
    }

    public QueryProposalPhase getPhase() {
        return phase;
    }

    public String getUserMessage() {
        return userMessage;
    }

    /**
     * どの検証項目で不合格となったか等の補足（ユーザー向け全文メッセージは {@link #getMessage()}）。
     */
    public String getDetail() {
        return detail;
    }
}
