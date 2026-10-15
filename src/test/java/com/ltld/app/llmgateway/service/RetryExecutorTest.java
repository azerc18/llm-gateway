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

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final List<Duration> sleeps = new ArrayList<>();
    private final Sleeper fakeSleeper = sleeps::add;                 // ghi lại, không chờ thật

    private final RetryProperties props = new RetryProperties(
            3, 1, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(4), Duration.ofSeconds(50));

    private final RetryExecutor executor = new RetryExecutor(props, fakeSleeper);

    /** Deadline rất xa: các test này không kiểm tra deadline, chỉ kiểm tra retry. */
    private static Deadline farAway() {
        return Deadline.after(Duration.ofHours(1), System::nanoTime);
    }

    private static ProviderResult ok() {
        var usage = ChatResponse.Usage.builder().inputTokens(1).outputTokens(1).totalTokens(2).build();
        return new ProviderResult("groq", "m", "ok", FinishReason.STOP, usage);
    }

    private static ProviderException err(ErrorCode code) {
        return new ProviderException(code, "groq", 503, null);
    }

    /** Lỗi {@code failures} lần đầu, sau đó thành công. Đếm số lần được gọi. */
    private static Supplier<ProviderResult> failThenSucceed(int failures, ErrorCode code, AtomicInteger calls) {
        return () -> {
            int n = calls.incrementAndGet();
            if (n <= failures) throw err(code);
            return ok();
        };
    }

    private RetryExecutor.Outcome run(int maxAttempts, Supplier<ProviderResult> call) {
        return executor.execute("groq", maxAttempts, TIMEOUT, farAway(), call);
    }

    @Test
    void success_firstTry_noRetry_noSleep() {
        var calls = new AtomicInteger();
        var outcome = run(3, failThenSucceed(0, null, calls));

        assertEquals(1, calls.get());
        assertEquals(0, outcome.retries());
        assertTrue(sleeps.isEmpty());
    }

    @Test
    void retryableError_thenSuccess_retriesWithBackoff() {
        var calls = new AtomicInteger();
        var outcome = run(3, failThenSucceed(2, ErrorCode.PROVIDER_UNAVAILABLE, calls));

        assertEquals(3, calls.get());
        assertEquals(2, outcome.retries());
        assertEquals(List.of(Duration.ofSeconds(1), Duration.ofSeconds(2)), sleeps);
    }

    @Test
    void retryableError_exhaustsAttempts_throwsLastError() {
        var calls = new AtomicInteger();

        ProviderException ex = assertThrows(ProviderException.class,
                () -> run(3, failThenSucceed(99, ErrorCode.PROVIDER_TIMEOUT, calls)));

        assertEquals(ErrorCode.PROVIDER_TIMEOUT, ex.getErrorCode());
        assertEquals(3, calls.get());              // đúng maxAttempts
        assertEquals(2, sleeps.size());            // không chờ sau lần cuối
    }

    @Test
    void nonRetryableError_failsImmediately() {
        var calls = new AtomicInteger();

        assertThrows(ProviderException.class,
                () -> run(3, failThenSucceed(99, ErrorCode.PROVIDER_AUTH_FAILED, calls)));

        assertEquals(1, calls.get());
        assertTrue(sleeps.isEmpty());
    }

    @Test
    void maxAttemptsParameter_overridesConfig() {
        var calls = new AtomicInteger();

        assertThrows(ProviderException.class,
                () -> run(1, failThenSucceed(99, ErrorCode.PROVIDER_UNAVAILABLE, calls)));

        assertEquals(1, calls.get());              // fallback chỉ thử 1 lần, dù config chung là 3
        assertTrue(sleeps.isEmpty());
    }

    @Test
    void deadlineAlreadyTooShort_doesNotCall_throwsTimeout() {
        var calls = new AtomicInteger();
        Deadline almostOver = Deadline.after(Duration.ofSeconds(5), System::nanoTime);   // 5s < timeout 15s

        ProviderException ex = assertThrows(ProviderException.class,
                () -> executor.execute("gemini", 1, TIMEOUT, almostOver, failThenSucceed(0, null, calls)));

        assertEquals(ErrorCode.PROVIDER_TIMEOUT, ex.getErrorCode());
        assertEquals("gemini", ex.getProvider());
        assertEquals(0, calls.get());              // KHÔNG gọi provider khi không đủ thời gian
    }

    @Test
    void notEnoughTimeForBackoffPlusCall_givesUpWithoutSleeping() {
        FakeClock clock = new FakeClock();
        var props = new RetryProperties(3, 1, Duration.ofSeconds(1), 2.0,
                Duration.ofSeconds(4), Duration.ofSeconds(45));
        var executor = new RetryExecutor(props, clock);
        var groq = new ScriptedAdapter("groq", clock)
                .fail(ErrorCode.PROVIDER_TIMEOUT, Duration.ofSeconds(15))
                .fail(ErrorCode.PROVIDER_TIMEOUT, Duration.ofSeconds(15));
        Deadline deadline = Deadline.after(props.deadline(), clock);

        ProviderException ex = assertThrows(ProviderException.class,
                () -> executor.execute("groq", 3, Duration.ofSeconds(15), deadline,
                        () -> groq.chat(null, "m")));

        assertEquals(ErrorCode.PROVIDER_TIMEOUT, ex.getErrorCode());
        assertEquals(2, groq.calls());
        assertEquals(List.of(Duration.ofSeconds(1)), clock.sleeps());   // không có lần ngủ 2s
        assertEquals(Duration.ofSeconds(31), clock.elapsed());          // báo lỗi ngay ở t=31s
    }

    @Test
    void exactlyEnoughTimeForBackoffPlusCall_stillRetries() {
        FakeClock clock = new FakeClock();
        var props = new RetryProperties(3, 1, Duration.ofSeconds(1), 2.0,
                Duration.ofSeconds(4), Duration.ofSeconds(48));
        var executor = new RetryExecutor(props, clock);
        var groq = new ScriptedAdapter("groq", clock)
                .fail(ErrorCode.PROVIDER_TIMEOUT, Duration.ofSeconds(15))
                .fail(ErrorCode.PROVIDER_TIMEOUT, Duration.ofSeconds(15))
                .ok(Duration.ofSeconds(5));
        Deadline deadline = Deadline.after(props.deadline(), clock);

        // t=31s còn đúng 17s = backoff 2s + timeout 15s → vẫn được thử lần 3
        var outcome = executor.execute("groq", 3, Duration.ofSeconds(15), deadline,
                () -> groq.chat(null, "m"));

        assertEquals(2, outcome.retries());
        assertEquals(List.of(Duration.ofSeconds(1), Duration.ofSeconds(2)), clock.sleeps());
        assertEquals(Duration.ofSeconds(38), clock.elapsed());
    }
}