package com.ltld.app.llmgateway.service;

import com.ltld.app.llmgateway.dto.chatdto.ChatRequest;
import com.ltld.app.llmgateway.dto.chatdto.ChatResponse;
import com.ltld.app.llmgateway.entity.ApiClient;
import com.ltld.app.llmgateway.provider.ProviderResult;
import com.ltld.app.llmgateway.provider.groq.GroqAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {
    private static final String GROQ_MODEL = "openai/gpt-oss-120b";

    private final GroqAdapter groqAdapter;

    public ChatResponse chat(ChatRequest request, ApiClient client, String requestId) {
        long start = System.nanoTime();

        ProviderResult result = groqAdapter.chat(request, GROQ_MODEL);

        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        log.info("requestId: " + requestId);

        return ChatResponse.builder()
                .requestId(requestId)
                .provider(result.provider())
                .model(GROQ_MODEL)
                .content(result.content())
                .finishReason(result.finishReason())
                .usage(result.usage())
                .latencyMs(latencyMs)
                .fallbackUsed(false)
                .build();
    }
}
