-- Shared, atomic chat rate limiting across application instances.
CREATE TABLE chat_rate_limits (
    activity_id UUID NOT NULL REFERENCES activities(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    window_started_at TIMESTAMPTZ NOT NULL,
    request_count INTEGER NOT NULL,
    PRIMARY KEY (activity_id, user_id),
    CONSTRAINT chk_chat_rate_limit_count CHECK (request_count > 0)
);
CREATE INDEX idx_chat_rate_limits_window ON chat_rate_limits(window_started_at);
