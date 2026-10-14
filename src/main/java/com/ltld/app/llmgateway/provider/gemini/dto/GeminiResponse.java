package com.ltld.app.llmgateway.provider.gemini.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ltld.app.llmgateway.enums.FinishReason;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiResponse(
        List<Candidate> candidates,
        PromptFeedback promptFeedback,
        UsageMetadata usageMetadata,
        String modelVersion,
        String responseId
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Candidate(
            GeminiContent content,
            String finishReason
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PromptFeedback(String blockReason) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UsageMetadata(
            Integer promptTokenCount,
            Integer candidatesTokenCount,
            Integer thoughtsTokenCount,
            Integer totalTokenCount
    ) {}
}
