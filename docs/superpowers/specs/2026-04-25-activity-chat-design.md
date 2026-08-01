# Activity Chat Design Spec

**Date:** 2026-04-25
**Status:** Approved

## Goal

Add real-time activity-scoped chat. Participants in an activity can send and receive messages instantly. Messages persist in PostgreSQL. The system broadcasts system events (join/leave) automatically.

**Out of scope (future):** Group chat, direct messages, the top-level Messages screen, file attachments, message search. The data model should not block these, but we don't build them now.

## Architecture

STOMP over WebSocket for real-time delivery. REST for persistence and history. Spring Session (cookie-based) provides auth for both REST and WebSocket.

```
Browser                        Server
  |                              |
  |-- POST /api/activities/{id}/messages ---> persist to chat_messages
  |                              |
  |<-- STOMP broadcast to /topic/activity/{id} ---+
  |                              |                |
  |-- Subscribe /topic/activity/{id} --- check participant -->
  |                              |
  |-- GET /api/activities/{id}/messages ---> load history
```

## Data Model

**Existing table** (`chat_messages`):

| Column | Type | Notes |
|--------|------|-------|
| id | UUID PK | Used as tiebreaker in cursor pagination |
| activity_id | UUID FK → activities | Scopes messages to an activity |
| user_id | UUID FK → users, nullable | NULL for SYSTEM messages |
| content | TEXT | Message body, max 2000 characters |
| type | VARCHAR(50) | `USER` or `SYSTEM` |
| created_at | TIMESTAMPTZ | |
| updated_at | TIMESTAMPTZ | |

No schema changes needed. When groups arrive later, add a nullable `group_id` column.

**New JPA entity:** `ChatMessage` mapping this table. Fields: id, activityId, userId, content, type (enum: USER, SYSTEM), createdAt, updatedAt. Transient field `displayName` and `avatarUrl` populated in the response DTO (not stored — derived from user table on read).

## Backend

### New dependencies

Add to `build.gradle`:
```
implementation 'org.springframework.boot:spring-boot-starter-websocket'
```

### WebSocket config

`WebSocketConfig` implements `WebSocketMessageBrokerConfigurer`:
- Register STOMP endpoint at `/ws` with SockJS fallback
- Enable simple broker for `/topic` prefix
- Set application destination prefix to `/app`

### Channel security

`WebSocketAuthInterceptor` (extends `ChannelInterceptor`):
- On `SUBSCRIBE` to `/topic/activity/{id}`: extract activity ID from destination, check the session user is a participant (query `activity_participants`). Reject if not.
- Auth comes from the Spring Session cookie — the handshake interceptor extracts the session and sets the STOMP user principal.
- On participant removal: the chat event listener disconnects the removed user's STOMP subscription for that activity (via `SimpMessageSendingOperations` user destination), preventing them from receiving further messages.

### REST endpoints

`ChatController`:

**`GET /api/activities/{activityId}/messages`**
- Params: `limit` (default 50, max 100), `before` (cursor — message UUID, for loading older messages)
- Cursor pagination: queries `WHERE (created_at, id) < (cursor_created_at, cursor_id) ORDER BY created_at DESC, id DESC LIMIT n`, then reverses results to return ASC order. Using `(created_at, id)` as a composite cursor avoids skipping messages with identical timestamps.
- Returns: list of `ChatMessageResponse` DTOs ordered by `created_at ASC, id ASC`
- Auth: user must be a participant of the activity
- Response DTO: id, content, type, createdAt, userId, displayName, avatarUrl

**`POST /api/activities/{activityId}/messages`**
- Body: `{ "content": "..." }`
- Validation: content must be 1–2000 characters (trimmed), reject empty or over-limit
- Persists message to `chat_messages`
- Publishes `ChatMessageResponse` to `/topic/activity/{activityId}` via `SimpMessagingTemplate`
- Auth: user must be a participant of the activity
- Returns: 200 with the created `ChatMessageResponse`

### System messages

`ParticipantService` emits system messages on join/leave events:
- When a user joins (status → APPROVED): insert SYSTEM message "{displayName} joined the activity", broadcast
- When a user leaves: insert SYSTEM message "{displayName} left the activity", broadcast
- Done via Spring `ApplicationEvent` — participant service publishes event, chat service listens and inserts+broadcasts

### Service layer

`ChatService`:
- `getMessages(activityId, userId, limit, before)` — loads history, checks participation
- `sendMessage(activityId, userId, content)` — persists and broadcasts
- `sendSystemMessage(activityId, content)` — inserts SYSTEM type and broadcasts (used by event listener)

`ChatMessageRepository`:
- `findByActivityIdAndCursorBefore(activityId, cursorCreatedAt, cursorId, limit)` — keyset query: `WHERE activity_id = ? AND (created_at, id) < (?, ?) ORDER BY created_at DESC, id DESC LIMIT ?`, results reversed in service
- `findTopNByActivityIdOrderByCreatedAtDescIdDesc(activityId, limit)` — initial load (no cursor)

## Frontend

### Composable: `useChat(activityId)`

Manages the STOMP connection lifecycle for a given activity:

- On mount: fetch message history via REST, subscribe to STOMP topic
- `messages` ref: reactive array of chat messages, appended to on each STOMP push
- `send(text)`: POST to REST endpoint
- On unmount: unsubscribe from topic
- Connection: uses `@stomp/stompjs` npm package with SockJS

### Wiring into ActivityDetailView

Replace the current mock data with `useChat(activityId)`:
- Pass `messages` to `ChatPanel`
- Wire `ChatPanel`'s `@send` event to `useChat`'s `send()`

### Existing components (no changes needed)

- `ChatPanel.vue` — already accepts `messages` array and emits `send`
- `ChatMessage.vue` — already renders USER/SYSTEM types with avatars
- `ChatInput.vue` — already emits text on send

### Messages screen

The `/messages` route stays as a nav placeholder. No view built yet. When group chat or DMs arrive, this is where the thread list goes.

## Validation and rate limiting

- Message content: 1–2000 characters after trimming. Reject blank or over-limit with 400.
- Rate limit: max 10 messages per 10 seconds per user per activity. Enforced in `ChatService` with an in-memory sliding window (Caffeine cache keyed by userId+activityId). Returns 429 on exceed.
- `limit` param on GET: clamped to 1–100.

## Error handling

- REST: standard `NotFoundException` / `ForbiddenException` from existing exception handlers
- WebSocket: subscription rejection sends ERROR frame to client — the composable handles this gracefully
- Connection drops: SockJS auto-reconnects. On reconnect, the composable re-fetches recent messages to fill any gap
- Session expiry: `SessionDisconnectHandler` listens for `SessionDisconnectEvent` and cleans up STOMP subscriptions. The client-side composable detects the disconnect and redirects to login if re-auth fails.

## Testing

### Unit tests

- `ChatServiceTest`: message persistence, participation checks, system message generation
- Mock repositories, verify broadcasts via `SimpMessagingTemplate` mock

### Integration tests

- `ChatIntegrationTest`: REST endpoints with authenticated session
  - GET messages returns history
  - POST message persists and is visible in GET
  - Non-participant gets 403
  - System message appears after join
  - Cursor pagination returns correct page when messages share a timestamp
  - Message over 2000 chars returns 400
  - Rate limit returns 429 after burst

### Manual verification

1. Open activity detail as participant → chat loads history
2. Send message → appears instantly
3. Open same activity in second browser → receives message in real-time
4. Non-participant → chat panel shows locked state (existing behavior)
5. Join activity → system message appears in chat
