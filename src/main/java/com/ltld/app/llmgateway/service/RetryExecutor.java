package com.ltld.app.llmgateway.service;

import com.ltld.app.llmgateway.config.RetryProperties;
import com.ltld.app.llmgateway.exception.ProviderException;
import com.ltld.app.llmgateway.provider.ProviderResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

@Component
@Slf4j
@RequiredArgsConstructor
public class RetryExecutor {

    private final RetryProperties props;
    private final Sleeper sleeper;

    public record Outcome(ProviderResult result, int retries) {}

    public Outcome execute(String provider, Supplier<ProviderResult> call) {
        int max = props.maxAttempts();

        for (int attempt = 1; ; attempt++) {
            try {
                return new Outcome(call.get(), attempt - 1);
            } catch (ProviderException e) {
                boolean lastAttempt = attempt >= max;
                if (!e.isRetryable() || lastAttempt) {
                    throw e;
                }
                Duration wait = props.backoffFor(attempt);
                log.warn("{} attempt {}/{} failed: {} (upstream {}), retrying in {} ms",
                        provider, attempt, max, e.getErrorCode(), e.getUpstreamStatus(), wait.toMillis());
                sleepOrAbort(wait, e);
            }
        }
    }

    private void sleepOrAbort(Duration wait, ProviderException cause) {
        try {
            sleeper.sleep(wait);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw cause;
        }
    }
}
