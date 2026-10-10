package com.ltld.app.llmgateway.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    INVALID_REQUEST("INVALID_REQUEST","The request is invalid. Please check the request body and try again.", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("UNAUTHORIZED","Missing or invalid API key.", HttpStatus.UNAUTHORIZED),
    MODEL_NOT_SUPPORTED("MODEL_NOT_SUPPORTED","The requested model is not supported.", HttpStatus.BAD_REQUEST),
    QUOTA_EXCEEDED("QUOTA_EXCEEDED","Token quota exceeded. Please try again after your quota resets.", HttpStatus.TOO_MANY_REQUESTS),
    RATE_LIMITED("RATE_LIMITED","Too many requests. Please slow down and try again later.", HttpStatus.TOO_MANY_REQUESTS),
    PROVIDER_UNAVAILABLE("PROVIDER_UNAVAILABLE","The upstream AI provider is temporarily unavailable. Please try again later", HttpStatus.SERVICE_UNAVAILABLE),
    PROVIDER_ERROR("PROVIDER_ERROR","The upstream AI provider returned an error. Please try again later.", HttpStatus.BAD_GATEWAY),
    PROVIDER_RATE_LIMITED("PROVIDER_RATE_LIMITED","The upstream AI provider is limiting requests right now. Please try again later.", HttpStatus.SERVICE_UNAVAILABLE),
    PROVIDER_TIMEOUT("PROVIDER_TIMEOUT","The upstream AI provider did not respond in time. Please try again later.", HttpStatus.GATEWAY_TIMEOUT),
    PROVIDER_AUTH_FAILED("PROVIDER_AUTH_FAILED","The gateway could not authenticate with the upstream AI provider. Please contact the administrator.", HttpStatus.BAD_GATEWAY),
    PROVIDER_BAD_REQUEST("PROVIDER_BAD_REQUEST","The upstream AI provider rejected the request. Please contact the administrator.", HttpStatus.BAD_GATEWAY),
    INTERNAL_ERROR("INTERNAL_ERROR","An unexpected error occurred. Please try again later.", HttpStatus.INTERNAL_SERVER_ERROR)
    ;

    ErrorCode(String code, String message, HttpStatus statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }

    private final String code;
    private final String message;
    private final HttpStatus statusCode;
}
