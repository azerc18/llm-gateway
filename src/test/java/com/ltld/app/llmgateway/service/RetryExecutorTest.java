package com.ltld.app.llmgateway.service;

import com.ltld.app.llmgateway.config.RetryProperties;
import com.ltld.app.llmgateway.dto.chatdto.ChatResponse;
import com.ltld.app.llmgateway.enums.FinishReason;
import com.ltld.app.llmgateway.exception.ErrorCode;
import com.ltld.app.llmgateway.exception.ProviderException;
import com.ltld.app.llmgateway.provider.ProviderResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class RetryExecutorTest {

    private final List<Duration> sleeps = new ArrayList<>();
    private final Sleeper fakeSleeper = sleeps::add;            // ghi lại, không chờ

    private final RetryExecutor executor = new RetryExecutor(
            new RetryProperties(3, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(4)),
            fakeSleeper);

    private static ProviderResult ok() {
        var usage = ChatResponse.Usage.builder().inputTokens(1).outputTokens(1).totalTokens(2).build();
        return new ProviderResult("groq", "m", "ok", FinishReason.STOP, usage);
    }

    private static ProviderException err(ErrorCode code) {
        return new ProviderException(code, "groq", 503, null);
    }

    /** Trả về một Supplier: lỗi {@code failures} lần đầu, sau đó thành công. Đếm số lần được gọi. */
    private static Supplier<ProviderResult> failThenSucceed(int failures, ErrorCode code, AtomicInteger calls) {
        return () -> {
            int n = calls.incrementAndGet();
            if (n <= failures) throw err(code);
            return ok();
        };
    }

    @Test
    void success_firstTry_noRetry_noSleep() {
        var calls = new AtomicInteger();
        var outcome = executor.execute("groq", failThenSucceed(0, null, calls));

        assertEquals(1, calls.get());
        assertEquals(0, outcome.retries());
        assertTrue(sleeps.isEmpty());
    }

    @Test
    void retryableError_thenSuccess_retriesWithBackoff() {
        var calls = new AtomicInteger();
        var outcome = executor.execute("groq", failThenSucceed(2, ErrorCode.PROVIDER_UNAVAILABLE, calls));

        assertEquals(3, calls.get());
        assertEquals(2, outcome.retries());
        assertEquals(List.of(Duration.ofSeconds(1), Duration.ofSeconds(2)), sleeps);   // 1s rồi 2s
    }

    @Test
    void retryableError_exhaustsAttempts_throwsLastError() {
        var calls = new AtomicInteger();

        ProviderException ex = assertThrows(ProviderException.class,
                () -> executor.execute("groq", failThenSucceed(99, ErrorCode.PROVIDER_TIMEOUT, calls)));

        assertEquals(ErrorCode.PROVIDER_TIMEOUT, ex.getErrorCode());
        assertEquals(3, calls.get());
        assertEquals(2, sleeps.size());
    }

    @Test
    void nonRetryableError_failsImmediately() {
        var calls = new AtomicInteger();

        assertThrows(ProviderException.class,
                () -> executor.execute("groq", failThenSucceed(99, ErrorCode.PROVIDER_AUTH_FAILED, calls)));

        assertEquals(1, calls.get());
        assertTrue(sleeps.isEmpty());
    }
}