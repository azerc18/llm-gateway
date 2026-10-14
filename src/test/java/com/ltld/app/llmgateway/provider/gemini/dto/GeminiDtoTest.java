package com.ltld.app.llmgateway.provider.gemini.dto;

import com.ltld.app.llmgateway.enums.FinishReason;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeminiDtoTest {

    private final JsonMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private static final String SUCCESS_JSON = """
            {
              "candidates": [{
                "content": {
                  "role": "model",
                  "parts": [{ "text": "Spring Boot là...", "thoughtSignature": "CsYBAdHtim9xyz" }]
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

    private static final String BLOCKED_JSON = """
            {
              "promptFeedback": { "blockReason": "SAFETY" },
              "usageMetadata": { "promptTokenCount": 12, "totalTokenCount": 12 },
              "modelVersion": "gemini-3.6-flash"
            }
            """;

    @Test
    void parsesSuccessResponse_ignoringUnknownFields() {
        GeminiResponse res = mapper.readValue(SUCCESS_JSON, GeminiResponse.class);

        GeminiResponse.Candidate c = res.candidates().get(0);
        assertEquals("model", c.content().role());
        assertEquals("Spring Boot là...", c.content().parts().get(0).text());
        assertNull(c.content().parts().get(0).thought());
        assertEquals("STOP", c.finishReason());

        assertEquals(23, res.usageMetadata().promptTokenCount());
        assertEquals(525, res.usageMetadata().thoughtsTokenCount());
        assertEquals("gemini-3.6-flash", res.modelVersion());
    }

    @Test
    void parsesBlockedResponse_withoutCandidates() {
        GeminiResponse res = mapper.readValue(BLOCKED_JSON, GeminiResponse.class);

        assertNull(res.candidates());
        assertEquals("SAFETY", res.promptFeedback().blockReason());
        assertEquals(12, res.usageMetadata().promptTokenCount());
        assertNull(res.usageMetadata().candidatesTokenCount());
        assertNull(res.usageMetadata().thoughtsTokenCount());
    }

    @Test
    void serializesRequest_omittingNullFields() {
        GeminiRequest req = new GeminiRequest(
                null,
                List.of(new GeminiContent("user", List.of(GeminiPart.ofText("hi")))),
                new GeminiRequest.GenerationConfig(
                        100,
                        null,
                        new GeminiRequest.ThinkingConfig("low"))
        );

        String json = mapper.writeValueAsString(req);

        assertTrue(json.contains("\"maxOutputTokens\":100"));
        assertTrue(json.contains("\"thinkingLevel\":\"low\""));
        assertTrue(json.contains("\"role\":\"user\""));

        assertFalse(json.contains("systemInstruction"));
        assertFalse(json.contains("temperature"));
        assertFalse(json.contains("thought"));
        assertFalse(json.contains("null"));
    }

}
