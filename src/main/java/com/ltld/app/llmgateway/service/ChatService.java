package com.ltld.app.llmgateway.service;

import com.ltld.app.llmgateway.config.LlmProperties;
import com.ltld.app.llmgateway.dto.chatdto.ChatRequest;
import com.ltld.app.llmgateway.dto.chatdto.ChatResponse;
import com.ltld.app.llmgateway.entity.ApiClient;
import com.ltld.app.llmgateway.enums.FinishReason;
import com.ltld.app.llmgateway.provider.ProviderAdapter;
import com.ltld.app.llmgateway.provider.ProviderResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ChatService {
    private final Map<String, ProviderAdapter> adapters;
    private final ModelRouter router;

    public ChatService(List<ProviderAdapter> adapterList, ModelRouter router) {
        this.adapters = adapterList.stream()
                .collect(Collectors.toMap(ProviderAdapter::name, a -> a));
        this.router = router;
    }

    public ChatResponse chat(ChatRequest request, ApiClient client, String requestId) {
        long start = System.nanoTime();

        LlmProperties.Route route = router.resolve(request.getModel());
        LlmProperties.Target target = route.primary();

        ProviderAdapter adapter = adapters.get(target.provider());
        ProviderResult result = adapter.chat(request, target.model());

        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        log.info("chat ok provider={} model={} latencyMs={} totalTokens={} finish={}",
                result.provider(), result.model(), latencyMs,
                result.usage().getTotalTokens(), result.finishReason());

        if (result.finishReason() == FinishReason.LENGTH) {
            log.warn("response truncated by maxTokens={}", request.getMaxTokens());
        }

        return ChatResponse.builder()
                .requestId(requestId)
                .provider(result.provider())
                .model(result.model())
                .content(result.content())
                .finishReason(result.finishReason())
                .usage(result.usage())
                .latencyMs(latencyMs)
                .fallbackUsed(false)
                .build();
    }
}
