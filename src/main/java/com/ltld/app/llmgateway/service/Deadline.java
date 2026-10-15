package com.ltld.app.llmgateway.service;

import java.time.Duration;

public record Deadline(long expiresAtNanos, Ticker ticker) {

    public static Deadline after(Duration budget, Ticker ticker) {
        return new Deadline(ticker.nanoTime() + budget.toNanos(), ticker);
    }

    public Duration remaining() {
        long left = expiresAtNanos - ticker.nanoTime();
        return Duration.ofNanos(Math.max(0, left));
    }

    public boolean allows(Duration needed) {
        return remaining().compareTo(needed) >= 0;
    }
}
