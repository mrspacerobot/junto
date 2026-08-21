-- Shared access codes admit more than one account, which invite_codes cannot
-- express: it holds a single used_by/used_at pair and consumeIfAvailable's
-- `used_at IS NULL` guard is the single-use rule itself. Shared codes therefore
-- get their own tables instead of a flag that would have to weaken that guard.
CREATE TABLE access_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(64) NOT NULL UNIQUE,
    label VARCHAR(64) NOT NULL,
    created_by UUID NOT NULL REFERENCES users(id),
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Bootstrapping the first administrator is a one-time operation, so at most one
-- code may carry the bootstrap label. This index, not application logic, is the
-- final invariant.
CREATE UNIQUE INDEX uq_access_codes_bootstrap ON access_codes (label) WHERE label = 'bootstrap';

-- Redemptions are what invite_codes.used_by is for single-use invites: the
-- record of how an account was admitted. Rows are never deleted, and revocation
-- is access_codes.revoked_at, exactly as invite_codes.revoked_at works.
CREATE TABLE access_code_redemptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    access_code_id UUID NOT NULL REFERENCES access_codes(id),
    user_id UUID NOT NULL REFERENCES users(id),
    redeemed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_access_code_redemption UNIQUE (access_code_id, user_id)
);
CREATE INDEX idx_access_code_redemptions_user ON access_code_redemptions(user_id);

-- A signup code carried by auth_tokens can now be an operator-chosen shared
-- code instead of only the 12-character generated invite format.
ALTER TABLE auth_tokens ALTER COLUMN invite_code TYPE VARCHAR(64);
