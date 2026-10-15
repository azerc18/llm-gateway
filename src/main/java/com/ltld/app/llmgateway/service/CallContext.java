package com.ltld.app.llmgateway.service;

import com.ltld.app.llmgateway.dto.chatdto.ChatResponse;
import com.ltld.app.llmgateway.entity.ApiClient;
import com.ltld.app.llmgateway.enums.FinishReason;
import com.ltld.app.llmgateway.exception.AppException;
import com.ltld.app.llmgateway.exception.ErrorCode;
import com.ltld.app.llmgateway.exception.ProviderException;
import com.ltld.app.llmgateway.provider.ProviderResult;
import lombok.Getter;

@Getter
public class CallContext {

    private final String requestId;
    private final ApiClient client;
    private final String requestedModel;

    private String provider;
    private String actualModel;
    private ChatResponse.Usage usage;
    private FinishReason finishReason;
    private ErrorCode errorCode;
    private int attempts;
    private boolean fallbackUsed;
    private long latencyMs;

    public CallContext(String requestId, ApiClient client, String requestedModel) {
        this.requestId = requestId;
        this.client = client;
        this.requestedModel = requestedModel;
    }

    /** Gọi NGAY TRƯỚC mỗi lần gọi provider (kể cả các lần retry). */
    public void attempt(String provider) {
        this.attempts++;
        this.provider = provider;
    }

    public void fallbackStarted() {
        this.fallbackUsed = true;
    }

    public void success(ProviderResult result) {
        this.provider = result.provider();
        this.actualModel = result.model();
        this.usage = result.usage();
        this.finishReason = result.finishReason();
    }

    public void failure(RuntimeException e) {
        if (e instanceof AppException app) {
            this.errorCode = app.getErrorCode();
        } else {
            this.errorCode = ErrorCode.INTERNAL_ERROR;   // lỗi không lường trước vẫn phải có mã
        }
        if (e instanceof ProviderException pe) {
            this.provider = pe.getProvider();
        }
    }

    public void finish(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public boolean succeeded() {
        return errorCode == null;
    }
}