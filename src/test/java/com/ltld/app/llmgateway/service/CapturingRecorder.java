package com.ltld.app.llmgateway.service;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CapturingRecorder extends UsageRecorder {

    private final List<CallContext> records = new ArrayList<>();

    CapturingRecorder() {
        super(null);
    }

    @Override
    public void record(CallContext ctx) {
        records.add(ctx);
    }

    CallContext only() {
        assertEquals(1, records.size(), "mỗi request phải ghi đúng 1 dòng usage_log");
        return records.get(0);
    }
}