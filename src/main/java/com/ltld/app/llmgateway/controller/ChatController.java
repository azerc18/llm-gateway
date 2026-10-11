package com.ltld.app.llmgateway.controller;

import com.ltld.app.llmgateway.config.ApiKeyAuthFilter;
import com.ltld.app.llmgateway.config.RequestIdFilter;
import com.ltld.app.llmgateway.dto.chatdto.ChatRequest;
import com.ltld.app.llmgateway.dto.chatdto.ChatResponse;
import com.ltld.app.llmgateway.entity.ApiClient;
import com.ltld.app.llmgateway.enums.FinishReason;
import com.ltld.app.llmgateway.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;


    @PostMapping("/chat")
    public ChatResponse chat(
            @Valid @RequestBody ChatRequest request,
            @RequestAttribute(RequestIdFilter.ATTRIBUTE) String requestId,
            @RequestAttribute(ApiKeyAuthFilter.CLIENT_ATTRIBUTE) ApiClient client) {
        return chatService.chat(request, client, requestId);
    }
}

