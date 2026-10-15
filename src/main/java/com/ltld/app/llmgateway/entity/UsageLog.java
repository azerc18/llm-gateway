package com.ltld.app.llmgateway.entity;

import com.ltld.app.llmgateway.enums.FinishReason;
import com.ltld.app.llmgateway.enums.UsageStatus;
import com.ltld.app.llmgateway.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "usage_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, unique = true, length = 64)
    private String requestId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private ApiClient client;

    @Column(name = "requested_model", nullable = false, length = 100)
    private String requestedModel;

    @Column(length = 30)
    private String provider;

    @Column(name = "actual_model", length = 100)
    private String actualModel;

    @Column(name = "input_tokens")
    private Long inputTokens;

    @Column(name = "reasoning_tokens")
    private Long reasoningTokens;

    @Column(name = "output_tokens")
    private Long outputTokens;

    @Column(name = "total_tokens")
    private Long totalTokens;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UsageStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_code", length = 40)
    private ErrorCode errorCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "finish_reason", length = 30)
    private FinishReason finishReason;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;                   // primitive: không bao giờ null

    @Column(name = "fallback_used", nullable = false)
    private boolean fallbackUsed;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}