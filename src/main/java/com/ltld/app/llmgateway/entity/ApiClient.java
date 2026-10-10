package com.ltld.app.llmgateway.entity;

import com.ltld.app.llmgateway.enums.ClientStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "api_client")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class ApiClient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "api_key_hash", nullable = false, unique = true, length = 64)
    private String apiKeyHash;

    @Column(name = "daily_token_quota", nullable = false)
    private Long dailyTokenQuota;

    @Column(name = "rate_limit_per_minute", nullable = false)
    private Integer rateLimitPerMinute;

    @Column(length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ClientStatus status = ClientStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private Instant createdAt;
}
