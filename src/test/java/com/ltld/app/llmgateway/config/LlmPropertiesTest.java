package com.ltld.app.llmgateway.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LlmPropertiesTest {

    private final Map<String, LlmProperties.Provider> providers = Map.of(
            "groq",   new LlmProperties.Provider("https://groq.test", "k", Duration.ofSeconds(30)),
            "gemini", new LlmProperties.Provider("https://gemini.test", "k", Duration.ofSeconds(30))
    );

    private static LlmProperties.Target t(String provider, String model) {
        return new LlmProperties.Target(provider, model);
    }

    private static LlmProperties.Route route(LlmProperties.Target primary, LlmProperties.Target fallback) {
        return new LlmProperties.Route(primary, fallback);
    }

    @Test
    void validRoutes_areAccepted() {
        var routes = Map.of("chat-fast", route(t("groq", "m1"), t("gemini", "m2")));
        assertDoesNotThrow(() -> new LlmProperties(providers, routes));
    }

    @Test
    void routeWithoutFallback_isAccepted() {
        var routes = Map.of("chat-fast", route(t("groq", "m1"), null));
        assertDoesNotThrow(() -> new LlmProperties(providers, routes));
    }

    @Test
    void nullRoutes_becomesEmptyMap() {
        LlmProperties props = new LlmProperties(providers, null);
        assertNotNull(props.routes());
        assertTrue(props.routes().isEmpty());
    }

    @Test
    void missingPrimary_fails() {
        var routes = Map.of("chat-fast", route(null, t("gemini", "m2")));
        var ex = assertThrows(IllegalStateException.class, () -> new LlmProperties(providers, routes));
        assertTrue(ex.getMessage().contains("llm.routes.chat-fast.primary is required"));
    }

    @Test
    void blankModel_fails() {
        var routes = Map.of("chat-fast", route(t("groq", " "), null));
        var ex = assertThrows(IllegalStateException.class, () -> new LlmProperties(providers, routes));
        assertTrue(ex.getMessage().contains("llm.routes.chat-fast.primary.model is required"));
    }

    @Test
    void unknownPrimaryProvider_fails() {
        var routes = Map.of("chat-fast", route(t("grok", "m1"), null));
        var ex = assertThrows(IllegalStateException.class, () -> new LlmProperties(providers, routes));
        assertTrue(ex.getMessage().contains("llm.routes.chat-fast.primary.provider 'grok'"));
    }

    @Test
    void unknownFallbackProvider_fails() {
        var routes = Map.of("chat-fast", route(t("groq", "m1"), t("openai", "m2")));
        var ex = assertThrows(IllegalStateException.class, () -> new LlmProperties(providers, routes));
        assertTrue(ex.getMessage().contains("llm.routes.chat-fast.fallback.provider 'openai'"));
    }
}
