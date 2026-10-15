package com.ltld.app.llmgateway.service;

import com.ltld.app.llmgateway.dto.chatdto.ChatResponse;
import com.ltld.app.llmgateway.entity.UsageLog;
import com.ltld.app.llmgateway.enums.FinishReason;
import com.ltld.app.llmgateway.enums.UsageStatus;
import com.ltld.app.llmgateway.exception.AppException;
import com.ltld.app.llmgateway.exception.ErrorCode;
import com.ltld.app.llmgateway.exception.ProviderException;
import com.ltld.app.llmgateway.provider.ProviderResult;
import com.ltld.app.llmgateway.repository.UsageLogRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UsageRecorderTest {

    private final UsageLogRepository repo = mock(UsageLogRepository.class);
    private final UsageRecorder recorder = new UsageRecorder(repo);

    private static CallContext ctx() {
        return new CallContext("req_1", null, "chat-fast");
    }

    private static ProviderResult result() {
        var usage = ChatResponse.Usage.builder()
                .inputTokens(10).outputTokens(5).totalTokens(15).build();
        return new ProviderResult("groq", "openai/gpt-oss-120b", "hi", FinishReason.STOP, usage);
    }

    private UsageLog savedEntity() {
        ArgumentCaptor<UsageLog> captor = ArgumentCaptor.forClass(UsageLog.class);
        verify(repo).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void success_mapsAllFields() {
        CallContext ctx = ctx();
        ctx.attempt("groq");
        ctx.success(result());
        ctx.finish(1_200);

        recorder.record(ctx);
        UsageLog log = savedEntity();

        assertEquals("req_1", log.getRequestId());
        assertEquals("chat-fast", log.getRequestedModel());
        assertEquals(UsageStatus.SUCCESS, log.getStatus());
        assertEquals("groq", log.getProvider());
        assertEquals("openai/gpt-oss-120b", log.getActualModel());
        assertEquals(15L, log.getTotalTokens());
        assertEquals(FinishReason.STOP, log.getFinishReason());
        assertEquals(1, log.getAttemptCount());
        assertEquals(1_200, log.getLatencyMs());
        assertNull(log.getErrorCode());
        assertFalse(log.isFallbackUsed());
    }

    @Test
    void bothProvidersFail_recordsLastProviderAndAllAttempts() {
        CallContext ctx = ctx();
        ctx.attempt("groq");
        ctx.attempt("groq");
        ctx.fallbackStarted();
        ctx.attempt("gemini");
        ctx.failure(new ProviderException(ErrorCode.PROVIDER_ERROR, "gemini", 500, null));
        ctx.finish(3_000);

        recorder.record(ctx);
        UsageLog log = savedEntity();

        assertEquals(UsageStatus.ERROR, log.getStatus());
        assertEquals(ErrorCode.PROVIDER_ERROR, log.getErrorCode());
        assertEquals("gemini", log.getProvider());
        assertEquals(3, log.getAttemptCount());
        assertTrue(log.isFallbackUsed());
        assertNull(log.getTotalTokens());            // lỗi thì không có token
        assertNull(log.getFinishReason());
    }

    @Test
    void failureBeforeProvider_hasNoProviderAndZeroAttempts() {
        CallContext ctx = ctx();
        ctx.failure(new AppException(ErrorCode.MODEL_NOT_SUPPORTED));

        recorder.record(ctx);
        UsageLog log = savedEntity();

        assertEquals(ErrorCode.MODEL_NOT_SUPPORTED, log.getErrorCode());
        assertNull(log.getProvider());
        assertEquals(0, log.getAttemptCount());
    }

    @Test
    void unexpectedException_recordedAsInternalError() {
        CallContext ctx = ctx();
        ctx.failure(new IllegalStateException("boom"));

        recorder.record(ctx);

        assertEquals(ErrorCode.INTERNAL_ERROR, savedEntity().getErrorCode());
    }

    @Test
    void dbFailure_isSwallowed() {
        when(repo.save(any(UsageLog.class)))
                .thenThrow(new DataIntegrityViolationException("db down"));
        CallContext ctx = ctx();
        ctx.attempt("groq");
        ctx.success(result());

        assertDoesNotThrow(() -> recorder.record(ctx));
        verify(repo).save(any(UsageLog.class));       // đã thử ghi, chỉ là không ném lỗi ra
    }
}