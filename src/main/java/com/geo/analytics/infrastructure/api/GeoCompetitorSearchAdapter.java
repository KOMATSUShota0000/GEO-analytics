package com.geo.analytics.infrastructure.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.geo.analytics.domain.support.TextWhitespaceNormalizer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.SgeMentionResult;
import com.geo.analytics.application.port.SgeMeasurementPort;
import com.geo.analytics.infrastructure.api.dto.SerpApiResponse;
import com.geo.analytics.infrastructure.api.dto.SerpOrganicResult;
import com.geo.analytics.infrastructure.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import java.lang.StrictMath;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class GeoCompetitorSearchAdapter implements SgeMeasurementPort {
    private static final Logger log = LoggerFactory.getLogger(GeoCompetitorSearchAdapter.class);
    private static final String SERPAPI_SEARCH_URL = "https://serpapi.com/search.json";
    private static final Duration SERP_CONNECT_TIMEOUT = Duration.ofSeconds(15);
    /** AI Overview 等の AI visibility 応答は JSON が大きくなり得るため読み取りタイムアウトを長めに設定する。 */
    private static final Duration SERP_READ_TIMEOUT = Duration.ofSeconds(90);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String serpApiKey;

    public GeoCompetitorSearchAdapter(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            AppProperties appProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) SERP_CONNECT_TIMEOUT.toMillis());
        requestFactory.setReadTimeout((int) SERP_READ_TIMEOUT.toMillis());
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
        String key = appProperties.getSerpapi().getApiKey();
        this.serpApiKey = key != null ? key : "";
    }

    private URI buildSerpUri(String searchQuery, Integer num) {
        var builder = UriComponentsBuilder
            .fromUriString(SERPAPI_SEARCH_URL)
            .queryParam("engine", "google")
            .queryParam("google_domain", "google.co.jp")
            .queryParam("hl", "ja")
            .queryParam("gl", "jp")
            .queryParam("q", searchQuery)
            .queryParam("api_key", serpApiKey);
        if (num != null && num > 0) {
            builder.queryParam("num", num);
        }
        return builder.encode(StandardCharsets.UTF_8).build().toUri();
    }

    public List<SerpOrganicResult> fetchOrganicResults(String searchQuery, int num) {
        if (serpApiKey.isBlank()) {
            throw new IllegalStateException("AI visibility provider API key is not configured (app.serpapi.api-key)");
        }
        String trimmed = searchQuery == null ? "" : searchQuery.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("searchQuery");
        }
        int n = num > 0 ? num : 15;
        URI uri = buildSerpUri(trimmed, n);
        String body;
        try {
            body = restClient.get().uri(uri).retrieve().body(String.class);
        } catch (RestClientResponseException restClientResponseException) {
            throw new SerpApiHttpException(
                    restClientResponseException.getStatusCode().value(),
                    restClientResponseException.getStatusText(),
                    restClientResponseException.getResponseBodyAsString(StandardCharsets.UTF_8),
                    restClientResponseException);
        }
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("AI visibility provider returned empty body");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JsonProcessingException jsonProcessingException) {
            throw new IllegalStateException(jsonProcessingException);
        }
        return parseOrganicResults(root);
    }

    private static List<SerpOrganicResult> parseOrganicResults(JsonNode root) {
        if (root == null || root.isNull()) {
            return List.of();
        }
        JsonNode organic = root.get("organic_results");
        if (organic == null || !organic.isArray()) {
            return List.of();
        }
        List<SerpOrganicResult> out = new ArrayList<>();
        for (JsonNode node : organic) {
            if (node == null || node.isNull()) {
                continue;
            }
            String title = readText(node, "title");
            String link = readText(node, "link");
            String snippet = readText(node, "snippet");
            if (link == null || link.isBlank()) {
                continue;
            }
            out.add(new SerpOrganicResult(
                    title != null ? title : "",
                    link.trim(),
                    snippet != null ? snippet : ""));
        }
        return List.copyOf(out);
    }

    private static String readText(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || !v.isTextual()) {
            return "";
        }
        return v.asText();
    }

    @Override
    public SgeMentionResult checkSgeMention(String query, String brandName) {
        if (serpApiKey.isBlank()) {
            throw new IllegalStateException("AI visibility provider API key is not configured (app.serpapi.api-key)");
        }
        // Why: 旧実装は GeoCompetitorQueryBuilder で「ブランド名 + クエリ」を検索していた。検索語にブランド名を
        //      入れれば AI 回答へ自社が出るのは当たり前で、測定が自作自演になる。実測でもブランド名を混ぜた
        //      場合は page_token のみが返り本文が取れず、クエリのみなら text_blocks が直接返った（ADR-039）。
        //      ユーザーが実際に打つクエリのみで検索する。
        String searchQuery = TextWhitespaceNormalizer.normalize(query);
        if (searchQuery == null || searchQuery.isBlank()) {
            log.warn("AI visibility request skipped: empty query brand=\"{}\" userKeyword=\"{}\"", brandName, query);
            throw new IllegalArgumentException("AI visibility query must not be blank");
        }
        URI uri = buildSerpUri(searchQuery, null);
        String body;
        try {
            body = restClient.get()
                .uri(uri)
                .retrieve()
                .body(String.class);
        } catch (RestClientResponseException restClientResponseException) {
            String errorBody = restClientResponseException.getResponseBodyAsString(StandardCharsets.UTF_8);
            if (errorBody == null) {
                errorBody = "";
            }
            log.warn(
                    "AI visibility provider HTTP error status={} searchQuery=\"{}\" brand=\"{}\" userKeyword=\"{}\"",
                    restClientResponseException.getStatusCode().value(),
                    searchQuery,
                    brandName,
                    query);
            throw new SerpApiHttpException(
                restClientResponseException.getStatusCode().value(),
                restClientResponseException.getStatusText(),
                errorBody,
                restClientResponseException);
        }
        if (body == null || body.isBlank()) {
            log.warn(
                    "AI visibility provider returned empty body searchQuery=\"{}\" brand=\"{}\" userKeyword=\"{}\"",
                    searchQuery,
                    brandName,
                    query);
            throw new IllegalStateException("AI visibility provider returned empty body");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JsonProcessingException jsonProcessingException) {
            log.warn(
                    "AI visibility provider JSON parse failed searchQuery=\"{}\" brand=\"{}\" userKeyword=\"{}\"",
                    searchQuery,
                    brandName,
                    query,
                    jsonProcessingException);
            throw new IllegalStateException(jsonProcessingException);
        }
        int organicCount = countOrganicResults(root);
        log.info(
                "Measuring AI visibility: found {} AI visibility evidence (organic_results) snippets for searchQuery=\"{}\" (brand=\"{}\", userKeyword=\"{}\")",
                organicCount,
                searchQuery,
                brandName,
                query);
        if (organicCount == 0) {
            log.warn(
                    "AI visibility provider returned zero AI visibility evidence (organic_results) (no AI Overview snippets). searchQuery=\"{}\" brand=\"{}\" userKeyword=\"{}\"",
                    searchQuery,
                    brandName,
                    query);
        }
        SerpApiResponse response = objectMapper.convertValue(root, SerpApiResponse.class);
        if (!hasMinimalSerpStructure(response)) {
            log.warn(
                    "AI visibility provider response lacks usable blocks (organic/ai_overview/answer_box/related). searchQuery=\"{}\" brand=\"{}\" userKeyword=\"{}\" organic_results={}",
                    searchQuery,
                    brandName,
                    query,
                    organicCount);
            return new SgeMentionResult(false, 0, body);
        }
        JsonNode resolvedRoot = resolveAiOverviewBody(root, searchQuery);
        String overviewBody = AiOverviewPayload.bodyText(resolvedRoot);
        if (overviewBody.isEmpty()) {
            log.info(
                    "AI Overview body unavailable for searchQuery=\"{}\" (no text_blocks and no resolvable page_token)",
                    searchQuery);
        }
        int mentionCount = AiOverviewPayload.countBrandMentions(overviewBody, brandName);
        return new SgeMentionResult(mentionCount > 0, mentionCount, writeJsonOrFallback(resolvedRoot, body));
    }

    /**
     * AI Overview 本文が page_token でしか返らない場合、google_ai_overview エンジンへ2回目を投げて
     * 解決済みの ai_overview で差し替える。
     *
     * <p>Why: SerpAPI は AI Overview を本文（text_blocks）で返す場合と、引換券（page_token）で返す場合がある。
     * 旧実装は page_token を扱わず1回目のレスポンスしか見ていなかったため、後者では本文を取得できていなかった。
     * トークンは1分で失効するため即座に使う。失敗しても測定全体は落とさず、本文なしとして続行する。
     */
    private JsonNode resolveAiOverviewBody(JsonNode root, String searchQuery) {
        if (AiOverviewPayload.hasTextBlocks(root)) {
            return root;
        }
        String pageToken = AiOverviewPayload.pageTokenOrNull(root);
        if (pageToken == null) {
            return root;
        }
        try {
            String followUp = restClient.get().uri(buildAiOverviewUri(pageToken)).retrieve().body(String.class);
            if (followUp == null || followUp.isBlank()) {
                return root;
            }
            JsonNode followUpRoot = objectMapper.readTree(followUp);
            JsonNode aiOverview = followUpRoot.get("ai_overview");
            if (aiOverview == null || aiOverview.isNull()) {
                return root;
            }
            if (root instanceof ObjectNode objectRoot) {
                objectRoot.set("ai_overview", aiOverview);
            }
            return root;
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn(
                    "AI Overview page_token follow-up failed searchQuery=\"{}\" reason={}",
                    searchQuery,
                    exception.toString());
            return root;
        }
    }

    private URI buildAiOverviewUri(String pageToken) {
        return UriComponentsBuilder.fromUriString(SERPAPI_SEARCH_URL)
                .queryParam("engine", "google_ai_overview")
                .queryParam("page_token", pageToken)
                .queryParam("api_key", serpApiKey)
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();
    }

    private String writeJsonOrFallback(JsonNode node, String fallback) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            return fallback;
        }
    }

    private static int countOrganicResults(JsonNode root) {
        if (root == null || root.isNull()) {
            return 0;
        }
        JsonNode organic = root.get("organic_results");
        if (organic != null && organic.isArray()) {
            return organic.size();
        }
        return 0;
    }

    private static boolean hasMinimalSerpStructure(SerpApiResponse response) {
        if (response == null) {
            return false;
        }
        if (response.aiOverview() != null && !response.aiOverview().isNull()) {
            return true;
        }
        return jsonNodeHasPayload(response.organicResults())
            || jsonNodeHasPayload(response.answerBox())
            || jsonNodeHasPayload(response.relatedQuestions());
    }

    private static boolean jsonNodeHasPayload(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return false;
        }
        if (node.isArray()) {
            return node.size() > 0;
        }
        if (node.isObject()) {
            return node.size() > 0;
        }
        return node.isTextual() && !node.asText().isBlank();
    }

}
