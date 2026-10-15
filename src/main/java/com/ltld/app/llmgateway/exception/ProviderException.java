package com.ltld.app.llmgateway.exception;

import lombok.Getter;

@Getter
public class ProviderException extends AppException  {
    private final String provider;
    private final Integer upstreamStatus;

    public ProviderException(ErrorCode code, String provider, Integer upstreamStatus, Throwable cause) {
        super(code, cause);
        this.provider = provider;
        this.upstreamStatus = upstreamStatus;
    }

    public boolean isRetryable() {
        return switch (getErrorCode()) {
            case PROVIDER_UNAVAILABLE, PROVIDER_ERROR,
                 PROVIDER_RATE_LIMITED, PROVIDER_TIMEOUT -> true;
            default -> false;
        };
    }

    public boolean isFallbackAllowed() {
        return isRetryable() || getErrorCode() == ErrorCode.PROVIDER_AUTH_FAILED;
    }
}
