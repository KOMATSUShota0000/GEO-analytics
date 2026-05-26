package com.geo.analytics.infrastructure.ai;

import com.geo.analytics.domain.enums.IndustryType;

public final class GiantFilterPrompts {

    private GiantFilterPrompts() {}

    public static String systemPrompt() {
        return "Google Placesの候補リストから競合となる事業者を最大3つ選んでください。tabelogホットペッパー食べログ価格comホームズSUUMOZOZOアットホーム一休じゃらんトリップアドバイザーぐるなび価格com比較口コミまとめアフィリエイトメディア全国チェーン本社のみのフランチャイズ募集ページ大手ポータルディレクトリのみの行は選ばないでください。同一商圏の単一事業者・中小規模ライバルを優先し各社について日本語で120字以内のselectionReasonを必ず出力してください。SEO順位検索ボリューム順位チェックへの言及は禁止です。";
    }

    public static String userMessage(IndustryType industry, String location, String selfUrl, String candidatesJson) {
        String ind = industry == null ? "" : industry.name();
        String loc = location == null ? "" : location;
        String url = selfUrl == null ? "" : selfUrl.trim();
        String payload = candidatesJson == null ? "[]" : candidatesJson;
        return "SelfUrl=" + url + "\nIndustryType=" + ind + "\nLocation=" + loc + "\nPlacesCandidatesJson=" + payload;
    }
}
