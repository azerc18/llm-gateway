package com.ltld.app.llmgateway.provider;

import com.ltld.app.llmgateway.dto.chatdto.ChatResponse;
import com.ltld.app.llmgateway.enums.FinishReason;

public record ProviderResult(
        String provider,
        String model,
        String content,
        FinishReason finishReason,
        ChatResponse.Usage usage
) {}
