package com.ltld.app.llmgateway.dto;

import com.ltld.app.llmgateway.enums.FinishReason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatResponse {
    private String requestId;
    private String provider;
    private String model;
    private String content;
    private FinishReason finishReason;
    private Usage usage;
    private long latencyMs;
    private boolean fallbackUsed;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Usage {
        private long inputTokens;
        private Long reasoningTokens;
        private long outputTokens;
        private long totalTokens;
    }
}
