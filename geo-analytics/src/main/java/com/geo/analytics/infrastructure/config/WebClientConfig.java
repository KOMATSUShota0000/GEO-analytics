package com.geo.analytics.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {
    @Bean
    public WebClient deepSeekWebClient(
            WebClient.Builder webClientBuilder,
            AppProperties appProperties) {
        String baseUrl = appProperties.getAi().getDeepseek().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.deepseek.com";
        }
        return webClientBuilder.baseUrl(baseUrl).build();
    }
}
