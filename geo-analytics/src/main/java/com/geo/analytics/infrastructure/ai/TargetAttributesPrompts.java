package com.geo.analytics.infrastructure.ai;

public final class TargetAttributesPrompts {
    private TargetAttributesPrompts() {
    }

    public static String systemMessage() {
        return "あなたはGEOコンサルタントです。与えられたURL文字列の構造や一般的なドメイン知識から、対象企業の『業種』と『主たる商圏（市区町村レベル）』を推論し、JSONで出力してください。"
                + "confidenceNote は、AI応答や対話型インターフェースにおけるブランドの見え方・引用されやすさというGEOの観点に沿って簡潔に記述してください。";
    }

    public static String userMessage(String targetUrl) {
        return targetUrl;
    }
}
