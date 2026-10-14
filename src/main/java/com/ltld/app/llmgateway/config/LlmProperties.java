package com.ltld.app.llmgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

@ConfigurationProperties(prefix = "llm")
public record LlmProperties(Map<String, Provider> providers,
                            Map<String, Route> routes) {
    public LlmProperties {
        providers = (providers == null) ? Map.of() : providers;
        routes = (routes == null) ? Map.of() : routes;

        for (Map.Entry<String, Route> entry : routes.entrySet()) {
            String alias = entry.getKey();
            Route route = entry.getValue();

            if (route == null || route.primary() == null) {
                throw new IllegalStateException("llm.routes." + alias + ".primary is required");
            }
            checkTarget(alias, "primary", route.primary(), providers);
            if (route.fallback() != null) {
                checkTarget(alias, "fallback", route.fallback(), providers);
            }
        }
    }

    private static void checkTarget(String alias, String role, Target t, Map<String, Provider> providers) {
        String path = "llm.routes." + alias + "." + role;

        if (t.provider() == null || t.provider().isBlank()) {
            throw new IllegalStateException(path + ".provider is required");
        }
        if (t.model() == null || t.model().isBlank()) {
            throw new IllegalStateException(path + ".model is required");
        }
        if (!providers.containsKey(t.provider())) {
            throw new IllegalStateException(path + ".provider '" + t.provider()
                    + "' is not defined in llm.providers");
        }
    }

    public Provider provider(String name){
        Provider p = providers == null ? null : providers.get(name);

        if(p == null){
            throw new IllegalStateException("Missing config: llm.providers." + name);
        }
        return p;
    }

    public record Provider(String baseUrl, String apiKey, Duration timeout) {}

    public record Route(Target primary, Target fallback) {}

    public record Target(String provider, String model) {}

}
