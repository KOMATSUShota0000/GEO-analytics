package com.geo.analytics.infrastructure.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.ExtractedPlace;
import com.geo.analytics.domain.enums.IndustryType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CompetitorFilterPrompts {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private CompetitorFilterPrompts() {
    }

    public static String systemMessage() {
        return "あなたはGEO競合スカウトです。選ぶのは地域に根ざした単一事業者に限定し、実店舗または単一ブランドで運営されるビジネスを優先してください。一覧や比較を主業とする集約型プラットフォームよりも、現場でサービスを提供する規模感の近い候補を選んでください。"
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
}
