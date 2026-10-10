package com.ltld.app.llmgateway.provider.groq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GroqRequest(
        String model,
        List<GroqMessage> messages,
        @JsonProperty("max_tokens") Integer maxTokens,
        Double temperature
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GroqMessage(String role, String content) {}
}

