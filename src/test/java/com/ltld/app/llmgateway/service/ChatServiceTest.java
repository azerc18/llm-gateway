package com.ltld.app.llmgateway.service;

import com.ltld.app.llmgateway.config.LlmProperties;
import com.ltld.app.llmgateway.dto.chatdto.ChatRequest;
import com.ltld.app.llmgateway.dto.chatdto.ChatResponse;
import com.ltld.app.llmgateway.enums.Role;
import com.ltld.app.llmgateway.exception.AppException;
import com.ltld.app.llmgateway.exception.ErrorCode;
import com.ltld.app.llmgateway.provider.ProviderAdapter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChatServiceTest {

    private ChatService serviceWith(ProviderAdapter... adapters) {
        var props = new LlmProperties(
                Map.of("groq",   new LlmProperties.Provider("u", "k", Duration.ofSeconds(1)),
                        "gemini", new LlmProperties.Provider("u", "k", Duration.ofSeconds(1))),
                Map.of("chat-fast",   new LlmProperties.Route(new LlmProperties.Target("groq", "g-model"), null),
                        "chat-gemini", new LlmProperties.Route(new LlmProperties.Target("gemini", "m-model"), null)));
        List<ProviderAdapter> list = List.of(adapters);
        return new ChatService(list, new ModelRouter(props, list));
    }

    private static ChatRequest request(String alias) {
        return new ChatRequest(alias, List.of(new ChatRequest.Message(Role.USER, "hi")), 100, null);
    }

    @Test
    void routesAliasToCorrectProvider() {
        var service = serviceWith(new FakeAdapter("groq"), new FakeAdapter("gemini"));

        ChatResponse res = service.chat(request("chat-gemini"), null, "req_1");

        assertEquals("gemini", res.getProvider());
        assertEquals("m-model", res.getModel());
        assertEquals("req_1", res.getRequestId());
        assertFalse(res.isFallbackUsed());
    }

    @Test
    void unknownAlias_throwsBeforeCallingProvider() {
        var service = serviceWith(new FakeAdapter("groq"), new FakeAdapter("gemini"));

        AppException ex = assertThrows(AppException.class,
                () -> service.chat(request("gpt-99"), null, "req_1"));
        assertEquals(ErrorCode.MODEL_NOT_SUPPORTED, ex.getErrorCode());
    }

    @Test
    void latencyIncludesProviderCall() {
        var service = serviceWith(new FakeAdapter("groq", 50), new FakeAdapter("gemini"));

        ChatResponse res = service.chat(request("chat-fast"), null, "req_1");

        assertTrue(res.getLatencyMs() >= 50,
                "latencyMs phải gồm cả thời gian gọi provider, nhưng là " + res.getLatencyMs());
    }
}
