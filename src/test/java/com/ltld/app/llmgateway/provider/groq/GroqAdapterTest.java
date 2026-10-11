package com.ltld.app.llmgateway.provider.groq;

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

public class GroqAdapterTest {

    private MockRestServiceServer server;
    private GroqAdapter adapter;

    private static final String BASE_URL = "https://groq.test/openai/v1";
    private static final String MODEL = "openai/gpt-oss-120b";

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new GroqAdapter(builder.build());
    }

    private static final String SUCCESS_JSON = """
            {
              "id": "chatcmpl-1",
              "model": "openai/gpt-oss-120b",
              "choices": [{
                "index": 0,
                "message": { "role": "assistant", "content": "Xin chào", "reasoning": "..." },
                "finish_reason": "stop"
              }],
              "usage": {
                "prompt_tokens": 10,
                "completion_tokens": 25,
                "total_tokens": 999,
                "completion_tokens_details": { "reasoning_tokens": 7 }
              },
              "x_groq": { "id": "req_x" }
            }
            """;

    private ChatRequest sampleRequest() {
        return new ChatRequest(
                "chat-fast",
                List.of(new ChatRequest.Message(Role.USER, "hi")),
                100,
                0.5
        );
    }

    @Test
    void success_sendsCorrectRequest_andMapsResponse() {
        // ---------- Arrange ----------
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value(MODEL))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content").value("hi"))
                .andExpect(jsonPath("$.max_tokens").value(100))
                .andRespond(withSuccess(SUCCESS_JSON, MediaType.APPLICATION_JSON));

        ProviderResult result = adapter.chat(sampleRequest(), MODEL);

        assertEquals("groq", result.provider());
        assertEquals("Xin chào", result.content());
        assertEquals(FinishReason.STOP, result.finishReason());
        assertEquals(10, result.usage().getInputTokens());
        assertEquals(25, result.usage().getOutputTokens());
        assertEquals(7L, result.usage().getReasoningTokens());
        assertEquals(35, result.usage().getTotalTokens());

        server.verify();
    }

    @Test
    void unauthorized_throwsAuthFailed_notRetryable() {
        // Arrange
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"Invalid API Key\"}}"));

        ProviderException ex = assertThrows(ProviderException.class,
                () -> adapter.chat(sampleRequest(), MODEL));

        assertEquals(ErrorCode.PROVIDER_AUTH_FAILED, ex.getErrorCode());
        assertEquals(401, ex.getUpstreamStatus());
        assertFalse(ex.isRetryable());
        assertEquals("groq", ex.getProvider());
    }

    @ParameterizedTest
    @CsvSource({
            "400, PROVIDER_BAD_REQUEST,  false",
            "404, PROVIDER_BAD_REQUEST,  false",
            "429, PROVIDER_RATE_LIMITED, true",
            "500, PROVIDER_ERROR,        true",
            "503, PROVIDER_UNAVAILABLE,  true"
    })
    void httpErrors_areMappedCorrectly(int status, ErrorCode expected, boolean retryable) {
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withStatus(HttpStatus.valueOf(status)));

        ProviderException ex = assertThrows(ProviderException.class,
                () -> adapter.chat(sampleRequest(), MODEL));

        assertEquals(expected, ex.getErrorCode());
        assertEquals(status, ex.getUpstreamStatus());
        assertEquals(retryable, ex.isRetryable());
    }

    @Test
    void timeout_throwsProviderTimeout() {
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        ProviderException ex = assertThrows(ProviderException.class,
                () -> adapter.chat(sampleRequest(), MODEL));

        assertEquals(ErrorCode.PROVIDER_TIMEOUT, ex.getErrorCode());
        assertNull(ex.getUpstreamStatus());     // timeout thì không có mã HTTP
        assertTrue(ex.isRetryable());
    }

    @Test
    void nullUsageAndEmptyChoices_doNotThrow() {
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess("""
                    { "id": "x", "model": "openai/gpt-oss-120b", "choices": [] }
                    """, MediaType.APPLICATION_JSON));

        ProviderResult result = adapter.chat(sampleRequest(), MODEL);

        assertEquals("", result.content());
        assertEquals(FinishReason.OTHER, result.finishReason());
        assertEquals(0, result.usage().getTotalTokens());
        assertNull(result.usage().getReasoningTokens());
    }
}
