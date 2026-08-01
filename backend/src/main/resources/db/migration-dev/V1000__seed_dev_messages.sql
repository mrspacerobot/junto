-- Seed Chat Messages for each activity

-- Anna's cycling trip: "Lake loop, coffee stop"
-- Participants: Anna, Marco, Clara, Tom
INSERT INTO chat_messages (id, activity_id, user_id, content, type, created_at) VALUES
-- Anna creates the trip and kicks things off
(gen_random_uuid(), (SELECT id FROM activities WHERE title = 'Lake loop, coffee stop'), (SELECT id FROM users WHERE display_name = 'Anna'), 'Hey everyone! Planning a lake loop ride this weekend. Coffee stop halfway as usual ☕🚴', 'USER', now() - interval '3 days' + interval '2 hours'),
-- System message when Marco joins
(gen_random_uuid(), (SELECT id FROM activities WHERE title = 'Lake loop, coffee stop'), NULL, 'Marco joined the activity.', 'SYSTEM', now() - interval '3 days' + interval '3 hours'),
-- Marco responds
(gen_random_uuid(), (SELECT id FROM activities WHERE title = 'Lake loop, coffee stop'), (SELECT id FROM users WHERE display_name = 'Marco'), 'Count me in! What''s the pace looking like?', 'USER', now() - interval '3 days' + interval '3 hours' + interval '5 minutes'),
-- Anna replies
(gen_random_uuid(), (SELECT id FROM activities WHERE title = 'Lake loop, coffee stop'), (SELECT id FROM users WHERE display_name = 'Anna'), 'Easy pace, around 25 km/h. No one gets dropped!', 'USER', now() - interval '3 days' + interval '3 hours' + interval '20 minutes'),
-- System message when Clara joins
(gen_random_uuid(), (SELECT id FROM activities WHERE title = 'Lake loop, coffee stop'), NULL, 'Clara joined the activity.', 'SYSTEM', now() - interval '2 days' + interval '1 hour'),
-- Clara chimes in
(gen_random_uuid(), (SELECT id FROM activities WHERE title = 'Lake loop, coffee stop'), (SELECT id FROM users WHERE display_name = 'Clara'), 'Perfect, been wanting to do this route! What time are we meeting?', 'USER', now() - interval '2 days' + interval '1 hour' + interval '10 minutes'),
-- Anna with details
(gen_random_uuid(), (SELECT id FROM activities WHERE title = 'Lake loop, coffee stop'), (SELECT id FROM users WHERE display_name = 'Anna'), 'Let''s meet at 9am at Bahnhof Tiefenbrunnen. I''ll be on my blue Canyon.', 'USER', now() - interval '2 days' + interval '1 hour' + interval '30 minutes'),
-- System message when Tom joins
(gen_random_uuid(), (SELECT id FROM activities WHERE title = 'Lake loop, coffee stop'), NULL, 'Tom joined the activity.', 'SYSTEM', now() - interval '1 day'),
-- Tom
(gen_random_uuid(), (SELECT id FROM activities WHERE title = 'Lake loop, coffee stop'), (SELECT id FROM users WHERE display_name = 'Tom'), 'Great route! Do we need to bring our own lock for the coffee stop?', 'USER', now() - interval '1 day' + interval '15 minutes'),
-- Marco
(gen_random_uuid(), (SELECT id FROM activities WHERE title = 'Lake loop, coffee stop'), (SELECT id FROM users WHERE display_name = 'Marco'), 'I always bring one — recommend it. The café has a rack out front.', 'USER', now() - interval '1 day' + interval '30 minutes'),
-- Anna wraps up
(gen_random_uuid(), (SELECT id FROM activities WHERE title = 'Lake loop, coffee stop'), (SELECT id FROM users WHERE display_name = 'Anna'), 'See you all Saturday! Weather looks great 🌤️', 'USER', now() - interval '6 hours');

