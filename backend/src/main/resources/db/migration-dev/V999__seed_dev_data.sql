-- Create a system user if it doesn't exist
INSERT INTO users (id, email, display_name, role, status, onboarded, invite_codes_remaining, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'system@junto.ch', 'Junto System', 'ADMIN', 'ACTIVE', true, 999, now(), now())
ON CONFLICT (id) DO NOTHING;

-- Add an invite code for dev purposes
INSERT INTO invite_codes (id, code, created_by, created_at)
VALUES (gen_random_uuid(), 'JUNTO-DEBUG', '00000000-0000-0000-0000-000000000001', now())
ON CONFLICT (code) DO NOTHING;

-- Seed Users (Mockup personas)
INSERT INTO users (id, email, display_name, avatar_url, role, status, onboarded, created_at, updated_at) VALUES
(gen_random_uuid(), 'anna@example.com', 'Anna', 'https://images.unsplash.com/photo-1438761681033-6461ffad8d80?auto=format&fit=crop&w=150&h=150&q=80', 'MEMBER', 'ACTIVE', true, now(), now()),
(gen_random_uuid(), 'lukas@example.com', 'Lukas', 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=150&h=150&q=80', 'MEMBER', 'ACTIVE', true, now(), now()),
(gen_random_uuid(), 'sofia@example.com', 'Sofia', 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=150&h=150&q=80', 'MEMBER', 'ACTIVE', true, now(), now()),
(gen_random_uuid(), 'marco@example.com', 'Marco', 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=150&h=150&q=80', 'MEMBER', 'ACTIVE', true, now(), now()),
(gen_random_uuid(), 'clara@example.com', 'Clara', 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=150&h=150&q=80', 'MEMBER', 'ACTIVE', true, now(), now()),
(gen_random_uuid(), 'tom@example.com', 'Tom', 'https://images.unsplash.com/photo-1552058544-f2b08422138a?auto=format&fit=crop&w=150&h=150&q=80', 'MEMBER', 'ACTIVE', true, now(), now());

-- Seed Activities (Mockup plans)
-- Anna: Cycling
INSERT INTO activities (id, creator_id, type_slug, title, city, location_lat, location_lng, starts_at, max_participants, custom_fields)
VALUES (
    gen_random_uuid(),
    (SELECT id FROM users WHERE display_name = 'Anna'),
    'cycling',
    'Lake loop, coffee stop',
    'Zurich, Switzerland',
    47.3769, 8.5417,
    now() + interval '1 day',
    12,
    '{"difficulty": "Intermediate", "distance": 58, "pace": "Easy"}'
);

-- Lukas: Climbing
INSERT INTO activities (id, creator_id, type_slug, title, city, location_lat, location_lng, starts_at, max_participants, custom_fields)
VALUES (
    gen_random_uuid(),
    (SELECT id FROM users WHERE display_name = 'Lukas'),
    'climbing',
    'Arête du Lion via West face',
    'Chamonix, France',
    45.9237, 6.8694,
    now() + interval '5 days',
    4,
    '{"level": "Advanced", "difficulty": "6b", "duration": 8}'
);

-- Sofia: Skiing
INSERT INTO activities (id, creator_id, type_slug, title, city, location_lat, location_lng, starts_at, max_participants, custom_fields)
VALUES (
    gen_random_uuid(),
    (SELECT id FROM users WHERE display_name = 'Sofia'),
    'skiing',
    'Off-piste morning in Verbier',
    'Verbier, Switzerland',
    46.0963, 7.2293,
    now() + interval '6 days',
    8,
    '{"level": "Advanced", "difficulty": "4/5", "terrain": "Off-piste"}'
);

-- Seed Participants
-- Anna's cycling trip
INSERT INTO activity_participants (id, activity_id, user_id, role, status) VALUES
(gen_random_uuid(), (SELECT id FROM activities WHERE title = 'Lake loop, coffee stop'), (SELECT id FROM users WHERE display_name = 'Anna'), 'CREATOR', 'APPROVED'),
(gen_random_uuid(), (SELECT id FROM activities WHERE title = 'Lake loop, coffee stop'), (SELECT id FROM users WHERE display_name = 'Marco'), 'PARTICIPANT', 'APPROVED'),
(gen_random_uuid(), (SELECT id FROM activities WHERE title = 'Lake loop, coffee stop'), (SELECT id FROM users WHERE display_name = 'Clara'), 'PARTICIPANT', 'APPROVED'),
(gen_random_uuid(), (SELECT id FROM activities WHERE title = 'Lake loop, coffee stop'), (SELECT id FROM users WHERE display_name = 'Tom'), 'PARTICIPANT', 'APPROVED');

-- Lukas's climbing trip
INSERT INTO activity_participants (id, activity_id, user_id, role, status) VALUES
(gen_random_uuid(), (SELECT id FROM activities WHERE title = 'Arête du Lion via West face'), (SELECT id FROM users WHERE display_name = 'Lukas'), 'CREATOR', 'APPROVED'),
(gen_random_uuid(), (SELECT id FROM activities WHERE title = 'Arête du Lion via West face'), (SELECT id FROM users WHERE display_name = 'Marco'), 'PARTICIPANT', 'APPROVED');

-- Sofia's skiing trip
INSERT INTO activity_participants (id, activity_id, user_id, role, status) VALUES
(gen_random_uuid(), (SELECT id FROM activities WHERE title = 'Off-piste morning in Verbier'), (SELECT id FROM users WHERE display_name = 'Sofia'), 'CREATOR', 'APPROVED'),
(gen_random_uuid(), (SELECT id FROM activities WHERE title = 'Off-piste morning in Verbier'), (SELECT id FROM users WHERE display_name = 'Clara'), 'PARTICIPANT', 'APPROVED');
