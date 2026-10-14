package com.ltld.app.llmgateway.provider.gemini;

import com.ltld.app.llmgateway.dto.chatdto.ChatRequest;
import com.ltld.app.llmgateway.dto.chatdto.ChatResponse;
import com.ltld.app.llmgateway.enums.FinishReason;
import com.ltld.app.llmgateway.enums.Role;
import com.ltld.app.llmgateway.exception.ProviderErrorMapper;
import com.ltld.app.llmgateway.provider.ProviderAdapter;
import com.ltld.app.llmgateway.provider.ProviderResult;
import com.ltld.app.llmgateway.provider.gemini.dto.GeminiContent;
import com.ltld.app.llmgateway.provider.gemini.dto.GeminiPart;
import com.ltld.app.llmgateway.provider.gemini.dto.GeminiRequest;
import com.ltld.app.llmgateway.provider.gemini.dto.GeminiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.ltld.app.llmgateway.provider.gemini.dto.GeminiPart.ofText;
import static com.ltld.app.llmgateway.util.StringUtil.truncate;

@Component
@Slf4j
public class GeminiAdapter implements ProviderAdapter {

    private final RestClient restClient;
    private final String thinkingLevel;

    public GeminiAdapter(@Qualifier("geminiRestClient") RestClient restClient,
                         @Value("${llm.gemini.thinking-level:low}") String thinkingLevel) {
        this.restClient = restClient;
        this.thinkingLevel = thinkingLevel;
    }

    @Override
    public ProviderResult chat(ChatRequest request, String providerModel) {
        GeminiRequest body = toGeminiRequest(request);
        GeminiResponse response;
        try {
            response = restClient.post()
                    .uri("/models/{model}:generateContent", providerModel)
                    .body(body)
                    .retrieve()
                    .body(GeminiResponse.class);
        } catch (RestClientResponseException e) {
            log.warn("Gemini returned {}: {}", e.getStatusCode().value(),
                    truncate(e.getResponseBodyAsString(), 300));
            throw ProviderErrorMapper.fromHttp(name(), e);
        } catch (ResourceAccessException e) {
            log.warn("Gemini I/O error: {}", e.getMessage());
            throw ProviderErrorMapper.fromIo(name(), e);
        }
        return toResult(response, providerModel);
    }

    @Override
    public String name() {
        return "gemini";
    }

    private GeminiRequest toGeminiRequest(ChatRequest request) {
        String systemText = request.getMessages().stream()
                .filter(m -> m.getRole() == Role.SYSTEM)
                .map(ChatRequest.Message::getContent)
                .collect(Collectors.joining("\n"));

        GeminiContent systemInstruction = systemText.isBlank()
                ? null
                : new GeminiContent(null, List.of(GeminiPart.ofText(systemText)));

        List<GeminiContent> contents = request.getMessages().stream()
                .filter(m -> m.getRole() != Role.SYSTEM)
                .map(c -> new GeminiContent(toGeminiRole(c.getRole()), List.of(ofText(c.getContent()))))
                .toList();

        GeminiRequest.GenerationConfig config = new GeminiRequest.GenerationConfig(
                request.getMaxTokens(),
                request.getTemperature(),
                new GeminiRequest.ThinkingConfig(thinkingLevel)
        );

        return new GeminiRequest(systemInstruction, contents, config);
    }

    static String toGeminiRole(Role role) {
        return switch (role) {
            case USER -> "user";
            case ASSISTANT -> "model";
            case SYSTEM -> throw new IllegalArgumentException("SYSTEM must go to systemInstruction");
        };
    }

    ProviderResult toResult(GeminiResponse r, String providerModel) {
        if (r == null) {
            log.warn("Gemini returned empty body");
            return new ProviderResult(name(), providerModel, "", FinishReason.OTHER, toUsage(null));
        }

        String model = (r.modelVersion() != null) ? r.modelVersion() : providerModel;
        ChatResponse.Usage usage = toUsage(r.usageMetadata());

        if (r.candidates() == null || r.candidates().isEmpty()) {
            String blockReason = (r.promptFeedback() == null) ? null : r.promptFeedback().blockReason();
            if (blockReason != null) {
                log.warn("Gemini blocked prompt: blockReason={}", blockReason);
                return new ProviderResult(name(), model, "", FinishReason.CONTENT_FILTERED, usage);
            }
            log.warn("Gemini returned no candidates and no blockReason");
            return new ProviderResult(name(), model, "", FinishReason.OTHER, usage);
        }

        GeminiResponse.Candidate candidate = r.candidates().get(0);
        String content = extractText(candidate.content());
        FinishReason finish = mapFinishReason(candidate.finishReason());

        if (finish == FinishReason.CONTENT_FILTERED || finish == FinishReason.OTHER) {
            log.warn("Gemini finishReason raw={} mapped={}", candidate.finishReason(), finish);
        }

        return new ProviderResult(name(), model, content, finish, usage);
    }

    private static ChatResponse.Usage toUsage(GeminiResponse.UsageMetadata u) {
        long input = 0;
        long candidates = 0;
        long thoughts = 0;
        Long reasoning = null;

        if (u != null) {
            input = u.promptTokenCount() == null ? 0 : u.promptTokenCount();
            candidates = u.candidatesTokenCount() == null ? 0 : u.candidatesTokenCount();
            thoughts = u.thoughtsTokenCount() == null ? 0 : u.thoughtsTokenCount();
            reasoning = toLong(u.thoughtsTokenCount());
        }

        long output = candidates + thoughts;

        return ChatResponse.Usage.builder()
                .inputTokens(input)
                .outputTokens(output)
                .reasoningTokens(reasoning)
                .totalTokens(input + output)
                .build();
    }

    private static Long toLong(Integer value) {
        return value == null ? null : value.longValue();
    }

    static String extractText(GeminiContent content) {
        if (content == null || content.parts() == null) {
            return "";
        }
        return content.parts().stream()
                .filter(p -> !Boolean.TRUE.equals(p.thought()))
                .map(GeminiPart::text)
                .filter(Objects::nonNull)
                .collect(Collectors.joining());
    }

    static FinishReason mapFinishReason(String raw) {
        if (raw == null || raw.isBlank()) {
            return FinishReason.OTHER;
        }
        return switch (raw) {
            case "STOP" -> FinishReason.STOP;
            case "MAX_TOKENS" -> FinishReason.LENGTH;
            case "SAFETY", "BLOCKLIST", "SPII", "PROHIBITED_CONTENT", "RECITATION" -> FinishReason.CONTENT_FILTERED;
            default -> FinishReason.OTHER;
        };
    }


}
