package com.ltld.app.llmgateway.service;

import com.ltld.app.llmgateway.dto.chatdto.ChatRequest;
import com.ltld.app.llmgateway.dto.chatdto.ChatResponse;
import com.ltld.app.llmgateway.enums.FinishReason;
import com.ltld.app.llmgateway.provider.ProviderAdapter;
import com.ltld.app.llmgateway.provider.ProviderResult;

record FakeAdapter(String name, long delayMs) implements ProviderAdapter {

    FakeAdapter(String name) { this(name, 0); }

    @Override
    public ProviderResult chat(ChatRequest request, String providerModel) {
        if (delayMs > 0) {
            try { Thread.sleep(delayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        ChatResponse.Usage usage = ChatResponse.Usage.builder()
                .inputTokens(1).outputTokens(1).totalTokens(2).build();
        return new ProviderResult(name, providerModel, "ok from " + name, FinishReason.STOP, usage);
    }
}
