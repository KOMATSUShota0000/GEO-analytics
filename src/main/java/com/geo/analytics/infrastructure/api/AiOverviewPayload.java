package com.geo.analytics.infrastructure.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Locale;

/**
 * SerpAPI レスポンスから AI Overview の本文を取り出す純粋ロジック。
 *
 * <p>Why: 旧実装はレスポンス JSON 全体を再帰的に走査してブランド名の出現を数えていた。
 * organic_results のタイトル・URL、ページネーション URL、検索語そのもの（search_parameters.q）まで
 * 対象に入るため、「AI 回答の中での言及回数」とは無関係な値になっていた。実測では 56 回のうち
 * AI Overview 本文由来が 0 回だった。数える対象を text_blocks に限定する。
 */
public final class AiOverviewPayload {

    private AiOverviewPayload() {}

    /** SerpAPI が AI Overview 本文を後読みで返す場合の引換券。1分で失効するため即座に使う。 */
    public static String pageTokenOrNull(JsonNode root) {
        JsonNode aiOverview = aiOverviewNode(root);
        if (aiOverview == null) {
            return null;
        }
        JsonNode token = aiOverview.get("page_token");
        return token != null && token.isTextual() && !token.asText().isBlank() ? token.asText() : null;
    }

    public static boolean hasTextBlocks(JsonNode root) {
        JsonNode aiOverview = aiOverviewNode(root);
        if (aiOverview == null) {
            return false;
        }
        JsonNode blocks = aiOverview.get("text_blocks");
        return blocks != null && blocks.isArray() && !blocks.isEmpty();
    }

    /**
     * AI Overview 本文を平文へ組み立てる。paragraph / heading は snippet を、list は各要素の
     * title と snippet を拾う。取得できなければ空文字。
     */
    public static String bodyText(JsonNode root) {
        JsonNode aiOverview = aiOverviewNode(root);
        if (aiOverview == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(512);
        appendBlocks(aiOverview.get("text_blocks"), sb);
        return sb.toString().strip();
    }

    /** 本文中にブランド名が現れた回数。大文字小文字は無視し、重なりは数えない。 */
    public static int countBrandMentions(String bodyText, String brandName) {
        if (bodyText == null || bodyText.isBlank() || brandName == null || brandName.isBlank()) {
            return 0;
        }
        String haystack = bodyText.toLowerCase(Locale.ROOT);
        String needle = brandName.strip().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return 0;
        }
        int count = 0;
        int from = 0;
        while (true) {
            int found = haystack.indexOf(needle, from);
            if (found < 0) {
                break;
            }
            count++;
            from = found + needle.length();
        }
        return count;
    }

    private static JsonNode aiOverviewNode(JsonNode root) {
        if (root == null || root.isNull()) {
            return null;
        }
        JsonNode aiOverview = root.get("ai_overview");
        return aiOverview == null || aiOverview.isNull() || !aiOverview.isObject() ? null : aiOverview;
    }

    private static void appendBlocks(JsonNode blocks, StringBuilder sink) {
        if (blocks == null || !blocks.isArray()) {
            return;
        }
        for (JsonNode block : blocks) {
            if (block == null || !block.isObject()) {
                continue;
            }
            appendText(block.get("snippet"), sink);
            JsonNode list = block.get("list");
            if (list != null && list.isArray()) {
                for (JsonNode item : list) {
                    if (item == null || !item.isObject()) {
                        continue;
                    }
                    appendText(item.get("title"), sink);
                    appendText(item.get("snippet"), sink);
                    // Why: SerpAPI は list の要素がさらに入れ子の text_blocks を持つ形を返しうる。
                    appendBlocks(item.get("list"), sink);
                }
            }
        }
    }

    private static void appendText(JsonNode node, StringBuilder sink) {
        if (node == null || !node.isTextual()) {
            return;
        }
        String text = node.asText().strip();
        if (text.isEmpty()) {
            return;
        }
        if (!sink.isEmpty()) {
            sink.append('\n');
        }
        sink.append(text);
    }
}
