package com.ltld.app.llmgateway.provider.groq;

import com.ltld.app.llmgateway.enums.FinishReason;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroqAdapterMappingTest {
    @Test
    void mapsFinishReasons() {
        assertEquals(FinishReason.STOP, GroqAdapter.mapFinishReason("stop"));
        assertEquals(FinishReason.LENGTH, GroqAdapter.mapFinishReason("length"));
        assertEquals(FinishReason.CONTENT_FILTERED, GroqAdapter.mapFinishReason("content_filter"));
        assertEquals(FinishReason.OTHER, GroqAdapter.mapFinishReason("tool_calls"));
        assertEquals(FinishReason.OTHER, GroqAdapter.mapFinishReason(null));
        assertEquals(FinishReason.OTHER, GroqAdapter.mapFinishReason("something_new"));
    }
}
