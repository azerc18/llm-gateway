package com.ltld.app.llmgateway.controller;

import com.ltld.app.llmgateway.dto.ChatRequest;
import com.ltld.app.llmgateway.dto.ChatResponse;
import com.ltld.app.llmgateway.enums.FinishReason;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ChatController {
    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return ChatResponse.builder()
                .requestId(UUID.randomUUID().toString())
                .provider("stub")
                .model(request.getModel())
                .content("stub response")
                .finishReason(FinishReason.STOP)
                .build();
    }
}

