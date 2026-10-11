package com.ltld.app.llmgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

@ConfigurationProperties(prefix = "llm")
public record LlmProperties(Map<String, Provider> providers) {
    public record Provider(String baseUrl, String apiKey, Duration timeout) {}

    public Provider provider(String name){
        Provider p = providers == null ? null : providers.get(name);

        if(p == null){
            throw new IllegalStateException("Missing config: llm.providers." + name);
        }
        return p;
    }
}
