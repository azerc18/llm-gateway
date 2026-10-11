package com.ltld.app.llmgateway.provider;

import com.ltld.app.llmgateway.dto.chatdto.ChatRequest;

public interface ProviderAdapter {
    String name();

    ProviderResult chat(ChatRequest request, String providerModel);
}
