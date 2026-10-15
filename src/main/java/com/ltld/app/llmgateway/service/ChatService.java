package com.ltld.app.llmgateway.service;

import com.ltld.app.llmgateway.config.LlmProperties;
import com.ltld.app.llmgateway.config.RetryProperties;
import com.ltld.app.llmgateway.dto.chatdto.ChatRequest;
import com.ltld.app.llmgateway.dto.chatdto.ChatResponse;
import com.ltld.app.llmgateway.entity.ApiClient;
import com.ltld.app.llmgateway.enums.FinishReason;
import com.ltld.app.llmgateway.exception.ProviderException;
import com.ltld.app.llmgateway.provider.ProviderAdapter;
import com.ltld.app.llmgateway.provider.ProviderResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ChatService {

    private final Map<String, ProviderAdapter> adapters;
    private final ModelRouter router;
    private final RetryExecutor retry;
    private final RetryProperties retryProps;
    private final LlmProperties llmProps;
    private final Ticker ticker;
    private final UsageRecorder usageRecorder;

    public ChatService(List<ProviderAdapter> adapterList, ModelRouter router, RetryExecutor retry,
                       RetryProperties retryProps, LlmProperties llmProps, Ticker ticker,
                       UsageRecorder usageRecorder) {
        this.adapters = adapterList.stream().collect(Collectors.toMap(ProviderAdapter::name, a -> a));
        this.router = router;
        this.retry = retry;
        this.retryProps = retryProps;
        this.llmProps = llmProps;
        this.ticker = ticker;
        this.usageRecorder = usageRecorder;
    }

    public ChatResponse chat(ChatRequest request, ApiClient client, String requestId) {
        long start = ticker.nanoTime();
        CallContext ctx = new CallContext(requestId, client, request.getModel());
        try {
            return doChat(request, requestId, ctx, start);
        } catch (RuntimeException e) {
            ctx.failure(e);
            throw e;                              // ném lại nguyên vẹn → GlobalExceptionHandler xử lý như cũ
        } finally {
            ctx.finish(elapsedMs(start));
            usageRecorder.record(ctx);            // LUÔN chạy, và không bao giờ ném lỗi
        }
    }

    private ChatResponse doChat(ChatRequest request, String requestId, CallContext ctx, long start) {
        Deadline deadline = Deadline.after(retryProps.deadline(), ticker);
        LlmProperties.Route route = router.resolve(request.getModel());

        RetryExecutor.Outcome outcome;
        boolean fallbackUsed = false;

        try {
            outcome = call(route.primary(), request, retryProps.maxAttempts(), deadline, ctx);
        } catch (ProviderException primaryError) {
            if (route.fallback() == null || !primaryError.isFallbackAllowed()) {
                throw primaryError;
            }
            log.warn("primary {} failed with {}, falling back to {}",
                    route.primary().provider(), primaryError.getErrorCode(), route.fallback().provider());
            ctx.fallbackStarted();
            try {
                outcome = call(route.fallback(), request, retryProps.fallbackMaxAttempts(), deadline, ctx);
                fallbackUsed = true;
            } catch (ProviderException fallbackError) {
                fallbackError.addSuppressed(primaryError);
                throw fallbackError;
            }
        }

        ProviderResult result = outcome.result();
        ctx.success(result);
        long latencyMs = elapsedMs(start);

        log.info("chat ok provider={} model={} latencyMs={} retries={} fallback={} totalTokens={} finish={}",
                result.provider(), result.model(), latencyMs, outcome.retries(), fallbackUsed,
                result.usage().getTotalTokens(), result.finishReason());

        return ChatResponse.builder()
                .requestId(requestId)
                .provider(result.provider())
                .model(result.model())
                .content(result.content())
                .finishReason(result.finishReason())
                .usage(result.usage())
                .latencyMs(latencyMs)
                .fallbackUsed(fallbackUsed)
                .build();
    }

    private RetryExecutor.Outcome call(LlmProperties.Target target, ChatRequest request,
                                       int maxAttempts, Deadline deadline, CallContext ctx) {
        ProviderAdapter adapter = adapters.get(target.provider());
        Duration timeout = llmProps.provider(target.provider()).timeout();
        return retry.execute(target.provider(), maxAttempts, timeout, deadline, () -> {
            ctx.attempt(target.provider());
            return adapter.chat(request, target.model());
        });
    }

    private long elapsedMs(long start) {
        return (ticker.nanoTime() - start) / 1_000_000;
    }
}