-- Lukas's climbing trip: "Arête du Lion via West face"
-- Participants: Lukas, Marco
INSERT INTO chat_messages (id, activity_id, user_id, content, type, created_at) VALUES
-- Lukas organizes
(gen_random_uuid(), (SELECT id FROM activities WHERE title = 'Arête du Lion via West face'), (SELECT id FROM users WHERE display_name = 'Lukas'), 'Looking for a solid partner for Arête du Lion next week. 6b on the West face, full day commitment.', 'USER', now() - interval '5 days'),
-- System: Marco joins
(gen_random_uuid(), (SELECT id FROM activities WHERE title = 'Arête du Lion via West face'), NULL, 'Marco joined the activity.', 'SYSTEM', now() - interval '5 days' + interval '6 hours'),
-- Marco
(gen_random_uuid(), (SELECT id FROM activities WHERE title = 'Arête du Lion via West face'), (SELECT id FROM users WHERE display_name = 'Marco'), 'I''m in. I climbed the South ridge last year — how''s the West face compare?', 'USER', now() - interval '5 days' + interval '6 hours' + interval '20 minutes'),
-- Lukas
(gen_random_uuid(), (SELECT id FROM activities WHERE title = 'Arête du Lion via West face'), (SELECT id FROM users WHERE display_name = 'Lukas'), 'More exposed, slightly harder crux. Bring your 60m rope and a set of cams. I have the nuts and draws.', 'USER', now() - interval '5 days' + interval '6 hours' + interval '45 minutes'),
-- Marco
(gen_random_uuid(), (SELECT id FROM activities WHERE title = 'Arête du Lion via West face'), (SELECT id FROM users WHERE display_name = 'Marco'), 'Got it. Should we plan to bail if conditions turn?', 'USER', now() - interval '4 days'),
-- Lukas
(gen_random_uuid(), (SELECT id FROM activities WHERE title = 'Arête du Lion via West face'), (SELECT id FROM users WHERE display_name = 'Lukas'), 'Absolutely. Safety first. I''ll check the forecast the night before and message here.', 'USER', now() - interval '4 days' + interval '1 hour'),
-- Lukas with an update
(gen_random_uuid(), (SELECT id FROM activities WHERE title = 'Arête du Lion via West face'), (SELECT id FROM users WHERE display_name = 'Lukas'), 'Quick update — looks like a weather window on Thursday. Early start, leaving parking at 6am. Still good for you?', 'USER', now() - interval '1 day'),
-- Marco confirms
(gen_random_uuid(), (SELECT id FROM activities WHERE title = 'Arête du Lion via West face'), (SELECT id FROM users WHERE display_name = 'Marco'), 'Thursday works. I''ll bring the headlamps just in case.', 'USER', now() - interval '1 day' + interval '2 hours');

-- Sofia's skiing trip: "Off-piste morning in Verbier"
-- Participants: Sofia, Clara
INSERT INTO chat_messages (id, activity_id, user_id, content, type, created_at) VALUES
-- Sofia plans the trip
(gen_random_uuid(), (SELECT id FROM activities WHERE title = 'Off-piste morning in Verbier'), (SELECT id FROM users WHERE display_name = 'Sofia'), 'Heading to Verbier next weekend for some off-piste. Looking for an advanced ski buddy — no piste skiing 😄', 'USER', now() - interval '4 days'),
-- System: Clara joins
(gen_random_uuid(), (SELECT id FROM activities WHERE title = 'Off-piste morning in Verbier'), NULL, 'Clara joined the activity.', 'SYSTEM', now() - interval '4 days' + interval '2 hours'),
-- Clara
(gen_random_uuid(), (SELECT id FROM activities WHERE title = 'Off-piste morning in Verbier'), (SELECT id FROM users WHERE display_name = 'Clara'), 'You had me at off-piste! Which areas are you thinking?', 'USER', now() - interval '4 days' + interval '2 hours' + interval '10 minutes'),
-- Sofia
(gen_random_uuid(), (SELECT id FROM activities WHERE title = 'Off-piste morning in Verbier'), (SELECT id FROM users WHERE display_name = 'Sofia'), 'Was thinking Mont Gelé first, then depending on conditions maybe the Staircase. Both 4/5 difficulty.', 'USER', now() - interval '4 days' + interval '2 hours' + interval '40 minutes'),
-- Clara
(gen_random_uuid(), (SELECT id FROM activities WHERE title = 'Off-piste morning in Verbier'), (SELECT id FROM users WHERE display_name = 'Clara'), 'Mont Gelé is stunning. I have my transceiver, probe, and shovel — you?', 'USER', now() - interval '4 days' + interval '3 hours'),
-- Sofia
(gen_random_uuid(), (SELECT id FROM activities WHERE title = 'Off-piste morning in Verbier'), (SELECT id FROM users WHERE display_name = 'Sofia'), 'Full AV kit, yes. Let''s do a quick transceiver check at the top before we drop.', 'USER', now() - interval '4 days' + interval '3 hours' + interval '15 minutes'),
-- Clara
(gen_random_uuid(), (SELECT id FROM activities WHERE title = 'Off-piste morning in Verbier'), (SELECT id FROM users WHERE display_name = 'Clara'), 'Smart. Meeting at the top of the cable car at 9?', 'USER', now() - interval '3 days'),
-- Sofia
(gen_random_uuid(), (SELECT id FROM activities WHERE title = 'Off-piste morning in Verbier'), (SELECT id FROM users WHERE display_name = 'Sofia'), 'Perfect. See you at the Mayen station at 8:30 so we catch the first car up.', 'USER', now() - interval '3 days' + interval '30 minutes');
