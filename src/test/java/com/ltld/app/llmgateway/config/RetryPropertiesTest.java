package com.ltld.app.llmgateway.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RetryPropertiesTest {

    private static RetryProperties props(int maxAttempts, int fallbackMaxAttempts,
                                         Duration initial, double multiplier,
                                         Duration max, Duration deadline) {
        return new RetryProperties(maxAttempts, fallbackMaxAttempts, initial, multiplier, max, deadline);
    }

    private static final Duration S1 = Duration.ofSeconds(1);
    private static final Duration S4 = Duration.ofSeconds(4);
    private static final Duration S50 = Duration.ofSeconds(50);

    @Test
    void backoff_growsExponentially_andIsCapped() {
        var p = props(5, 1, S1, 2.0, S4, S50);

        assertEquals(Duration.ofMillis(1000), p.backoffFor(1));
        assertEquals(Duration.ofMillis(2000), p.backoffFor(2));
        assertEquals(Duration.ofMillis(4000), p.backoffFor(3));
        assertEquals(Duration.ofMillis(4000), p.backoffFor(4));     // chặn bởi maxBackoff
    }

    @Test
    void maxAttemptsBelowOne_fails() {
        assertThrows(IllegalStateException.class, () -> props(0, 1, S1, 2.0, S4, S50));
    }

    @Test
    void fallbackMaxAttemptsBelowOne_fails() {
        assertThrows(IllegalStateException.class, () -> props(2, 0, S1, 2.0, S4, S50));
    }

    @Test
    void multiplierBelowOne_fails() {
        assertThrows(IllegalStateException.class, () -> props(2, 1, S1, 0.5, S4, S50));
    }

    @Test
    void negativeInitialBackoff_fails() {
        assertThrows(IllegalStateException.class, () -> props(2, 1, Duration.ofSeconds(-1), 2.0, S4, S50));
    }

    @Test
    void maxBackoffBelowInitial_fails() {
        assertThrows(IllegalStateException.class, () -> props(2, 1, S4, 2.0, S1, S50));
    }

    @Test
    void zeroDeadline_fails() {
        assertThrows(IllegalStateException.class, () -> props(2, 1, S1, 2.0, S4, Duration.ZERO));
    }
}