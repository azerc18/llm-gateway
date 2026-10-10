package com.ltld.app.llmgateway.controller;

import com.ltld.app.llmgateway.config.ApiKeyAuthFilter;
import com.ltld.app.llmgateway.config.RequestIdFilter;
import com.ltld.app.llmgateway.dto.chatdto.ChatRequest;
import com.ltld.app.llmgateway.dto.chatdto.ChatResponse;
import com.ltld.app.llmgateway.entity.ApiClient;
import com.ltld.app.llmgateway.enums.FinishReason;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ChatController {
    @PostMapping("/chat")
    public ChatResponse chat(
            @Valid @RequestBody ChatRequest request,
            @RequestAttribute(RequestIdFilter.ATTRIBUTE) String requestId,
            @RequestAttribute(ApiKeyAuthFilter.CLIENT_ATTRIBUTE) ApiClient client) {
        return ChatResponse.builder()
                .requestId(requestId)
                .provider("stub")
                .model(request.getModel())
                .content("stub response")
                .finishReason(FinishReason.STOP)
                .build();
    }
}

