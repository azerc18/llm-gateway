CREATE TABLE api_client (
    id                    BIGSERIAL PRIMARY KEY,
    name                  VARCHAR(100) NOT NULL,
    api_key_hash          VARCHAR(64)  NOT NULL UNIQUE,
    daily_token_quota     BIGINT       NOT NULL,
    rate_limit_per_minute INTEGER      NOT NULL,
    status                VARCHAR(20)  NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);