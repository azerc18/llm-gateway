CREATE TABLE usage_log(
    id               BIGSERIAL PRIMARY KEY,
    request_id       VARCHAR(64)  NOT NULL UNIQUE,
    client_id        BIGINT       NOT NULL REFERENCES api_client (id),
    requested_model  VARCHAR(100) NOT NULL,
    provider         VARCHAR(30),
    actual_model     VARCHAR(100),
    input_tokens     BIGINT,
    reasoning_tokens BIGINT,
    output_tokens    BIGINT,
    total_tokens     BIGINT,
    latency_ms       BIGINT       NOT NULL,
    status           VARCHAR(10)  NOT NULL CHECK (status IN ('SUCCESS', 'ERROR')),
    error_code       VARCHAR(40),
    finish_reason    VARCHAR(30),
    attempt_count    INTEGER      NOT NULL DEFAULT 0,
    fallback_used    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_usage_log_error
        CHECK ((status = 'SUCCESS') = (error_code IS NULL))
);

CREATE INDEX idx_usage_log_client_created ON usage_log (client_id, created_at);