package com.ltld.app.llmgateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class RestClientConfig {
    @Bean
    RestClient groqRestClient(RestClient.Builder builder, LlmProperties properties) {
        LlmProperties.Provider groq = properties.provider("groq");

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(groq.timeout());

        return builder
                .baseUrl(groq.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + groq.apiKey())
                .requestFactory(factory)
                .build();
    }
}
