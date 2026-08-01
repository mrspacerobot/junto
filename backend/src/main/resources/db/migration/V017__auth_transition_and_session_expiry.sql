-- Complete auth columns that were previously created outside immutable Flyway
-- history. Existing serialized sessions can contain com.onda class names and
-- are intentionally expired during the Onda -> Junto deployment.
ALTER TABLE auth_tokens ADD COLUMN IF NOT EXISTS invite_code VARCHAR(12);

DELETE FROM spring_session;

-- Serialize concurrent code requests per normalized email by allowing only one
-- current (unused) token. requestCode removes the previous row before insert.
CREATE UNIQUE INDEX IF NOT EXISTS idx_auth_tokens_one_unused_per_email
    ON auth_tokens(email) WHERE used_at IS NULL;

-- Fixed-window endpoint throttles. Keys are SHA-256 digests, not raw emails or
-- IP addresses, so rate-limit metadata does not become a second PII store.
CREATE TABLE auth_rate_limits (
    rate_key VARCHAR(64) NOT NULL,
    action VARCHAR(40) NOT NULL,
    window_started_at TIMESTAMPTZ NOT NULL,
    request_count INTEGER NOT NULL,
    PRIMARY KEY (rate_key, action),
    CONSTRAINT chk_auth_rate_limit_count CHECK (request_count > 0)
);
CREATE INDEX idx_auth_rate_limits_window ON auth_rate_limits(window_started_at);
