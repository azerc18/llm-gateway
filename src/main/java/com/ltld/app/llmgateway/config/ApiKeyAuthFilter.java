package com.ltld.app.llmgateway.config;

import com.ltld.app.llmgateway.entity.ApiClient;
import com.ltld.app.llmgateway.enums.ClientStatus;
import com.ltld.app.llmgateway.exception.ErrorCode;
import com.ltld.app.llmgateway.exception.ErrorResponse;
import com.ltld.app.llmgateway.repository.ApiClientRepository;
import com.ltld.app.llmgateway.util.ApiKeyHasher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-API-Key";

    private final ApiClientRepository apiClientRepository;
    private final JsonMapper jsonMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String rawKey = request.getHeader(HEADER);
        if(rawKey == null || rawKey.isBlank()){
            writeUnauthorized(request,response);
            return;
        }

        String apiKeyHash = ApiKeyHasher.sha256Hex(rawKey);
        Optional<ApiClient> apiClient = apiClientRepository.findByApiKeyHash(apiKeyHash)
                .filter(client -> client.getStatus() == ClientStatus.ACTIVE);

        if(apiClient.isEmpty()){
            writeUnauthorized(request,response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        ErrorCode code = ErrorCode.UNAUTHORIZED;

        ErrorResponse body = new ErrorResponse();
        body.setErrorCode(code.getCode());
        body.setMessage(code.getMessage());
        body.setStatus(code.getStatusCode().value());
        body.setRequestId((String) request.getAttribute(RequestIdFilter.ATTRIBUTE));

        response.setStatus(code.getStatusCode().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getWriter(), body);
    }
}
