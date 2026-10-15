package com.ltld.app.llmgateway.service;

import com.ltld.app.llmgateway.config.RetryProperties;
import com.ltld.app.llmgateway.exception.ErrorCode;
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

    public Outcome execute(String provider, int maxAttempts, Duration callTimeout,
                           Deadline deadline, Supplier<ProviderResult> call) {

        ProviderException last = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {

            if (!deadline.allows(callTimeout)) {
                log.warn("{}: deadline too close ({} ms left < timeout {} ms), not starting attempt {}",
                        provider, deadline.remaining().toMillis(), callTimeout.toMillis(), attempt);
                break;
            }

            try {
                return new Outcome(call.get(), attempt - 1);
            } catch (ProviderException e) {
                last = e;
                if (!e.isRetryable() || attempt == maxAttempts) {
                    throw e;
                }
                Duration wait = props.backoffFor(attempt);
                log.warn("{} attempt {}/{} failed: {} (upstream {}), retrying in {} ms",
                        provider, attempt, maxAttempts, e.getErrorCode(), e.getUpstreamStatus(), wait.toMillis());
                sleepOrAbort(wait, e);
            }
        }

        if (last != null) {
            throw last;
        }
        throw new ProviderException(ErrorCode.PROVIDER_TIMEOUT, provider, null, null);
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
