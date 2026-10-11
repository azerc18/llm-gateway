package com.ltld.app.llmgateway.provider.groq;

import com.ltld.app.llmgateway.dto.chatdto.ChatRequest;
import com.ltld.app.llmgateway.dto.chatdto.ChatResponse;
import com.ltld.app.llmgateway.enums.FinishReason;
import com.ltld.app.llmgateway.exception.ProviderErrorMapper;
import com.ltld.app.llmgateway.provider.ProviderAdapter;
import com.ltld.app.llmgateway.provider.ProviderResult;
import com.ltld.app.llmgateway.provider.groq.dto.GroqRequest;
import com.ltld.app.llmgateway.provider.groq.dto.GroqResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;


@Component
@Slf4j
public class GroqAdapter implements ProviderAdapter {
    private final RestClient restClient;

    public GroqAdapter(@Qualifier("groqRestClient") RestClient restClient) {
        this.restClient = restClient;
    }


    @Override
    public ProviderResult chat(ChatRequest request, String providerModel) {
        GroqRequest body = toGroqRequest(request, providerModel);
        GroqResponse response;
        try {
            response = restClient.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(GroqResponse.class);
        } catch (RestClientResponseException e){
            log.warn("Groq returned {}: {}", e.getStatusCode().value(), truncate(e.getResponseBodyAsString(), 300));
            throw ProviderErrorMapper.fromHttp(name(), e);
        } catch (ResourceAccessException e) {
            log.warn("Groq I/O error: {}", e.getMessage());
            throw ProviderErrorMapper.fromIo(name(), e);
    }
        return toResult(response);
    }

    @Override
    public String name() {
        return "groq";
    }

    private GroqRequest toGroqRequest(ChatRequest request, String providerModel) {
        List<GroqRequest.GroqMessage> groqMessage = request
                .getMessages().stream()
                .map(m -> new GroqRequest.GroqMessage(
                        m.getRole().getValue(),
                        m.getContent()
                )).toList();

        return new GroqRequest(
                providerModel,
                groqMessage,
                request.getMaxTokens(),
                request.getTemperature()
        );
    }

    private ProviderResult toResult(GroqResponse response) {
        GroqResponse.Usage u = response.usage();
        long input = 0;
        long output = 0;
        Long reasoning = null;

        if (u != null) {
            input = u.promptTokens() == null ? 0 : u.promptTokens();
            output = u.completionTokens() == null ? 0 : u.completionTokens();
            reasoning = (u.completionTokensDetails() == null)
                    ? null
                    : toLong(u.completionTokensDetails().reasoningTokens());
        }

        ChatResponse.Usage usage = ChatResponse.Usage.builder()
                .inputTokens(input)
                .reasoningTokens(reasoning)
                .outputTokens(output)
                .totalTokens(input + output)
                .build();

        if (response.choices() == null || response.choices().isEmpty()) {
            return new ProviderResult(name(), response.model(), "", FinishReason.OTHER, usage);
        }

        GroqResponse.Choice choice = response.choices().get(0);

        return new ProviderResult(
                name(),
                response.model(),
                choice.message().content(),
                mapFinishReason(choice.finishReason()),
                usage
        );
    }

    static FinishReason mapFinishReason(String raw) {
        if (raw == null || raw.isBlank()) {
            return FinishReason.OTHER;
        }

        return switch (raw) {
            case "stop" -> FinishReason.STOP;
            case "length" -> FinishReason.LENGTH;
            case "content_filter" -> FinishReason.CONTENT_FILTERED;
            default -> FinishReason.OTHER;
        };
    }

    private static Long toLong(Integer value) {
        return value == null ? null : value.longValue();
    }

    private static String truncate(String s, int max){
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
