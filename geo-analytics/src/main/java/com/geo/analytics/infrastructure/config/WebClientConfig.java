package com.geo.analytics.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {
    @Bean
    public WebClient deepSeekWebClient(
            WebClient.Builder webClientBuilder,
            @Value("${app.ai.deepseek.base-url:https://api.deepseek.com}") String baseUrl) {
        return webClientBuilder.baseUrl(baseUrl).build();
    }
}
