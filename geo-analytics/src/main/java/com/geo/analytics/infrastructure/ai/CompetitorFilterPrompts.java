package com.geo.analytics.infrastructure.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.ExtractedPlace;
import com.geo.analytics.domain.enums.IndustryType;
import com.geo.analytics.infrastructure.api.dto.SerpOrganicResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CompetitorFilterPrompts {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private CompetitorFilterPrompts() {
    }

    public static String systemMessage() {
        return "あなたはGEO競合スカウトです。近隣の実店舗を優先して選び、地域に根ざした単一事業者に限定し、実店舗または単一ブランドで運営されるビジネスを重視してください。一覧や比較を主業とする集約型プラットフォームよりも、現場でサービスを提供する規模感の近い候補を選んでください。"
                + "評価基準と選定理由（reasoning）の記述には、必ず『AI可視性ランク（GEO Readiness）』および『AI推奨ポテンシャル』の観点のみを用い、AIプラットフォーム上でのブランド露出と信頼性構築に焦点を当てて論理を展開してください。"
                + "各候補には candidates に index が付与されています。出力の sourceIndex は必ずその index のいずれかと一致させ、ちょうど3要素の selections を返してください。";
    }

    public static String systemMessageCorporateService() {
        return "あなたはGEO競合スカウトです。Googleマップ前提の近隣店舗ではなく、専門領域の重複が大きい全国規模のB2B競合サイトを優先してください。同一の意思決定者向けに同種のサービス・ソリューションを提示する事業者の公式サイトや製品サイトを選び、求人媒体・株情報・百科事典・ニュース記事のみのサイトや無関係なディレクトリは避けてください。"
                + "評価基準と選定理由（reasoning）の記述には、必ず『AI可視性ランク（GEO Readiness）』および『AI推奨ポテンシャル』の観点のみを用い、AIプラットフォーム上でのブランド露出と信頼性構築に焦点を当てて論理を展開してください。"
                + "各候補には candidates に index が付与されています。出力の sourceIndex は必ずその index のいずれかと一致させ、ちょうど3要素の selections を返してください。";
    }

    public static String systemMessageOnlineService() {
        return "あなたはGEO競合スカウトです。商品カテゴリの被りが大きく、おおよそ同価格帯または同じ購入ニーズを満たすECサイト・ブランド公式の通販を優先してください。比較・アフィリエイト中心の薄い一覧ページやクーポン誘導のみのサイトは避け、実際に商品を販売する競合を選んでください。"
                + "評価基準と選定理由（reasoning）の記述には、必ず『AI可視性ランク（GEO Readiness）』および『AI推奨ポテンシャル』の観点のみを用い、AIプラットフォーム上でのブランド露出と信頼性構築に焦点を当てて論理を展開してください。"
                + "各候補には candidates に index が付与されています。出力の sourceIndex は必ずその index のいずれかと一致させ、ちょうど3要素の selections を返してください。";
    }

    public static String userMessage(IndustryType industry, String tradeAreaLabel, List<ExtractedPlace> places) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("industry", industry.name());
            root.put("industryLabel", industry.getLabel());
            root.put("tradeAreaLabel", tradeAreaLabel != null ? tradeAreaLabel : "");
            List<Map<String, Object>> candidates = new ArrayList<>();
            for (int i = 0; i < places.size(); i++) {
                ExtractedPlace place = places.get(i);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("index", i);
                row.put("name", place.name());
                row.put("websiteUrl", place.websiteUrl());
                row.put("rating", place.rating());
                row.put("userRatingsTotal", place.userRatingsTotal());
                candidates.add(row);
            }
            root.put("candidates", candidates);
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException jsonProcessingException) {
            throw new IllegalStateException(jsonProcessingException);
        }
    }

    public static String userMessageSerp(
            IndustryType industry,
            String contextLabel,
            String targetBrand,
            String targetUrl,
            List<SerpOrganicResult> organics) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("industry", industry.name());
            root.put("industryLabel", industry.getLabel());
            root.put("contextLabel", contextLabel != null ? contextLabel : "");
            root.put("targetBrand", targetBrand != null ? targetBrand : "");
            root.put("targetUrl", targetUrl != null ? targetUrl : "");
            List<Map<String, Object>> candidates = new ArrayList<>();
            List<SerpOrganicResult> safe = organics != null ? organics : List.of();
            for (int i = 0; i < safe.size(); i++) {
                SerpOrganicResult o = safe.get(i);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("index", i);
                row.put("name", o.title());
                row.put("websiteUrl", o.link());
                row.put("snippet", o.snippet());
                row.put("rating", null);
                row.put("userRatingsTotal", null);
                candidates.add(row);
            }
            root.put("candidates", candidates);
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException jsonProcessingException) {
            throw new IllegalStateException(jsonProcessingException);
        }
    }
}
