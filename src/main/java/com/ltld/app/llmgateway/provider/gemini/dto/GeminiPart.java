package com.ltld.app.llmgateway.provider.gemini.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiPart(String text, Boolean thought) {
    public static GeminiPart ofText(String text) {
        return new GeminiPart(text, null);
    }
}
