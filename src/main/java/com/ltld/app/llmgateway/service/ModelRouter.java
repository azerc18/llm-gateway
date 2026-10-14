package com.ltld.app.llmgateway.service;

import com.ltld.app.llmgateway.config.LlmProperties;
import com.ltld.app.llmgateway.exception.AppException;
import com.ltld.app.llmgateway.exception.ErrorCode;
import com.ltld.app.llmgateway.provider.ProviderAdapter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ModelRouter {

    private final Map<String, LlmProperties.Route> routes;

    public ModelRouter(LlmProperties props, List<ProviderAdapter> adapters) {
        Set<String> adapterNames = adapters.stream()
                .map(ProviderAdapter::name)
                .collect(Collectors.toSet());

        props.routes().forEach((alias, route) -> {
            requireAdapter(alias, "primary", route.primary(), adapterNames);
            if (route.fallback() != null) {
                requireAdapter(alias, "fallback", route.fallback(), adapterNames);
            }
        });

        this.routes = props.routes();
    }

    private static void requireAdapter(String alias, String role,
                                       LlmProperties.Target target, Set<String> adapterNames) {
        if (!adapterNames.contains(target.provider())) {
            throw new IllegalStateException("llm.routes." + alias + "." + role
                    + ".provider '" + target.provider() + "' has no ProviderAdapter bean. Available: "
                    + adapterNames);
        }
    }

    public LlmProperties.Route resolve(String alias) {
        LlmProperties.Route route = routes.get(alias);
        if (route == null) {
            throw new AppException(ErrorCode.MODEL_NOT_SUPPORTED);
        }
        return route;
    }
}
