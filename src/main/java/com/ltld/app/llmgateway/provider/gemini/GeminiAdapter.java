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
        // 1. System prompt: lọc các message có role SYSTEM, lấy content, nối bằng "\n".
        //    Có thì tạo new GeminiContent(null, List.of(GeminiPart.ofText(...)))
        //    Không có thì để null (nhờ @JsonInclude, field sẽ không xuất hiện trong JSON)
        String systemText = request.getMessages().stream()
                .filter(m -> m.getRole() == Role.SYSTEM)
                .map(ChatRequest.Message::getContent)
                .collect(Collectors.joining("\n"));

        GeminiContent systemInstruction;

        if (systemText != null || !systemText.isBlank()) {
            systemInstruction = new GeminiContent(
                    null, List.of(ofText(systemText))
            );
        } else {
            systemInstruction = new GeminiContent(
                    null, null);
        }

        // 2. Contents: các message KHÔNG phải SYSTEM, map thành GeminiContent(toGeminiRole(role), [ofText(content)])
        List<GeminiContent> contents = request.getMessages().stream()
                .filter(m -> m.getRole() != Role.SYSTEM)
                .map(c -> new GeminiContent(toGeminiRole(c.getRole()), List.of(ofText(c.getContent()))))
                .toList();
        // 3. GenerationConfig(maxTokens, temperature, new ThinkingConfig(thinkingLevel))
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
        // Phòng trường hợp Gemini trả body rỗng
        if (r == null) {
            log.warn("Gemini returned empty body");
            return new ProviderResult(name(), providerModel, "", FinishReason.OTHER, toUsage(null));
        }

        String model = (r.modelVersion() != null) ? r.modelVersion() : providerModel;
        ChatResponse.Usage usage = toUsage(r.usageMetadata());

        // Không có candidates: thường là prompt bị chặn ngay từ đầu (design mục 6)
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

        // Lý do gốc (vd SPII, RECITATION) CHỈ ghi log, client chỉ thấy giá trị đã chuẩn hóa
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
            reasoning = toLong(u.thoughtsTokenCount());          // vắng mặt → null, KHÔNG phải 0
        }

        long output = candidates + thoughts;                      // Gemini: candidates CHƯA gồm thoughts

        return ChatResponse.Usage.builder()
                .inputTokens(input)
                .outputTokens(output)
                .reasoningTokens(reasoning)
                .totalTokens(input + output)                      // tự tính, không dùng totalTokenCount
                .build();
    }

    private static Long toLong(Integer value) {
        return value == null ? null : value.longValue();
    }

    static String extractText(GeminiContent content) {
        if (content == null || content.parts() == null) {
            return "";                                    // vd finishReason = SAFETY: không có nội dung
        }
        return content.parts().stream()
                .filter(p -> !Boolean.TRUE.equals(p.thought()))   // bỏ part "suy nghĩ"
                .map(GeminiPart::text)
                .filter(Objects::nonNull)                         // bỏ part không có text
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
