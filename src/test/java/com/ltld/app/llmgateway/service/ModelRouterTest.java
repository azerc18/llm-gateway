package com.ltld.app.llmgateway.service;

import com.ltld.app.llmgateway.config.LlmProperties;
import com.ltld.app.llmgateway.exception.AppException;
import com.ltld.app.llmgateway.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ModelRouterTest {

    private final Map<String, LlmProperties.Provider> providers = Map.of(
            "groq",   new LlmProperties.Provider("u", "k", Duration.ofSeconds(1)),
            "gemini", new LlmProperties.Provider("u", "k", Duration.ofSeconds(1))
    );

    private LlmProperties propsWith(Map<String, LlmProperties.Route> routes) {
        return new LlmProperties(providers, routes);
    }

    private static LlmProperties.Route route(String p, String m, String fp, String fm) {
        return new LlmProperties.Route(
                new LlmProperties.Target(p, m),
                fp == null ? null : new LlmProperties.Target(fp, fm));
    }

    @Test
    void resolve_knownAlias_returnsRoute() {
        var router = new ModelRouter(
                propsWith(Map.of("chat-fast", route("groq", "m1", "gemini", "m2"))),
                List.of(new FakeAdapter("groq"), new FakeAdapter("gemini")));

        LlmProperties.Route r = router.resolve("chat-fast");

        assertEquals("groq", r.primary().provider());
        assertEquals("m1", r.primary().model());
        assertEquals("gemini", r.fallback().provider());
    }

    @Test
    void resolve_unknownAlias_throwsModelNotSupported() {
        var router = new ModelRouter(
                propsWith(Map.of("chat-fast", route("groq", "m1", null, null))),
                List.of(new FakeAdapter("groq")));

        AppException ex = assertThrows(AppException.class, () -> router.resolve("gpt-99"));
        assertEquals(ErrorCode.MODEL_NOT_SUPPORTED, ex.getErrorCode());
    }

    @Test
    void resolve_isCaseSensitive() {
        var router = new ModelRouter(
                propsWith(Map.of("chat-fast", route("groq", "m1", null, null))),
                List.of(new FakeAdapter("groq")));

        assertThrows(AppException.class, () -> router.resolve("Chat-Fast"));
    }

    @Test
    void constructor_failsWhenProviderHasNoAdapter() {
        var props = propsWith(Map.of("chat-fast", route("groq", "m1", "gemini", "m2")));

        // chỉ có adapter groq, thiếu gemini
        var ex = assertThrows(IllegalStateException.class,
                () -> new ModelRouter(props, List.of(new FakeAdapter("groq"))));

        assertTrue(ex.getMessage().contains("llm.routes.chat-fast.fallback.provider 'gemini'"));
        assertTrue(ex.getMessage().contains("groq"));          // có liệt kê adapter hiện có
    }
}
