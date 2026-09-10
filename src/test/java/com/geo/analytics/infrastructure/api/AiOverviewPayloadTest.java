package com.geo.analytics.infrastructure.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** #71: 数える対象を AI Overview 本文に限定したことを固定する。 */
class AiOverviewPayloadTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void 本文はparagraphとheadingとlistから組み立てる() {
        var root = parse(
                """
                {"ai_overview":{"text_blocks":[
                  {"type":"paragraph","snippet":"おすすめはfreeeです。"},
                  {"type":"heading","snippet":"3選"},
                  {"type":"list","list":[
                    {"title":"freee","snippet":"freee会計"},
                    {"snippet":"弥生会計"}]}]}}
                """);

        assertThat(AiOverviewPayload.bodyText(root))
                .contains("おすすめはfreeeです。")
                .contains("3選")
                .contains("freee会計")
                .contains("弥生会計");
    }

    @Test
    void 本文の外にあるブランド名は数えない() {
        var root = parse(
                """
                {"search_parameters":{"q":"freee クラウド会計ソフト おすすめ"},
                 "organic_results":[{"title":"freee公式","link":"https://freee.co.jp","snippet":"freeeのサイト"}],
                 "ai_overview":{"text_blocks":[{"type":"paragraph","snippet":"会計ソフトは複数あります。"}]}}
                """);

        String body = AiOverviewPayload.bodyText(root);

        assertThat(body).isEqualTo("会計ソフトは複数あります。");
        assertThat(AiOverviewPayload.countBrandMentions(body, "freee")).isZero();
    }

    @Test
    void 本文中の出現回数を数える() {
        var root = parse(
                """
                {"ai_overview":{"text_blocks":[
                  {"type":"paragraph","snippet":"freeeは人気です。Freeeの自動仕訳が便利。"}]}}
                """);

        assertThat(AiOverviewPayload.countBrandMentions(AiOverviewPayload.bodyText(root), "freee"))
                .isEqualTo(2);
    }

    @Test
    void text_blocksがあればpage_tokenは不要と判定する() {
        var withBlocks = parse("{\"ai_overview\":{\"text_blocks\":[{\"snippet\":\"本文\"}]}}");

        assertThat(AiOverviewPayload.hasTextBlocks(withBlocks)).isTrue();
        assertThat(AiOverviewPayload.pageTokenOrNull(withBlocks)).isNull();
    }

    @Test
    void page_tokenのみの場合は本文が空でトークンを返す() {
        var tokenOnly = parse("{\"ai_overview\":{\"page_token\":\"abc123\",\"serpapi_link\":\"https://example\"}}");

        assertThat(AiOverviewPayload.hasTextBlocks(tokenOnly)).isFalse();
        assertThat(AiOverviewPayload.pageTokenOrNull(tokenOnly)).isEqualTo("abc123");
        assertThat(AiOverviewPayload.bodyText(tokenOnly)).isEmpty();
    }

    @Test
    void ai_overviewが無ければ空を返す() {
        var none = parse("{\"organic_results\":[{\"title\":\"freee\"}]}");

        assertThat(AiOverviewPayload.hasTextBlocks(none)).isFalse();
        assertThat(AiOverviewPayload.pageTokenOrNull(none)).isNull();
        assertThat(AiOverviewPayload.bodyText(none)).isEmpty();
        assertThat(AiOverviewPayload.countBrandMentions("", "freee")).isZero();
    }
}
