package com.ltld.app.llmgateway.service;

import com.ltld.app.llmgateway.dto.chatdto.ChatResponse;
import com.ltld.app.llmgateway.entity.UsageLog;
import com.ltld.app.llmgateway.enums.UsageStatus;
import com.ltld.app.llmgateway.repository.UsageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class UsageRecorder {

    private final UsageLogRepository repository;

    /** Không bao giờ ném exception: ghi log là việc phụ, không được làm hỏng response. */
    public void record(CallContext ctx) {
        try {
            repository.save(toEntity(ctx));
        } catch (RuntimeException e) {
            ChatResponse.Usage u = ctx.getUsage();
            log.error("usage_log write FAILED requestId={} clientId={} model={} provider={} status={} errorCode={} totalTokens={} cause={}",
                    ctx.getRequestId(),
                    ctx.getClient() == null ? null : ctx.getClient().getId(),
                    ctx.getRequestedModel(), ctx.getProvider(),
                    ctx.succeeded() ? "SUCCESS" : "ERROR", ctx.getErrorCode(),
                    u == null ? null : u.getTotalTokens(),
                    e.toString());
        }
    }

    static UsageLog toEntity(CallContext ctx) {
        ChatResponse.Usage u = ctx.getUsage();
        return UsageLog.builder()
                .requestId(ctx.getRequestId())
                .client(ctx.getClient())
                .requestedModel(ctx.getRequestedModel())
                .provider(ctx.getProvider())
                .actualModel(ctx.getActualModel())
                .inputTokens(u == null ? null : u.getInputTokens())
                .reasoningTokens(u == null ? null : u.getReasoningTokens())
                .outputTokens(u == null ? null : u.getOutputTokens())
                .totalTokens(u == null ? null : u.getTotalTokens())
                .latencyMs(ctx.getLatencyMs())
                .status(ctx.succeeded() ? UsageStatus.SUCCESS : UsageStatus.ERROR)
                .errorCode(ctx.getErrorCode())
                .finishReason(ctx.getFinishReason())
                .attemptCount(ctx.getAttempts())
                .fallbackUsed(ctx.isFallbackUsed())
                .build();
    }
}