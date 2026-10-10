package com.ltld.app.llmgateway.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ApiKeyHasherTest {
    @Test
    void matchesPostgresSeedHash() {
        String expected = "62ff47fc38cdfc59640c3c521806bae9691427fb522a7b955622bdfbd7f102d5";
        assertEquals(expected, ApiKeyHasher.sha256Hex("sk-dev-local-123"));
    }

    @Test
    void sameInputSameOutput() {
        String rawKey = "sk-dev-local-123";

        String firstTime = ApiKeyHasher.sha256Hex(rawKey);
        String secondTime = ApiKeyHasher.sha256Hex(rawKey);

        assertEquals(firstTime, secondTime);
    }

    @Test
    void outputIs64LowercaseHex() {
        String result = ApiKeyHasher.sha256Hex("sk-dev-local-123");

        assertTrue(result.matches("[0-9a-f]{64}"));
    }
}
