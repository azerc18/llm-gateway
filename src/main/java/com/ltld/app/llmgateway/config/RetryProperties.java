package com.ltld.app.llmgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "llm.retry")
public record RetryProperties(
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("1s") Duration initialBackoff,
        @DefaultValue("2.0") double multiplier,
        @DefaultValue("4s") Duration maxBackoff
) {
    public RetryProperties {
        if (maxAttempts < 1) {
            throw new IllegalStateException("llm.retry.max-attempts must be >= 1");
        }
        if (multiplier < 1.0) {
            throw new IllegalStateException("llm.retry.multiplier must be >= 1.0");
        }
        if(!initialBackoff().isPositive() || !maxBackoff.isPositive() ){
            throw new IllegalStateException("llm.retry.initialBackoff must be > 0");
        }
        if(initialBackoff.compareTo(maxBackoff) > 0){
            throw new IllegalStateException("llm.retry.initialBackoff must be smaller than maxBackoff");
        }
    }

    public Duration backoffFor(int retryNumber) {
        double millis = initialBackoff.toMillis() * Math.pow(multiplier, retryNumber - 1);
        return Duration.ofMillis((long) Math.min(millis, maxBackoff.toMillis()));
    }
}