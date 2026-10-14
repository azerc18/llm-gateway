package com.ltld.app.llmgateway.provider.gemini.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GeminiRequest(
        GeminiContent systemInstruction,
        List<GeminiContent> contents,
        GenerationConfig generationConfig
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GenerationConfig(
            Integer maxOutputTokens,
            Double temperature,
            ThinkingConfig thinkingConfig
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ThinkingConfig(String thinkingLevel) {}
}
