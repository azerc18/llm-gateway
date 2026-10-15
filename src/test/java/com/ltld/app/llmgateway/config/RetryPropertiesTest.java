package com.ltld.app.llmgateway.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetryPropertiesTest {

    @Test
    void backoff_growsExponentially_andIsCapped() {
        var p = new RetryProperties(5, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(4));

        assertEquals(Duration.ofMillis(1000), p.backoffFor(1));
        assertEquals(Duration.ofMillis(2000), p.backoffFor(2));
        assertEquals(Duration.ofMillis(4000), p.backoffFor(3));
        assertEquals(Duration.ofMillis(4000), p.backoffFor(4));     // bị chặn bởi maxBackoff
    }

    @Test
    void maxAttemptsBelowOne_fails() {
        assertThrows(IllegalStateException.class,
                () -> new RetryProperties(0, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(4)));
    }

    @Test
    void negativeInitialBackoff_fails() {
        assertThrows(IllegalStateException.class,
                () -> new RetryProperties(3, Duration.ofSeconds(-1), 2.0, Duration.ofSeconds(4)));

    }
}
