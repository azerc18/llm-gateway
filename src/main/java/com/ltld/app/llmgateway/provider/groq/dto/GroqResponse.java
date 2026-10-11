package com.ltld.app.llmgateway.provider.groq.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GroqResponse(
        String id,
        String model,
        List<Choice> choices,
        Usage usage
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(Message message, @JsonProperty("finish_reason") String finishReason) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String role, String content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
             @JsonProperty("prompt_tokens") Integer promptTokens,
             @JsonProperty("completion_tokens") Integer completionTokens,
             @JsonProperty("total_tokens") Integer totalTokens,
             @JsonProperty("completion_tokens_details") CompletionTokensDetails completionTokensDetails
            ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CompletionTokensDetails(@JsonProperty("reasoning_tokens") Integer reasoningTokens) {}
}
