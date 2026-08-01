ALTER TABLE auth_tokens ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0;

CREATE INDEX idx_auth_tokens_email_code_unused ON auth_tokens (email, code) WHERE used_at IS NULL;
CREATE INDEX idx_auth_tokens_token_unused ON auth_tokens (token) WHERE used_at IS NULL;
