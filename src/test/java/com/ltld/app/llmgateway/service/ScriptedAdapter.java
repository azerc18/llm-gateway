package com.ltld.app.llmgateway.service;

import com.ltld.app.llmgateway.dto.chatdto.ChatRequest;
import com.ltld.app.llmgateway.dto.chatdto.ChatResponse;
import com.ltld.app.llmgateway.enums.FinishReason;
import com.ltld.app.llmgateway.exception.ErrorCode;
import com.ltld.app.llmgateway.exception.ProviderException;
import com.ltld.app.llmgateway.provider.ProviderAdapter;
import com.ltld.app.llmgateway.provider.ProviderResult;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;

class ScriptedAdapter implements ProviderAdapter {

    private record Step(Duration cost, ErrorCode error) {}

    private final String name;
    private final FakeClock clock;
    private final Deque<Step> steps = new ArrayDeque<>();
    private int calls = 0;

    ScriptedAdapter(String name, FakeClock clock) {
        this.name = name;
        this.clock = clock;
    }

    ScriptedAdapter fail(ErrorCode code, Duration cost) { steps.add(new Step(cost, code)); return this; }
    ScriptedAdapter ok(Duration cost)                   { steps.add(new Step(cost, null)); return this; }

    int calls() { return calls; }

    @Override public String name() { return name; }

    @Override
    public ProviderResult chat(ChatRequest request, String providerModel) {
        calls++;
        Step step = steps.poll();
        if (step == null) {
            throw new AssertionError(name + " bị gọi lần " + calls + ", ngoài kịch bản");
        }
        clock.advance(step.cost());
        if (step.error() != null) {
            throw new ProviderException(step.error(), name, null, null);
        }
        var usage = ChatResponse.Usage.builder().inputTokens(1).outputTokens(1).totalTokens(2).build();
        return new ProviderResult(name, providerModel, "ok from " + name, FinishReason.STOP, usage);
    }
}