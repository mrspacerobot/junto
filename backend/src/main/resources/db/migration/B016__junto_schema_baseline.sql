-- Baseline for brand-new installations.
--
-- Released versioned migrations V001-V016 remain immutable for existing Onda
-- databases. Flyway uses this baseline migration only for a new environment,
-- then applies V017 and later forward migrations.

CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    avatar_url TEXT,
    bio TEXT,
    city VARCHAR(100),
    location_lat DOUBLE PRECISION,
    location_lng DOUBLE PRECISION,
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    invited_by UUID REFERENCES users(id),
    invite_codes_remaining INTEGER NOT NULL DEFAULT 5,
    onboarded BOOLEAN NOT NULL DEFAULT false,
    last_active_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE auth_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    token UUID NOT NULL UNIQUE,
    code VARCHAR(6) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    invite_code VARCHAR(12),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_auth_tokens_email_code ON auth_tokens (email, code);
CREATE INDEX idx_auth_tokens_token ON auth_tokens (token);
CREATE INDEX idx_auth_tokens_email_code_unused ON auth_tokens (email, code) WHERE used_at IS NULL;
CREATE INDEX idx_auth_tokens_token_unused ON auth_tokens (token) WHERE used_at IS NULL;

CREATE TABLE invite_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(12) NOT NULL UNIQUE,
    created_by UUID NOT NULL REFERENCES users(id),
    used_by UUID REFERENCES users(id),
    used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE spring_session (
    primary_id CHAR(36) NOT NULL,
    session_id CHAR(36) NOT NULL,
    creation_time BIGINT NOT NULL,
    last_access_time BIGINT NOT NULL,
    max_inactive_interval INT NOT NULL,
    expiry_time BIGINT NOT NULL,
    principal_name VARCHAR(100),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);
CREATE UNIQUE INDEX idx_spring_session_session_id ON spring_session (session_id);
CREATE INDEX idx_spring_session_expiry_time ON spring_session (expiry_time);
CREATE INDEX idx_spring_session_principal_name ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
    session_primary_id CHAR(36) NOT NULL,
    attribute_name VARCHAR(200) NOT NULL,
    attribute_bytes BYTEA NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES spring_session (primary_id) ON DELETE CASCADE
);

CREATE TABLE activities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_id UUID NOT NULL REFERENCES users(id),
    type_slug VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    city VARCHAR(100),
    location_lat DOUBLE PRECISION,
    location_lng DOUBLE PRECISION,
    location GEOGRAPHY(POINT, 4326),
    starts_at TIMESTAMPTZ NOT NULL,
    signup_deadline TIMESTAMPTZ,
    max_participants INTEGER NOT NULL DEFAULT 10,
    join_policy VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    custom_fields JSONB NOT NULL DEFAULT '{}',
    image_url VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_signup_before_start CHECK (signup_deadline IS NULL OR signup_deadline < starts_at),
    CONSTRAINT chk_activities_max_participants CHECK (max_participants > 0),
    CONSTRAINT chk_activities_coordinate_pair CHECK ((location_lat IS NULL) = (location_lng IS NULL)),
    CONSTRAINT chk_activities_latitude CHECK (location_lat IS NULL OR location_lat BETWEEN -90 AND 90),
    CONSTRAINT chk_activities_longitude CHECK (location_lng IS NULL OR location_lng BETWEEN -180 AND 180)
);
CREATE INDEX idx_activities_creator ON activities(creator_id);
CREATE INDEX idx_activities_type_slug ON activities(type_slug);
CREATE INDEX idx_activities_status ON activities(status);
CREATE INDEX idx_activities_starts_at ON activities(starts_at);
CREATE INDEX idx_activities_location ON activities USING GIST (location);
CREATE INDEX idx_activities_custom_fields_gin ON activities USING GIN (custom_fields);

CREATE OR REPLACE FUNCTION sync_activity_location()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.location_lat IS NOT NULL AND NEW.location_lng IS NOT NULL THEN
        NEW.location = ST_SetSRID(ST_MakePoint(NEW.location_lng, NEW.location_lat), 4326)::geography;
    ELSE
        NEW.location = NULL;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_activities_sync_location
BEFORE INSERT OR UPDATE OF location_lat, location_lng ON activities
FOR EACH ROW EXECUTE FUNCTION sync_activity_location();

CREATE TABLE activity_participants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id UUID NOT NULL REFERENCES activities(id),
    user_id UUID NOT NULL REFERENCES users(id),
    role VARCHAR(50) NOT NULL DEFAULT 'PARTICIPANT',
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(activity_id, user_id)
);
CREATE INDEX idx_activity_participants_activity ON activity_participants(activity_id);
CREATE INDEX idx_activity_participants_user ON activity_participants(user_id);

CREATE TABLE chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id UUID NOT NULL REFERENCES activities(id),
    user_id UUID REFERENCES users(id),
    content TEXT NOT NULL,
    type VARCHAR(50) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_chat_messages_activity ON chat_messages(activity_id);
CREATE INDEX idx_chat_messages_created_at ON chat_messages(created_at);

CREATE TABLE vouches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vouter_id UUID NOT NULL REFERENCES users(id),
    vouchee_id UUID NOT NULL REFERENCES users(id),
    activity_id UUID NOT NULL REFERENCES activities(id),
    content TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(vouter_id, vouchee_id, activity_id)
);
CREATE INDEX idx_vouches_vouchee ON vouches(vouchee_id);

CREATE TABLE reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id UUID NOT NULL REFERENCES users(id),
    target_type VARCHAR(50) NOT NULL,
    target_id UUID NOT NULL,
    reason VARCHAR(100) NOT NULL,
    content TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_reports_status ON reports(status);
CREATE INDEX idx_reports_target ON reports(target_type, target_id);

CREATE TABLE moderation_actions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    moderator_id UUID NOT NULL REFERENCES users(id),
    target_user_id UUID NOT NULL REFERENCES users(id),
    action_type VARCHAR(50) NOT NULL,
    reason TEXT NOT NULL,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_moderation_actions_user ON moderation_actions(target_user_id);
