INSERT INTO api_client (name, api_key_hash, daily_token_quota, rate_limit_per_minute, status)
VALUES ('dev-local', '62ff47fc38cdfc59640c3c521806bae9691427fb522a7b955622bdfbd7f102d5', 100000, 20, 'ACTIVE')
    ON CONFLICT (api_key_hash) DO NOTHING;