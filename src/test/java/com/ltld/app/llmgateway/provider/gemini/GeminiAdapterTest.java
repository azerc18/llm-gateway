package com.ltld.app.llmgateway.provider.gemini;

import com.ltld.app.llmgateway.dto.chatdto.ChatRequest;
import com.ltld.app.llmgateway.enums.FinishReason;
import com.ltld.app.llmgateway.enums.Role;
import com.ltld.app.llmgateway.exception.ErrorCode;
import com.ltld.app.llmgateway.exception.ProviderException;
import com.ltld.app.llmgateway.provider.ProviderResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GeminiAdapterTest {

    private static final String BASE_URL = "https://gemini.test/v1beta";
    private static final String MODEL = "gemini-3.6-flash";
    private static final String URL = BASE_URL + "/models/" + MODEL + ":generateContent";

    private MockRestServiceServer server;
    private GeminiAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new GeminiAdapter(builder.build(), "low");
    }

    private ChatRequest conversationRequest() {
        return new ChatRequest(
                "chat-gemini",
                List.of(
                        new ChatRequest.Message(Role.SYSTEM, "Trả lời ngắn"),
                        new ChatRequest.Message(Role.USER, "Spring Boot là gì?"),
                        new ChatRequest.Message(Role.ASSISTANT, "Là framework Java."),
                        new ChatRequest.Message(Role.USER, "Ví dụ?")
                ),
                256,
                0.5
        );
    }

    private ChatRequest simpleRequest() {
        return new ChatRequest(
                "chat-gemini",
                List.of(new ChatRequest.Message(Role.USER, "hi")),
                100,
                null
        );
    }

    private static final String SUCCESS_JSON = """
            {
              "candidates": [{
                "content": {
                  "role": "model",
                  "parts": [
                    { "text": "Mình cần nghĩ...", "thought": true },
                    { "text": "Spring Boot " },
                    { "text": "là framework.", "thoughtSignature": "CsYBAdHtim9" }
                  ]
                },
                "finishReason": "STOP",
                "index": 0,
                "safetyRatings": []
              }],
              "usageMetadata": {
                "promptTokenCount": 23,
                "candidatesTokenCount": 67,
                "thoughtsTokenCount": 525,
                "totalTokenCount": 615
              },
              "modelVersion": "gemini-3.6-flash",
              "responseId": "abc123"
            }
            """;

    private static final String BLOCKED_PROMPT_JSON = """
            {
              "promptFeedback": { "blockReason": "SAFETY" },
              "usageMetadata": { "promptTokenCount": 12, "totalTokenCount": 12 },
              "modelVersion": "gemini-3.6-flash"
            }
            """;

    private static final String MAX_TOKENS_JSON = """
            {
              "candidates": [{
                "content": { "role": "model", "parts": [{ "text": "Spring Boot là một" }] },
                "finishReason": "MAX_TOKENS"
              }],
              "usageMetadata": { "promptTokenCount": 23, "candidatesTokenCount": 10,
                                 "thoughtsTokenCount": 190, "totalTokenCount": 223 },
              "modelVersion": "gemini-3.6-flash"
            }
            """;

    private static final String NO_THOUGHTS_JSON = """
            {
              "candidates": [{
                "content": { "role": "model", "parts": [{ "text": "Xin chào" }] },
                "finishReason": "STOP"
              }],
              "usageMetadata": { "promptTokenCount": 5, "candidatesTokenCount": 3, "totalTokenCount": 8 },
              "modelVersion": "gemini-3.6-flash"
            }
            """;

    private static final String SAFETY_FINISH_JSON = """
            {
              "candidates": [{ "finishReason": "SAFETY", "index": 0 }],
              "usageMetadata": { "promptTokenCount": 15, "totalTokenCount": 15 },
              "modelVersion": "gemini-3.6-flash"
            }
            """;

    @Test
    void request_isTranslatedToGeminiFormat() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").doesNotExist())
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value("Trả lời ngắn"))
                .andExpect(jsonPath("$.systemInstruction.role").doesNotExist())
                .andExpect(jsonPath("$.contents.length()").value(3))
                .andExpect(jsonPath("$.contents[0].role").value("user"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("Spring Boot là gì?"))
                .andExpect(jsonPath("$.contents[1].role").value("model"))
                .andExpect(jsonPath("$.contents[1].parts[0].text").value("Là framework Java."))
                .andExpect(jsonPath("$.contents[2].role").value("user"))
                .andExpect(jsonPath("$.generationConfig.maxOutputTokens").value(256))
                .andExpect(jsonPath("$.generationConfig.temperature").value(0.5))
                .andExpect(jsonPath("$.generationConfig.thinkingConfig.thinkingLevel").value("low"))
                .andRespond(withSuccess(SUCCESS_JSON, MediaType.APPLICATION_JSON));

        adapter.chat(conversationRequest(), MODEL);

        server.verify();
    }

    @Test
    void request_withoutSystemOrTemperature_omitsThoseFields() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.systemInstruction").doesNotExist())
                .andExpect(jsonPath("$.generationConfig.temperature").doesNotExist())
                .andExpect(jsonPath("$.contents.length()").value(1))
                .andRespond(withSuccess(NO_THOUGHTS_JSON, MediaType.APPLICATION_JSON));

        adapter.chat(simpleRequest(), MODEL);

        server.verify();
    }

    @Test
    void success_joinsParts_skipsThought_sumsTokens() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess(SUCCESS_JSON, MediaType.APPLICATION_JSON));

        ProviderResult r = adapter.chat(conversationRequest(), MODEL);

        assertEquals("gemini", r.provider());
        assertEquals("gemini-3.6-flash", r.model());
        assertEquals("Spring Boot là framework.", r.content());
        assertFalse(r.content().contains("nghĩ"));
        assertEquals(FinishReason.STOP, r.finishReason());

        assertEquals(23, r.usage().getInputTokens());
        assertEquals(592, r.usage().getOutputTokens());
        assertEquals(525L, r.usage().getReasoningTokens());
        assertEquals(615, r.usage().getTotalTokens());
    }

    @Test
    void blockedPrompt_returnsContentFiltered_withoutThrowing() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess(BLOCKED_PROMPT_JSON, MediaType.APPLICATION_JSON));

        ProviderResult r = adapter.chat(simpleRequest(), MODEL);

        assertEquals(FinishReason.CONTENT_FILTERED, r.finishReason());
        assertEquals("", r.content());
        assertEquals(12, r.usage().getInputTokens());
        assertEquals(0, r.usage().getOutputTokens());
        assertNull(r.usage().getReasoningTokens());
    }

    @Test
    void maxTokens_returnsLength_andCountsThoughts() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess(MAX_TOKENS_JSON, MediaType.APPLICATION_JSON));

        ProviderResult r = adapter.chat(simpleRequest(), MODEL);

        assertEquals(FinishReason.LENGTH, r.finishReason());
        assertEquals("Spring Boot là một", r.content());
        assertEquals(200, r.usage().getOutputTokens());
    }

    @Test
    void noThoughtsCount_reasoningIsNull_outputIsCandidatesOnly() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess(NO_THOUGHTS_JSON, MediaType.APPLICATION_JSON));

        ProviderResult r = adapter.chat(simpleRequest(), MODEL);

        assertNull(r.usage().getReasoningTokens());
        assertEquals(3, r.usage().getOutputTokens());
        assertEquals(8, r.usage().getTotalTokens());
    }

    @Test
    void safetyFinish_withoutContent_returnsEmptyContentFiltered() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess(SAFETY_FINISH_JSON, MediaType.APPLICATION_JSON));

        ProviderResult r = adapter.chat(simpleRequest(), MODEL);

        assertEquals(FinishReason.CONTENT_FILTERED, r.finishReason());
        assertEquals("", r.content());
    }

    @ParameterizedTest
    @CsvSource({
            "400, PROVIDER_BAD_REQUEST,  false",
            "401, PROVIDER_AUTH_FAILED,  false",
            "403, PROVIDER_AUTH_FAILED,  false",
            "429, PROVIDER_RATE_LIMITED, true",
            "500, PROVIDER_ERROR,        true",
            "503, PROVIDER_UNAVAILABLE,  true"
    })
    void httpErrors_areMappedCorrectly(int status, ErrorCode expected, boolean retryable) {
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.valueOf(status))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":" + status + ",\"status\":\"X\"}}"));

        ProviderException ex = assertThrows(ProviderException.class,
                () -> adapter.chat(simpleRequest(), MODEL));

        assertEquals(expected, ex.getErrorCode());
        assertEquals(status, ex.getUpstreamStatus());
        assertEquals(retryable, ex.isRetryable());
        assertEquals("gemini", ex.getProvider());
    }

    @Test
    void timeout_throwsProviderTimeout() {
        server.expect(requestTo(URL))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        ProviderException ex = assertThrows(ProviderException.class,
                () -> adapter.chat(simpleRequest(), MODEL));

        assertEquals(ErrorCode.PROVIDER_TIMEOUT, ex.getErrorCode());
        assertNull(ex.getUpstreamStatus());
        assertTrue(ex.isRetryable());
        assertEquals("gemini", ex.getProvider());
    }
}