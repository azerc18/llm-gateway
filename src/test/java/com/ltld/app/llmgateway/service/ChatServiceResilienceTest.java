package com.ltld.app.llmgateway.service;

import com.ltld.app.llmgateway.config.LlmProperties;
import com.ltld.app.llmgateway.config.RetryProperties;
import com.ltld.app.llmgateway.dto.chatdto.ChatRequest;
import com.ltld.app.llmgateway.dto.chatdto.ChatResponse;
import com.ltld.app.llmgateway.enums.Role;
import com.ltld.app.llmgateway.exception.ErrorCode;
import com.ltld.app.llmgateway.exception.ProviderException;
import com.ltld.app.llmgateway.provider.ProviderAdapter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChatServiceResilienceTest {

    private static final Duration T15 = Duration.ofSeconds(15);
    private final CapturingRecorder recorder = new CapturingRecorder();

    private final FakeClock clock = new FakeClock();

    private final LlmProperties llmProps = new LlmProperties(
            Map.of("groq",   new LlmProperties.Provider("u", "k", T15),
                    "gemini", new LlmProperties.Provider("u", "k", T15)),
            Map.of("chat-fast", new LlmProperties.Route(
                    new LlmProperties.Target("groq", "g-model"),
                    new LlmProperties.Target("gemini", "m-model"))));

    private final RetryProperties prodRetry = new RetryProperties(
            2, 1, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(4), Duration.ofSeconds(50));

    // ---------- helpers ----------

    private ChatService service(ScriptedAdapter groq, ScriptedAdapter gemini) {
        return service(groq, gemini, prodRetry);
    }

    private ChatService service(ScriptedAdapter groq, ScriptedAdapter gemini, RetryProperties retry) {
        List<ProviderAdapter> list = List.of(groq, gemini);
        return new ChatService(list, new ModelRouter(llmProps, list),
                new RetryExecutor(retry, clock), retry, llmProps, clock, recorder);
    }

    private static ChatRequest request() {
        return new ChatRequest("chat-fast", List.of(new ChatRequest.Message(Role.USER, "hi")), 100, null);
    }

    private ScriptedAdapter groq()   { return new ScriptedAdapter("groq", clock); }
    private ScriptedAdapter gemini() { return new ScriptedAdapter("gemini", clock); }

    private static Duration ms(long v) { return Duration.ofMillis(v); }
    private static Duration s(long v)  { return Duration.ofSeconds(v); }

    // ---------- happy path ----------

    @Test
    void primaryOk_noRetry_noFallback() {
        var groq = groq().ok(s(1));
        var gemini = gemini();

        ChatResponse res = service(groq, gemini).chat(request(), null, "req_1");

        assertEquals("groq", res.getProvider());
        assertFalse(res.isFallbackUsed());
        assertEquals(1_000, res.getLatencyMs());
        assertEquals(0, gemini.calls());
        assertTrue(clock.sleeps().isEmpty());
    }

    @Test
    void primaryFailsOnce_retrySucceeds_noFallback() {
        var groq = groq().fail(ErrorCode.PROVIDER_UNAVAILABLE, ms(200)).ok(s(1));
        var gemini = gemini();

        ChatResponse res = service(groq, gemini).chat(request(), null, "req_1");

        assertEquals("groq", res.getProvider());
        assertFalse(res.isFallbackUsed());
        assertEquals(2, groq.calls());
        assertEquals(0, gemini.calls());
        assertEquals(List.of(s(1)), clock.sleeps());
        assertEquals(2_200, res.getLatencyMs());         // 200 + 1000 (ngủ) + 1000
    }

    // ---------- fallback ----------

    @Test
    void primaryRetryableTwice_fallbackSucceeds() {
        var groq = groq()
                .fail(ErrorCode.PROVIDER_UNAVAILABLE, ms(200))
                .fail(ErrorCode.PROVIDER_UNAVAILABLE, ms(200));
        var gemini = gemini().ok(s(2));

        ChatResponse res = service(groq, gemini).chat(request(), null, "req_1");

        assertEquals("gemini", res.getProvider());
        assertEquals("m-model", res.getModel());
        assertTrue(res.isFallbackUsed());
        assertEquals(2, groq.calls());
        assertEquals(3_400, res.getLatencyMs());         // 200 + 1000 + 200 + 2000
    }

    @Test
    void authFailed_fallsBackImmediately_noRetry() {
        var groq = groq().fail(ErrorCode.PROVIDER_AUTH_FAILED, ms(300));
        var gemini = gemini().ok(s(1));

        ChatResponse res = service(groq, gemini).chat(request(), null, "req_1");

        assertEquals("gemini", res.getProvider());
        assertTrue(res.isFallbackUsed());
        assertEquals(1, groq.calls());                   // key sai thì retry vô ích
        assertTrue(clock.sleeps().isEmpty());
        assertEquals(1_300, res.getLatencyMs());
    }

    @Test
    void badRequest_noRetry_noFallback() {
        var groq = groq().fail(ErrorCode.PROVIDER_BAD_REQUEST, ms(300));
        var gemini = gemini();

        ProviderException ex = assertThrows(ProviderException.class,
                () -> service(groq, gemini).chat(request(), null, "req_1"));

        assertEquals(ErrorCode.PROVIDER_BAD_REQUEST, ex.getErrorCode());
        assertEquals("groq", ex.getProvider());
        assertEquals(1, groq.calls());
        assertEquals(0, gemini.calls());                 // request hỏng thì gửi sang đâu cũng hỏng
    }

    @Test
    void bothFail_throwsFallbackError_withPrimarySuppressed() {
        var groq = groq()
                .fail(ErrorCode.PROVIDER_UNAVAILABLE, ms(200))
                .fail(ErrorCode.PROVIDER_UNAVAILABLE, ms(200));
        var gemini = gemini().fail(ErrorCode.PROVIDER_ERROR, ms(500));

        ProviderException ex = assertThrows(ProviderException.class,
                () -> service(groq, gemini).chat(request(), null, "req_1"));

        assertEquals(ErrorCode.PROVIDER_ERROR, ex.getErrorCode());
        assertEquals("gemini", ex.getProvider());

        assertEquals(1, ex.getSuppressed().length);
        ProviderException primary = (ProviderException) ex.getSuppressed()[0];
        assertEquals(ErrorCode.PROVIDER_UNAVAILABLE, primary.getErrorCode());
        assertEquals("groq", primary.getProvider());
    }

    // ---------- deadline ----------

    @Test
    void worstCase_allTimeouts_staysWithinDeadline() {
        var groq = groq()
                .fail(ErrorCode.PROVIDER_TIMEOUT, T15)
                .fail(ErrorCode.PROVIDER_TIMEOUT, T15);
        var gemini = gemini().fail(ErrorCode.PROVIDER_TIMEOUT, T15);

        ProviderException ex = assertThrows(ProviderException.class,
                () -> service(groq, gemini).chat(request(), null, "req_1"));

        assertEquals(ErrorCode.PROVIDER_TIMEOUT, ex.getErrorCode());
        assertEquals(1, gemini.calls());
        assertEquals(s(46), clock.elapsed());            // 15 + 1 + 15 + 15
        assertTrue(clock.elapsed().compareTo(prodRetry.deadline()) <= 0);
    }

    @Test
    void deadline45s_primaryUsesBudget_fallbackSkipped() {
        var retry45 = new RetryProperties(
                2, 1, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(4), Duration.ofSeconds(45));
        var groq = groq()
                .fail(ErrorCode.PROVIDER_TIMEOUT, T15)
                .fail(ErrorCode.PROVIDER_TIMEOUT, T15);
        var gemini = gemini();

        ProviderException ex = assertThrows(ProviderException.class,
                () -> service(groq, gemini, retry45).chat(request(), null, "req_1"));

        assertEquals(ErrorCode.PROVIDER_TIMEOUT, ex.getErrorCode());
        assertEquals("gemini", ex.getProvider());
        assertEquals(0, gemini.calls());
        assertEquals(s(31), clock.elapsed());
        assertEquals(ErrorCode.PROVIDER_TIMEOUT,
                ((ProviderException) ex.getSuppressed()[0]).getErrorCode());
    }

    @Test
    void success_recordedOnce() {
        var groq = groq().ok(s(1));

        service(groq, gemini()).chat(request(), null, "req_1");

        CallContext ctx = recorder.only();
        assertTrue(ctx.succeeded());
        assertEquals("groq", ctx.getProvider());
        assertEquals(1, ctx.getAttempts());
        assertEquals(1_000, ctx.getLatencyMs());
        assertFalse(ctx.isFallbackUsed());
    }

    @Test
    void retryThenFallback_countsEveryAttempt() {
        var groq = groq()
                .fail(ErrorCode.PROVIDER_UNAVAILABLE, ms(200))
                .fail(ErrorCode.PROVIDER_UNAVAILABLE, ms(200));
        var gemini = gemini().ok(s(2));

        service(groq, gemini).chat(request(), null, "req_1");

        CallContext ctx = recorder.only();
        assertTrue(ctx.succeeded());
        assertEquals("gemini", ctx.getProvider());
        assertEquals(3, ctx.getAttempts());          // groq 2 + gemini 1
        assertTrue(ctx.isFallbackUsed());
        assertEquals(3_400, ctx.getLatencyMs());
    }

    @Test
    void bothFail_recordedAsErrorOfFallback() {
        var groq = groq()
                .fail(ErrorCode.PROVIDER_UNAVAILABLE, ms(200))
                .fail(ErrorCode.PROVIDER_UNAVAILABLE, ms(200));
        var gemini = gemini().fail(ErrorCode.PROVIDER_ERROR, ms(500));

        assertThrows(ProviderException.class, () -> service(groq, gemini).chat(request(), null, "req_1"));

        CallContext ctx = recorder.only();
        assertEquals(ErrorCode.PROVIDER_ERROR, ctx.getErrorCode());
        assertEquals("gemini", ctx.getProvider());
        assertEquals(3, ctx.getAttempts());
        assertTrue(ctx.isFallbackUsed());
        assertNull(ctx.getUsage());
    }

    @Test
    void badRequest_recordedWithoutFallback() {
        var groq = groq().fail(ErrorCode.PROVIDER_BAD_REQUEST, ms(300));

        assertThrows(ProviderException.class, () -> service(groq, gemini()).chat(request(), null, "req_1"));

        CallContext ctx = recorder.only();
        assertEquals(ErrorCode.PROVIDER_BAD_REQUEST, ctx.getErrorCode());
        assertEquals(1, ctx.getAttempts());
        assertFalse(ctx.isFallbackUsed());
    }

    @Test
    void deadlineSkipsFallback_attemptsShowFallbackNeverCalled() {
        var retry45 = new RetryProperties(
                2, 1, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(4), Duration.ofSeconds(45));
        var groq = groq()
                .fail(ErrorCode.PROVIDER_TIMEOUT, T15)
                .fail(ErrorCode.PROVIDER_TIMEOUT, T15);

        assertThrows(ProviderException.class,
                () -> service(groq, gemini(), retry45).chat(request(), null, "req_1"));

        CallContext ctx = recorder.only();
        assertEquals(ErrorCode.PROVIDER_TIMEOUT, ctx.getErrorCode());
        assertEquals(2, ctx.getAttempts());          // chỉ có 2 lần gọi groq, gemini chưa từng được gọi
        assertTrue(ctx.isFallbackUsed());            // đã đi tới bước fallback
        assertEquals(31_000, ctx.getLatencyMs());
    }
}