package com.geo.analytics.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobQueryGenerationServiceTest {

    private final ChatLanguageModel model = mock(ChatLanguageModel.class);
    private final JobQueryGenerationService service =
            new JobQueryGenerationService(model, new ObjectMapper());

    @Test
    void buildBrandQuery_combinesBrandAndStripsWww() {
        assertEquals(
                "おにぎりこんが fbih.jp",
                JobQueryGenerationService.buildBrandQuery("おにぎりこんが", "https://www.fbih.jp/"));
    }

    @Test
    void buildBrandQuery_fallsBackWhenEmpty() {
        assertEquals("GEO", JobQueryGenerationService.buildBrandQuery("", ""));
        assertEquals("ブランド", JobQueryGenerationService.buildBrandQuery("ブランド", null));
    }

    @Test
    void generate_fallsBackToBrandQueryWhenLlmFails() {
        when(model.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("llm down"));
        List<String> result = service.generate("おにぎりこんが", "https://fbih.jp/", null, null, null, 3);
        // LLM 失敗でもブランド軸クエリ1本で解析を継続できること。
        assertEquals(List.of("おにぎりこんが fbih.jp"), result);
    }

    @Test
    void generate_putsBrandFirstDedupesAndTrims() {
        String json =
                "{\"queries\":[\"おにぎりこんが fbih.jp\",\"おにぎり 専門店\",\"おにぎり 専門店\",\"こんが 店舗\",\"こんが 評判\"]}";
        when(model.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(json)).build());
        List<String> result =
                service.generate("おにぎりこんが", "https://fbih.jp/", "おにぎり専門店", null, null, 3);
        // 先頭はブランド軸・重複排除・desiredCount にトリムされること。
        assertEquals(3, result.size());
        assertEquals("おにぎりこんが fbih.jp", result.get(0));
        assertEquals(result.size(), (int) result.stream().distinct().count());
    }
}
