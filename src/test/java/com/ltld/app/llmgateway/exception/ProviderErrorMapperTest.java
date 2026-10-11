package com.ltld.app.llmgateway.exception;

import org.junit.jupiter.api.Test;

import static com.ltld.app.llmgateway.exception.ErrorCode.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ProviderErrorMapperTest {
    @Test
    void mapsStatusCodes() {
        assertEquals(PROVIDER_AUTH_FAILED,  ProviderErrorMapper.codeForStatus(401));
        assertEquals(PROVIDER_BAD_REQUEST,  ProviderErrorMapper.codeForStatus(404));
        assertEquals(PROVIDER_BAD_REQUEST,  ProviderErrorMapper.codeForStatus(413));
        assertEquals(PROVIDER_RATE_LIMITED, ProviderErrorMapper.codeForStatus(429));
        assertEquals(PROVIDER_UNAVAILABLE,  ProviderErrorMapper.codeForStatus(503));
        assertEquals(PROVIDER_ERROR,        ProviderErrorMapper.codeForStatus(500));
    }
}
