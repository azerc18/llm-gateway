package com.ltld.app.llmgateway.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

class FakeClock implements Ticker, Sleeper {
    private long nanos = 0;
    private final List<Duration> sleeps = new ArrayList<>();

    @Override public long nanoTime() { return nanos; }

    @Override public void sleep(Duration d) {
        sleeps.add(d);
        advance(d);
    }

    void advance(Duration d) { nanos += d.toNanos(); }
    Duration elapsed()       { return Duration.ofNanos(nanos); }
    List<Duration> sleeps()  { return sleeps; }
}