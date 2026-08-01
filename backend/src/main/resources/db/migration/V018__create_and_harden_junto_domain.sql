-- Forward-only domain migration for existing Onda databases. New databases
-- receive the same tables from B016; IF NOT EXISTS keeps this migration safe on
-- both paths. An Onda database that has V016 necessarily already has activities,
-- because V016 indexes activities.custom_fields.

CREATE TABLE IF NOT EXISTS activities (
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
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE activities ADD COLUMN IF NOT EXISTS location_lat DOUBLE PRECISION;
ALTER TABLE activities ADD COLUMN IF NOT EXISTS location_lng DOUBLE PRECISION;
ALTER TABLE activities ADD COLUMN IF NOT EXISTS location GEOGRAPHY(POINT, 4326);
ALTER TABLE activities ADD COLUMN IF NOT EXISTS signup_deadline TIMESTAMPTZ;
ALTER TABLE activities ADD COLUMN IF NOT EXISTS image_url VARCHAR(500);
ALTER TABLE activities ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_activities_creator ON activities(creator_id);
CREATE INDEX IF NOT EXISTS idx_activities_type_slug ON activities(type_slug);
CREATE INDEX IF NOT EXISTS idx_activities_status ON activities(status);
CREATE INDEX IF NOT EXISTS idx_activities_starts_at ON activities(starts_at);
CREATE INDEX IF NOT EXISTS idx_activities_location ON activities USING GIST (location);
CREATE INDEX IF NOT EXISTS idx_activities_custom_fields_gin ON activities USING GIN (custom_fields);

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

DROP TRIGGER IF EXISTS trg_activities_sync_location ON activities;
CREATE TRIGGER trg_activities_sync_location
BEFORE INSERT OR UPDATE OF location_lat, location_lng ON activities
FOR EACH ROW EXECUTE FUNCTION sync_activity_location();

-- Reject impossible values at the persistence boundary as well as in API
-- validation. Add constraints only when a legacy database does not have them.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_signup_before_start') THEN
        ALTER TABLE activities ADD CONSTRAINT chk_signup_before_start
            CHECK (signup_deadline IS NULL OR signup_deadline < starts_at);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_activities_max_participants') THEN
        ALTER TABLE activities ADD CONSTRAINT chk_activities_max_participants
            CHECK (max_participants > 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_activities_coordinate_pair') THEN
        ALTER TABLE activities ADD CONSTRAINT chk_activities_coordinate_pair
            CHECK ((location_lat IS NULL) = (location_lng IS NULL));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_activities_latitude') THEN
        ALTER TABLE activities ADD CONSTRAINT chk_activities_latitude
            CHECK (location_lat IS NULL OR location_lat BETWEEN -90 AND 90);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_activities_longitude') THEN
        ALTER TABLE activities ADD CONSTRAINT chk_activities_longitude
            CHECK (location_lng IS NULL OR location_lng BETWEEN -180 AND 180);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS activity_participants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id UUID NOT NULL REFERENCES activities(id),
    user_id UUID NOT NULL REFERENCES users(id),
    role VARCHAR(50) NOT NULL DEFAULT 'PARTICIPANT',
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(activity_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_activity_participants_activity ON activity_participants(activity_id);
CREATE INDEX IF NOT EXISTS idx_activity_participants_user ON activity_participants(user_id);

CREATE TABLE IF NOT EXISTS chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id UUID NOT NULL REFERENCES activities(id),
    user_id UUID REFERENCES users(id),
    content TEXT NOT NULL,
    type VARCHAR(50) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_chat_messages_activity ON chat_messages(activity_id);
CREATE INDEX IF NOT EXISTS idx_chat_messages_created_at ON chat_messages(created_at);

CREATE TABLE IF NOT EXISTS vouches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vouter_id UUID NOT NULL REFERENCES users(id),
    vouchee_id UUID NOT NULL REFERENCES users(id),
    activity_id UUID NOT NULL REFERENCES activities(id),
    content TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(vouter_id, vouchee_id, activity_id)
);
CREATE INDEX IF NOT EXISTS idx_vouches_vouchee ON vouches(vouchee_id);

CREATE TABLE IF NOT EXISTS reports (
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
CREATE INDEX IF NOT EXISTS idx_reports_status ON reports(status);
CREATE INDEX IF NOT EXISTS idx_reports_target ON reports(target_type, target_id);

CREATE TABLE IF NOT EXISTS moderation_actions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    moderator_id UUID NOT NULL REFERENCES users(id),
    target_user_id UUID NOT NULL REFERENCES users(id),
    action_type VARCHAR(50) NOT NULL,
    reason TEXT NOT NULL,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_moderation_actions_user ON moderation_actions(target_user_id);
