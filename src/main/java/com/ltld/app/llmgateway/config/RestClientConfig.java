package com.ltld.app.llmgateway.config;

import com.ltld.app.llmgateway.service.Sleeper;
import com.ltld.app.llmgateway.service.Ticker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Slf4j
@Configuration
public class RestClientConfig {
    @Bean
    RestClient groqRestClient(RestClient.Builder builder, LlmProperties properties) {
        LlmProperties.Provider groq = properties.provider("groq");

        return build(builder,groq,HttpHeaders.AUTHORIZATION,"Bearer " + groq.apiKey());
    }

    @Bean
    RestClient geminiRestClient(RestClient.Builder builder, LlmProperties properties) {
        LlmProperties.Provider gemini = properties.provider("gemini");

        return build(builder,gemini,"x-goog-api-key",gemini.apiKey());
    }

    private RestClient build(RestClient.Builder builder, LlmProperties.Provider provider,
                             String authHeader, String authValue) {

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(provider.timeout());

        return builder
                .baseUrl(provider.baseUrl())
                .defaultHeader(authHeader, authValue)
                .requestFactory(factory)
                .build();
    }

    @Bean
    Sleeper sleeper() {
        return duration -> Thread.sleep(duration);
    }

    @Bean
    Ticker ticker() {
        return System::nanoTime;
    }


}
