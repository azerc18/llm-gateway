package com.ltld.app.llmgateway.dto;

import com.ltld.app.llmgateway.provider.groq.GroqResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroqChatResponseTest {

    // Bật giống hệt cấu hình của app, để test phản ánh đúng thực tế
    private final JsonMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Test
    void parsesRealGroqResponse_ignoringUnknownFields() {
        String json = """
                {
                                                     "id": "chatcmpl-abc123",
                                                     "object": "chat.completion",
                                                     "created": 1760000000,
                                                     "model": "openai/gpt-oss-120b",
                                                     "choices": [{
                                                       "index": 0,
                                                       "message": { "role": "assistant", "content": "Spring Boot là...", "reasoning": "..." },
                                                       "logprobs": null,
                                                       "finish_reason": "stop"
                                                     }],
                                                     "usage": {
                                                       "queue_time": 0.01, "prompt_tokens": 98, "prompt_time": 0.002,
                                                       "completion_tokens": 107, "completion_time": 0.3, "total_tokens": 205, "total_time": 0.31,
                                                       "completion_tokens_details": { "reasoning_tokens": 46 }
                                                     },
                                                     "system_fingerprint": "fp_xyz",
                                                     "x_groq": { "id": "req_xyz" }
                                                   }
            """;

        GroqResponse res = mapper.readValue(json, GroqResponse.class);

        assertEquals("Spring Boot là...", res.choices().get(0).message().content());
        assertEquals("stop", res.choices().get(0).finishReason());
        assertEquals(98, res.usage().promptTokens());
        assertEquals(46, res.usage().completionTokensDetails().reasoningTokens());
    }
}
