package com.geo.analytics.infrastructure.ai;

public final class TargetAttributesPrompts {
    private TargetAttributesPrompts() {
    }

    public static String systemMessage() {
        return "あなたはGEOコンサルタントです。与えられたURL文字列の構造や一般的なドメイン知識から、対象企業の業種と日本の住所階層（city・ward・town）および主たる商圏ラベル（tradeAreaLabel）を推論し、JSONで出力してください。"
                + "categoryKeyword は、Googleマップ／Web検索で同業の競合を見つけるための『具体的な職種・業態ワード』を1つ出力する（例: 訪問看護、美容室、歯科医院、ラーメン店、税理士事務所）。粗い分類語（例: YMYL分野・サービス・その他）や地名・固有のブランド名は含めず、検索でその職種の事業者が見つかる最も具体的で一般的な語にすること。判断できない場合のみ null。"
                + "city は市、または東京都の特別区（例: 渋谷区、新宿区）を指す。ward は政令指定都市などの行政区（例: 港北区）を指し、該当しない場合は null。town は町名・丁目などより細かい町域を指し、不明なら null。"
                + "tradeAreaLabel はユーザー向けの短い商圏表現とし、city・ward・town と整合させる。"
                + "検索範囲を不必要に広げないこと。confidenceNote・tradeAreaLabel・city・ward・town のいずれにも文字列「日本」および「全国」を含めないこと。"
                + "confidenceNote は、AI応答や対話型インターフェースにおけるブランドの見え方・引用されやすさというGEOの観点に沿って簡潔に記述してください。";
    }

    public static String userMessage(String targetUrl) {
        return targetUrl;
    }
}
