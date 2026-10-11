package com.ltld.app.llmgateway.exception;

import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;

public final class ProviderErrorMapper {
    private ProviderErrorMapper() {
    }

    public static ProviderException fromHttp(String provider, RestClientResponseException e) {
        int status = e.getStatusCode().value();
        ErrorCode code = codeForStatus(status);
        return new ProviderException(code, provider, status, e);
    }

    public static ProviderException fromIo(String provider, ResourceAccessException e) {
        if (e.getCause() instanceof SocketTimeoutException || e.getCause() instanceof HttpTimeoutException) {
            return new ProviderException(ErrorCode.PROVIDER_TIMEOUT, provider, null, e.getCause());
        } else {
            return new ProviderException(ErrorCode.PROVIDER_UNAVAILABLE, provider, null, e.getCause());
        }
    }

    static ErrorCode codeForStatus(int status) {
        return switch (status) {
            case 401, 403 -> ErrorCode.PROVIDER_AUTH_FAILED;
            case 429 -> ErrorCode.PROVIDER_RATE_LIMITED;
            case 503 -> ErrorCode.PROVIDER_UNAVAILABLE;
            default -> status >= 500 ? ErrorCode.PROVIDER_ERROR
                    : ErrorCode.PROVIDER_BAD_REQUEST;
        };
    }
}
