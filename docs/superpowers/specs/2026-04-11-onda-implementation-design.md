# Junto — Implementation Design

> Companion to the Junto Product Spec v5. This document covers HOW to build it — architecture, project structure, tooling, and build order.

---

## Decisions Summary

| Decision | Choice |
|----------|--------|
| Architecture style | Feature-based packages (backend), views + components (frontend) |
| Visual direction | Light mode, utility-first (clean, gets out of the way) |
| Frontend rendering | SPA — Vue 3 + Vue Router, client-rendered |
| Monorepo | Yes — `/backend` + `/frontend` at root |
| Dev environment | Separate dev servers, Vite proxy for API, Docker Postgres |
| Local DB | Docker Compose: Postgres 16 + PostGIS + Mailpit |
| Gradle DSL | Groovy (`build.gradle`) |
| Java | 21 (LTS, virtual threads) |
| Spring Boot | 4 |
| Hibernate | 6 (bundled) |
| API client generation | @hey-api/openapi-ts (fetch-based), output committed to repo |
| Frontend API | Full typed client generated from OpenAPI spec |
| API client library | @hey-api/client-fetch |
| JSONB mapping | `@JdbcTypeCode(SqlTypes.JSON)` + typed `CustomFields` wrapper |
| UUID generation | `gen_random_uuid()` in PostgreSQL |
| Timestamps | `TIMESTAMPTZ` in DB, `OffsetDateTime` in Java, ISO-8601 on wire |
| CSRF | `CookieCsrfTokenRepository` with `withHttpOnlyFalse()` |
| Frontend state | Pinia (auth store + filters store) |
| Frontend forms | Native HTML5 validation + Tailwind |
| Frontend dates | Native `Intl.DateTimeFormat` for display, ISO-8601 strings for API |
| Email in dev | Mailpit (localhost:1025, web UI at localhost:8025) |
| Email in prod | Resend API |
| System messages | Spring Application Events (decouples participant → chat) |
| DB connection pooling | HikariCP (5 max) + Neon PgBouncer in prod |
| Spring Session init | Flyway-managed (`spring.session.jdbc.initialize-schema=never`) |
| Testing backend | JUnit 5 + Mockito (unit), Testcontainers (integration) |
| Testing frontend | Vitest + Vue Test Utils |
| E2E testing | Playwright (P1) |
| Logging | SLF4J + Logback (Spring default), structured JSON in prod (P1) |
| CI | GitHub Actions — backend tests (Postgres service container), frontend lint + typecheck + build |
| CD backend | Railway native GitHub integration (auto-deploy on push to main) |
| CD frontend | Vercel native GitHub integration (auto-deploy on push to main) |
| Pre-push | Husky — runs backend tests before push |
| Branch rules | PR required, CI must pass, squash merge, conventional commits |

---

## Project Structure

```
junto/
├── backend/
│   ├── build.gradle
│   ├── settings.gradle
│   └── src/main/java/com/junto/
│       ├── JuntoApplication.java
│       ├── auth/
│       │   ├── AuthController.java
│       │   ├── AuthService.java
│       │   ├── AuthTokenRepository.java
│       │   ├── CurrentUser.java                    # Annotation
│       │   ├── CurrentUserArgumentResolver.java     # HandlerMethodArgumentResolver
│       │   ├── dto/
│       │   └── config/
│       │       └── SecurityConfig.java
│       ├── user/
│       │   ├── UserController.java
│       │   ├── UserService.java
│       │   ├── UserRepository.java
│       │   └── dto/
│       ├── invite/
│       │   ├── InviteController.java
│       │   ├── InviteService.java
│       │   ├── InviteCodeRepository.java
│       │   └── dto/
│       ├── activity/
│       │   ├── ActivityController.java
│       │   ├── ActivityService.java
│       │   ├── ActivityRepository.java
│       │   ├── dto/
│       │   │   ├── ActivityCreateRequest.java
│       │   │   ├── ActivityUpdateRequest.java
│       │   │   ├── ActivityDetailResponse.java
│       │   │   ├── ActivityListItemResponse.java
│       │   │   └── ActivityFilterParams.java
│       │   └── validation/
│       │       ├── ActivityFieldValidator.java      # Interface
│       │       ├── ClimbingFieldValidator.java
│       │       ├── CyclingFieldValidator.java
│       │       ├── SkiingFieldValidator.java
│       │       ├── RunningFieldValidator.java
│       │       └── YogaFieldValidator.java
│       ├── participant/
│       │   ├── ParticipantController.java
│       │   ├── ParticipantService.java
│       │   └── ParticipantRepository.java
│       ├── chat/
│       │   ├── ChatController.java
│       │   ├── ChatService.java
│       │   ├── ChatMessageRepository.java
│       │   └── dto/
│       ├── vouch/
│       │   ├── VouchController.java
│       │   ├── VouchService.java
│       │   ├── VouchRepository.java
│       │   └── dto/
│       ├── report/
│       │   ├── ReportController.java
│       │   ├── ReportService.java
│       │   ├── ReportRepository.java
│       │   └── dto/
│       ├── moderation/
│       │   ├── ModerationController.java
│       │   ├── ModerationService.java
│       │   ├── ModerationActionRepository.java
│       │   └── dto/
│       ├── activitytype/
│       │   ├── ActivityTypeController.java
│       │   ├── ActivityTypeService.java
│       │   └── ActivityTypeRepository.java
│       ├── email/
│       │   ├── EmailService.java
│       │   └── templates/
│       ├── job/
│       │   ├── ActivityAutoCompleteJob.java
│       │   ├── PendingRequestDeclineJob.java
│       │   └── AuthTokenCleanupJob.java
│       └── shared/
│           ├── event/
│           │   ├── ParticipantJoinedEvent.java
│           │   ├── ParticipantLeftEvent.java
│           │   ├── ParticipantApprovedEvent.java
│           │   ├── ParticipantDeclinedEvent.java
│           │   ├── ActivityEditedEvent.java
│           │   └── SignupClosedEvent.java
│           ├── entity/
│           │   ├── User.java
│           │   ├── AuthToken.java
│           │   ├── InviteCode.java
│           │   ├── Activity.java
│           │   ├── ActivityType.java
│           │   ├── ActivityParticipant.java
│           │   ├── Vouch.java
│           │   ├── ChatMessage.java
│           │   ├── Report.java
│           │   ├── ModerationAction.java
│           │   └── CustomFields.java
│           ├── enums/
│           │   ├── ActivityStatus.java
│           │   ├── JoinPolicy.java
│           │   ├── ParticipantRole.java
│           │   ├── ParticipantStatus.java
│           │   ├── UserRole.java
│           │   ├── UserStatus.java
│           │   ├── MessageType.java
│           │   ├── ReportStatus.java
│           │   ├── ReportTargetType.java
│           │   └── ModerationActionType.java
│           ├── pagination/
│           │   ├── CursorPage.java
│           │   └── CursorPageable.java
│           └── exception/
│               ├── GlobalExceptionHandler.java
│               ├── ApiException.java
│               ├── NotFoundException.java
│               ├── ForbiddenException.java
│               ├── ConflictException.java
│               └── SignupClosedException.java
│   └── src/main/resources/
│       ├── application.yml
│       ├── application-dev.yml
│       ├── application-prod.yml
│       └── db/migration/
│           ├── V001__enable_postgis.sql
│           ├── V002__create_users.sql
│           ├── V003__create_auth_tokens.sql
│           ├── V004__create_invite_codes.sql
│           ├── V005__create_activity_types.sql
│           ├── V006__seed_activity_types.sql
│           ├── V007__create_activities.sql
│           ├── V008__create_activity_participants.sql
│           ├── V009__create_chat_messages.sql
│           ├── V010__create_vouches.sql
│           ├── V011__create_reports.sql
│           ├── V012__create_moderation_actions.sql
│           └── V013__create_spring_session_tables.sql
├── frontend/
│   ├── package.json
│   ├── vite.config.ts
│   ├── tailwind.config.js
│   ├── tsconfig.json
│   ├── index.html
│   └── src/
│       ├── main.ts
│       ├── App.vue
│       ├── router/
│       │   └── index.ts
│       ├── api/
│       │   ├── generated/           # Auto-generated from OpenAPI spec
│       │   │   ├── models/          # TypeScript interfaces
│       │   │   └── api/             # Typed client classes
│       │   ├── client.ts            # Axios instance, CSRF, interceptors
│       │   └── index.ts             # Re-exports configured clients
│       ├── stores/
│       │   ├── auth.ts
│       │   └── filters.ts
│       ├── composables/
│       │   ├── usePolling.ts
│       │   ├── useActivityTypes.ts
│       │   └── usePagination.ts
│       ├── views/
│       │   ├── auth/
│       │   │   ├── LoginView.vue
│       │   │   └── OnboardView.vue
│       │   ├── activities/
│       │   │   ├── ActivityFeedView.vue
│       │   │   ├── ActivityDetailView.vue
│       │   │   └── ActivityCreateView.vue
│       │   ├── profile/
│       │   │   ├── ProfileView.vue
│       │   │   └── PublicProfileView.vue
│       │   └── chat/
│       │       └── (embedded in ActivityDetailView)
│       ├── components/
│       │   ├── layout/
│       │   │   ├── AppShell.vue
│       │   │   ├── NavBar.vue
│       │   │   └── PageContainer.vue
│       │   ├── activity/
│       │   │   ├── ActivityCard.vue
│       │   │   ├── ActivityForm.vue
│       │   │   ├── ActivityFilters.vue
│       │   │   └── SignupDeadline.vue
│       │   ├── chat/
│       │   │   ├── ChatPanel.vue
│       │   │   ├── ChatMessage.vue
│       │   │   └── ChatInput.vue
│       │   ├── auth/
│       │   │   ├── LoginForm.vue
│       │   │   └── OtpInput.vue
│       │   ├── participant/
│       │   │   ├── ParticipantList.vue
│       │   │   └── JoinButton.vue
│       │   └── ui/
│       │       ├── Button.vue
│       │       ├── Input.vue
│       │       ├── DatePicker.vue
│       │       ├── CursorPagination.vue
│       │       └── Modal.vue
│       └── types/                   # App-level types (not generated)
│           └── index.ts
├── docker-compose.yml
└── .gitignore
```

---

## Backend Architecture

### Cross-service rules

- **Services call other services** (for reads/validation), not other services' repos
- **Write ownership:** only one service writes to a given table
  - `ActivityService` owns writes to `activities`
  - `ParticipantService` owns writes to `activity_participants`
  - `ChatService` owns writes to `chat_messages`
  - Each service owns its own repository
- **System messages via Spring Application Events** — ParticipantService fires events (join, leave, approve, decline), ChatService listens. No direct dependency from ParticipantService → ChatService. This avoids a circular dependency.

Example:
```java
// ParticipantService — after join
applicationEventPublisher.publishEvent(
    new ParticipantJoinedEvent(activityId, userId, displayName)
);

// ChatService — listens
@EventListener
public void onParticipantJoined(ParticipantJoinedEvent event) {
    postSystemMessage(event.activityId(),
        "👋 " + event.displayName() + " joined");
}
```

### Custom field validation (per activity type)

```java
public interface ActivityFieldValidator {
    String getActivityTypeSlug();
    void validate(Map<String, Object> customFields);
    void normalize(Activity activity);  // populate subtype, difficulty_label
}
```

All validators injected into a `Map<String, ActivityFieldValidator>` by Spring. `ActivityService` looks up by slug on create/update.

### JSONB mapping

```java
// CustomFields.java — typed wrapper
public class CustomFields extends HashMap<String, Object> {
    public String getString(String key) { ... }
    public Integer getInt(String key) { ... }
    public Boolean getBoolean(String key) { ... }
}

// Activity.java entity
@Column(columnDefinition = "jsonb")
@JdbcTypeCode(SqlTypes.JSON)
private CustomFields customFields;
```

### Current user access

```java
// Custom annotation
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {}

// Resolver reads from SecurityContext
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver { ... }

// Usage in controllers
@GetMapping("/api/auth/me")
public UserProfileResponse me(@CurrentUser User user) { ... }
```

### Error response format

```json
{
  "error": {
    "code": "SIGNUP_CLOSED",
    "message": "Signup deadline has passed for this activity",
    "status": 400
  }
}
```

Error codes: `INVALID_REQUEST`, `SIGNUP_CLOSED`, `ACTIVITY_FULL`, `ALREADY_JOINED`, `NOT_FOUND`, `FORBIDDEN`, `UNAUTHORIZED`, `RATE_LIMITED`, `AUTH_CODE_EXPIRED`, `AUTH_CODE_INVALID`, `INVITE_CODE_INVALID`, `INVITE_CODE_REQUIRED`, `ACTIVITY_NOT_ACTIVE`, `VOUCH_NOT_ELIGIBLE`, `ACCOUNT_SUSPENDED`

### Scheduled jobs

| Job | Frequency | Service called |
|-----|-----------|---------------|
| ActivityAutoCompleteJob | Hourly | ActivityService |
| PendingRequestDeclineJob | 15 min | ParticipantService → ChatService |
| AuthTokenCleanupJob | Hourly | AuthService |

### Application profiles

- **dev:** Postgres localhost:5432, Mailpit localhost:1025, CORS localhost:5173, Springdoc enabled
- **prod:** Neon DB URL, Resend API key, CORS production origin, Springdoc disabled

```yaml
# application.yml (shared)
spring:
  session:
    jdbc:
      initialize-schema: never    # Flyway manages session tables
  jpa:
    hibernate:
      ddl-auto: validate          # Flyway manages schema
    open-in-view: false
logging:
  level:
    ch.junto: DEBUG
    org.springframework.security: WARN
    org.hibernate.SQL: DEBUG       # Dev only
  pattern:
    console: "%d{HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"

# application-prod.yml
spring:
  datasource:
    url: jdbc:postgresql://${NEON_HOST}/${NEON_DB}?sslmode=require
    hikari:
      maximum-pool-size: 5         # Conservative for Neon free tier
      minimum-idle: 2
      connection-timeout: 20000    # Neon cold start can be slow
      idle-timeout: 300000
logging:
  level:
    ch.junto: INFO
    org.hibernate.SQL: WARN
```

### Prod environment variables (Railway)

```
SPRING_PROFILES_ACTIVE=prod
DATABASE_URL=jdbc:postgresql://...   # Neon pooled connection string (PgBouncer)
RESEND_API_KEY=re_...
ALLOWED_ORIGIN=https://junto.app
```

### Prod startup validation

```java
// shared/config/EnvironmentValidator.java
@Component
@Profile("prod")
public class EnvironmentValidator {
    @Value("${resend.api-key:}")
    private String resendApiKey;

    @Value("${app.allowed-origin:}")
    private String allowedOrigin;

    @PostConstruct
    public void validate() {
        List<String> missing = new ArrayList<>();
        if (resendApiKey.isBlank()) missing.add("RESEND_API_KEY");
        if (allowedOrigin.isBlank()) missing.add("ALLOWED_ORIGIN");
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                "Missing required env vars: " + String.join(", ", missing));
        }
    }
}
```

Fails fast on deploy if config is wrong.

---

## Frontend Architecture

### API client (generated)

Backend exposes OpenAPI spec via Springdoc at `/v3/api-docs`.

Frontend runs `@hey-api/openapi-ts` to generate typed API clients:

```
src/api/
├── generated/           # Auto-generated, do not edit — committed to repo
├── client.ts            # Client config (base URL, CSRF, error handling)
└── index.ts             # Re-exports configured clients
```

- `npm run generate-api` — regenerates from running backend spec (backend must be running)
- Generated output is **committed to repo** (CI builds need it without a running backend)
- `client.ts` configures CSRF (`XSRF-TOKEN` cookie → `X-XSRF-TOKEN` header), base URL, 401 redirect

```json
// package.json scripts
{
  "generate-api": "openapi-ts"
}
```

### Frontend error handling

Middleware in `client.ts` handles errors centrally:

```ts
client.interceptors.response.use((response) => {
  if (response.status === 401) {
    const auth = useAuthStore()
    auth.clearSession()
    router.push('/auth/login')
  }
  return response
})
```

Components handle specific error codes:

```vue
<script setup>
async function handleJoin() {
  try {
    await activityApi.join(activityId)
  } catch (err) {
    if (err.code === 'SIGNUP_CLOSED') showToast('Signup has closed')
    else if (err.code === 'ACTIVITY_FULL') showToast('Activity is full')
    else showToast(err.message)
  }
}
</script>
```

### Routing

```
/                       → Redirect to /activities or /auth/login
/auth/login             → Email + invite code + OTP
/auth/onboard           → Set display_name (first-time)
/activities             → Activity feed with filters
/activities/:id         → Activity detail + chat + participants
/activities/new         → Create activity
/profile                → My profile + settings
/profile/:id            → Public profile + vouches
/invites                → My invite codes
```

Route guards (`beforeEach`):
- Unauthenticated → redirect to `/auth/login`
- Authenticated but not onboarded → redirect to `/auth/onboard`
- Authenticated on `/auth/login` → redirect to `/activities`
- Auth store initializes on first navigation via `GET /api/auth/me`

### State (Pinia)

- `auth` store: currentUser, isAuthenticated, onboarded, login/logout actions
- `filters` store: active feed filters (city, type, date, joinable)

### Composables

- `usePolling` — generic polling with visibility-based intervals (5s/15s/60s)
- `useActivityTypes` — cached activity type lookup
- `usePagination` — cursor-based pagination helper

### Design system (Tailwind)

- Light mode, one primary accent color (TBD during visual design)
- System font stack or Inter
- `rounded-lg`, subtle borders, minimal shadows
- Activity type color accents for visual scanning

### Vite dev proxy

```ts
server: {
  proxy: {
    '/api': 'http://localhost:8080',
    '/v3': 'http://localhost:8080',   // OpenAPI spec
  }
}
```

No CORS issues in dev.

---

## Docker Compose

```yaml
services:
  postgres:
    image: postgis/postgis:16-3.4
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: onda
      POSTGRES_USER: onda
      POSTGRES_PASSWORD: onda
    volumes:
      - pgdata:/var/lib/postgresql/data

  mailpit:
    image: axllent/mailpit
    ports:
      - "1025:1025"   # SMTP
      - "8025:8025"   # Web UI
    environment:
      MP_SMTP_AUTH_ACCEPT_ANY: 1
      MP_SMTP_AUTH_ALLOW_INSECURE: 1

volumes:
  pgdata:
```

---

## Service Dependency Map

```
AuthController → AuthService → AuthTokenRepository
                             → UserService (create user on first login)
                             → InviteService (validate + consume code)

UserController → UserService → UserRepository

InviteController → InviteService → InviteCodeRepository

ActivityController → ActivityService → ActivityRepository
                                       → ActivityTypeService
                                       → ActivityFieldValidator (per-type)
                                       → ApplicationEventPublisher (ActivityEditedEvent)

ParticipantController → ParticipantService → ParticipantRepository
                                             → ActivityService (read: status, deadline, capacity)
                                             → ApplicationEventPublisher (join/leave/approve/decline events)
                                             // NOTE: does NOT call ChatService directly

ChatController → ChatService → ChatMessageRepository
                             → ParticipantService (check approved access)
                             → @EventListener (listens for system message events)

VouchController → VouchService → VouchRepository
                               → ActivityService (check completed)
                               → ParticipantService (check both participated)

ReportController → ReportService → ReportRepository

ModerationController → ModerationService → ModerationActionRepository
                                           → ReportService (update status)
                                           → UserService (suspend)
                                           → ActivityService (remove content)

Jobs:
  ActivityAutoCompleteJob → ActivityService
  PendingRequestDeclineJob → ParticipantService (fires events, ChatService listens)
  AuthTokenCleanupJob → AuthService
```

---

## P0 Build Slices

### Slice 1 — Project Scaffold + Auth

**Backend:**
- Gradle + Spring Boot 4 project init (Groovy DSL)
- Docker Compose (Postgres + PostGIS + Mailpit)
- Flyway migrations: `users`, `auth_tokens`, `invite_codes`, Spring Session tables
- `shared/` package: entities, enums, exceptions, pagination, config
- `auth/` package: request-code, verify (OTP + magic link), logout, me
- `user/` package: get profile, update profile, onboard
- `invite/` package: validate on signup, generate + list codes
- `email/` package: Mailpit in dev, Resend in prod
- Springdoc OpenAPI setup
- `application-dev.yml` + `application-prod.yml`

**Frontend:**
- Vite + Vue 3 + Tailwind + TypeScript + Vue Router + Pinia
- OpenAPI client generation pipeline
- `/auth/login` page (email + invite code, OTP entry)
- `/auth/onboard` page (set display_name)
- `AppShell.vue` with NavBar
- Auth store (Pinia)
- API client config (CSRF, session handling)

**Deliverable:** User can sign up with invite code, verify via OTP, set display name, log out. Generate and view invite codes.

---

### Slice 2 — Activities (Create + Browse + Detail)

**Backend:**
- Flyway migrations: `activity_types` (seed 5 types), `activities` (with PostGIS trigger + indexes)
- `activity/` package: create, list (filters: city, type, subtype, difficulty, date, joinable), detail
- Basic update (title, description, custom_fields — always-editable fields only)
- Cancel and complete endpoints (lifecycle transitions)
- `activitytype/` package: list active types
- All 5 activity type validators
- Activity detail includes computed `is_joinable`, `spots`, `effective_deadline`

**Frontend:**
- Regenerate API client from updated spec
- `/activities` — feed page with filter bar
- `/activities/:id` — detail page (all fields, spots, status)
- `/activities/new` — create wizard (pick type → type-specific fields → common fields → review)
- `ActivityCard.vue`, `ActivityFilters.vue`, `ActivityForm.vue`, `SignupDeadline.vue`
- Activity type composable + filters store
- Cursor pagination

**Deliverable:** User can create any of 5 activity types, browse with filters, view details. No join yet.

---

### Slice 3 — Join Flow (Full)

**Backend:**
- Flyway migration: `activity_participants`
- `participant/` package: join (OPEN + APPROVAL_REQUIRED), leave, approve, decline, list participants
- `signup_deadline` logic: effective deadline computation, validation, auto-adjust on starts_at change
- `SELECT FOR UPDATE` for atomic capacity check
- System messages for join/leave/approve/decline
- Full editing rules: edit-with-notification (time/location/deadline), never-editable-after-join (type, max_participants)

**Frontend:**
- `JoinButton.vue` — contextual states (Join / Request / Pending / Approved / Full / Closed)
- `ParticipantList.vue` — approved + pending (creator view)
- Approve/decline UI in participant list
- Signup deadline picker in create/edit form
- Activity detail shows spots, deadline status, participant list

**Deliverable:** Full join flow. OPEN: click → in. APPROVAL_REQUIRED: request → approve/decline. Deadline and capacity enforced.

---

### Slice 4 — Chat + Vouching + Scheduled Jobs

**Backend:**
- Flyway migrations: `chat_messages`, `vouches`
- `chat/` package: get messages (cursor-paginated), send, system messages
- `vouch/` package: create, list, delete
- `job/` package: auto-complete (hourly), auto-decline pending (15 min), clean tokens (hourly)
- Chat access rules: approved only, read-only after completion
- Vouch rules: completed activity, both participated

**Frontend:**
- `ChatPanel.vue`, `ChatMessage.vue`, `ChatInput.vue` in activity detail
- Polling via `usePolling` composable
- Read-only indicator when activity completed/cancelled
- Vouch button on public profile (select shared completed activity)
- Vouch list on profile page

**Deliverable:** Group chat per activity. Scheduled jobs for auto-cleanup. Vouching on completed activities.

-Yes — here’s a focused audit of the auth flow you shared.

## High-level summary

This is a solid email-code / magic-link auth implementation with a few good practices already in place:

- invite code required for new users
- suspended users blocked at request time
- one-time token usage via `usedAt`
- expiry enforced
- invite code persisted through auth flow
- session authentication via Spring Security context
- CSRF enabled

But there are still some important issues and improvement areas, especially around:

- race conditions / token reuse
- account enumeration
- brute force protection
- multiple valid codes at once
- suspended user checks during verification
- GET magic-link CSRF/session concerns
- returning full `User` entity from `/me`

---

# Findings

## 1. Race condition on token/code verification
### Severity: High

### Problem
In `verifyAuthToken`:

```java
authToken.setUsedAt(OffsetDateTime.now());
authTokenRepository.save(authToken);
```

The token is loaded first with `usedAt IS NULL`, then marked used. Two concurrent requests could both pass the lookup before either saves `usedAt`.

This can allow:
- double login from same code/token
- potentially double account creation
- potentially double invite consumption

### Why this matters
For one-time auth tokens, atomic consumption is important.

### Recommendation
Use an atomic update at the DB layer, e.g. a repository method like:

```java
@Modifying
@Query("""
UPDATE AuthToken t
SET t.usedAt = :now
WHERE t.id = :id
  AND t.usedAt IS NULL
  AND t.expiresAt >= :now
""")
int consumeToken(@Param("id") UUID id, @Param("now") OffsetDateTime now);
```

Then:
1. load candidate token
2. atomically consume it
3. only proceed if update count is `1`

Even better: combine selection and consumption logic as tightly as possible.

---

## 2. Existing suspended users can still log in with previously issued tokens
### Severity: High

### Problem
You check suspension in `requestCode`, but not in `verifyAuthToken`.

```java
if (existingUser.isPresent()) {
    return existingUser.get();
}
```

If a user:
1. requests code while active
2. gets suspended before verification
3. verifies old code/token

They still get authenticated.

### Recommendation
Re-check user status during verification:

```java
if (existingUser.isPresent()) {
    if (existingUser.get().getStatus() == UserStatus.SUSPENDED) {
        throw new ApiException("ACCOUNT_SUSPENDED", "This account has been suspended", 403);
    }
    return existingUser.get();
}
```

Also consider invalidating all outstanding tokens for suspended users.

---

## 3. Multiple active codes/tokens can exist for the same email
### Severity: Medium

### Problem
Each `requestCode` creates a new `AuthToken` without invalidating previous unused ones.

Result:
- multiple valid codes at once
- multiple magic links at once
- increased attack surface
- user confusion (“which code is correct?”)

### Recommendation
Before saving a new token, invalidate or delete prior unused tokens for that email:

```java
@Modifying
@Query("DELETE FROM AuthToken t WHERE t.email = :email AND t.usedAt IS NULL")
void deleteUnusedByEmail(@Param("email") String email);
```

Or mark them superseded.

Best practice: only one active auth challenge per email.

---

## 4. No brute-force/rate limiting
### Severity: High

### Problem
There is no visible protection against:
- repeated `request-code` calls to spam email delivery
- repeated `verify` attempts to guess the 6-digit code
- repeated `verify-token` requests

A 6-digit code has only 1,000,000 possibilities; without rate limiting, it is brute-forceable in principle.

### Recommendation
Add rate limits for:
- `requestCode` per email and per IP
- `verifyCode` per email, per IP, and possibly per auth token
- global throttles

Also consider:
- max attempts per token/code
- invalidating token after N failed attempts
- cooldown windows

Example fields on `AuthToken`:
- `attemptCount`
- `lastAttemptAt`

---

## 5. Account enumeration via invite-code behavior and response differences
### Severity: Medium

### Problem
`requestCode` behaves differently for existing vs new users:

- new user without invite code → `INVITE_CODE_REQUIRED`
- existing suspended user → `ACCOUNT_SUSPENDED`
- existing active user → success

This leaks whether an email exists and whether it is suspended.

### Why this matters
Attackers can probe user existence/status using email addresses.

### Recommendation
For public auth endpoints, consider uniform outward responses such as:
- always return 200 with a generic message like “If eligible, a login code has been sent”

Internally:
- enforce invite/suspension rules
- but avoid disclosing account state

If your product intentionally allows this distinction, document it as an explicit tradeoff.

---

## 6. `verifyCode(email, code)` ties auth to email+code, which is okay, but code-only replay window remains broad
### Severity: Medium

### Problem
You search by:

```java
findByEmailAndCodeAndUsedAtIsNull(email, code)
```

If multiple tokens exist for same email, the same code could theoretically collide across entries, or an older code remains valid until expiry.

Since the code space is only 6 digits, ambiguity and replay complexity increase when multiple active tokens are allowed.

### Recommendation
Use one of these patterns:
- only one active token per email
- or require both `token + code` for code verification
- or store a hashed verifier challenge with strict uniqueness

At minimum, enforce one active challenge per email.

---

## 7. Magic-link verification is a GET that creates a session
### Severity: Medium

### Problem
`/api/auth/verify-token` is a `GET` and authenticates the user by creating a session.

GET requests are generally expected to be side-effect free. Here it:
- mutates auth state
- creates session
- consumes token

Potential issues:
- link prefetchers / email scanners may trigger the GET automatically
- security tooling may “burn” the magic link before user clicks it
- browser prefetch/crawlers can cause accidental login/token consumption

### Recommendation
Prefer one of these:
1. GET only displays a confirmation page, then POST completes login
2. GET redirects to frontend with a short-lived one-time handoff token, frontend POSTs to finalize
3. Use deep links carefully with anti-prefetch protections

This is a very common real-world problem with magic links.

---

## 8. Session fixation protection is not explicit
### Severity: Medium

### Problem
You manually create authentication and save security context:

```java
SecurityContextHolder.setContext(context);
securityContextRepository.saveContext(context, request, response);
```

This bypasses some of Spring Security’s normal authentication flow behavior, including session fixation handling depending on setup.

### Recommendation
Ensure session fixation protection is applied. Options:
- explicitly configure session management
- invalidate old session and create a new one on login
- authenticate through Spring Security’s standard mechanisms where possible

For example, confirm behavior of session migration in your current stack/version.

---

## 9. `/me` returns full `User` entity
### Severity: Medium

### Problem
This endpoint:

```java
@GetMapping("/me")
public User me(@CurrentUser User user) {
    return user;
}
```

Returning the entity directly can expose:
- internal fields
- lazy-loaded relationships
- sensitive flags/statuses
- invite metadata
- implementation details

### Recommendation
Return a DTO instead, e.g.:

```java
public record MeResponse(UUID id, String email, String name, UserStatus status) {}
```

Expose only intended fields.

---

## 10. Invite code is validated at request time but not revalidated at verification time
### Severity: Medium

### Problem
For new users:
- invite code is validated at `requestCode`
- stored on token
- later consumed at verification

If invite state changes in between (revoked, expired, already used by another path), behavior depends entirely on `consumeCode`.

### Recommendation
This is acceptable if `consumeCode` is authoritative and transactional. Make sure:
- `consumeCode` re-checks validity atomically
- invite can only be consumed once
- failures there roll back user creation if appropriate

Right now, if `createFromEmail` succeeds and `consumeCode` fails, you may end up with a user created without a valid invite, depending on transaction semantics in those services.

Because `verifyCode` and `verifyToken` are `@Transactional`, this may already roll back if `consumeCode` throws a runtime exception. Still worth verifying.

---

## 11. Auth code stored in plaintext
### Severity: Medium

### Problem
`AuthToken.setCode(code)` suggests the raw 6-digit code is persisted.

If DB contents leak, valid unused codes are exposed immediately.

### Recommendation
Store a hash of the code instead of plaintext, similar to password reset tokens:
- generate code
- hash with a server-side algorithm
- compare hash on verification

For a 6-digit code, hashing alone doesn’t fully prevent offline brute force because entropy is low, but it still protects against instant plaintext disclosure and is better than raw storage.

You can also:
- salt per token
- combine with token identifier
- enforce short expiry + attempt limits

---

## 12. No normalization of email
### Severity: Low/Medium

### Problem
Emails are used directly:

```java
userService.findByEmail(email)
authToken.setEmail(email)
emailService.sendAuthCode(email, ...)
```

Without normalization, these may behave inconsistently:
- `User@Example.com`
- ` user@example.com `
- case sensitivity mismatches

### Recommendation
Normalize before all operations:
- trim whitespace
- lowercase domain at minimum
- often lowercase full email unless your business explicitly preserves local-part case distinctions

Use the same normalization in user lookup, token creation, and user creation.

---

## 13. Expired tokens are only cleaned asynchronously/manual call
### Severity: Low

### Problem
Expired tokens remain in the table until `cleanExpiredTokens()` is called.

Not a direct auth bypass, because expiry is checked during verification, but:
- table growth
- possible operational issues

### Recommendation
Run `cleanExpiredTokens()` on a schedule or use DB TTL/cleanup job.

---

## 14. Logging email addresses
### Severity: Low

### Problem
You log:

```java
log.info("Auth code requested for {}", email);
log.info("New user created: {}", email);
```

Email is PII.

### Recommendation
Depending on your privacy/compliance requirements:
- reduce to debug level
- partially mask email
- use structured audit logs with restricted access

---

## 15. CSRF setup is generally good, but auth endpoints need UX alignment
### Severity: Low

### Observation
You enabled CSRF with cookie token repo, which is good for session-based auth.

However, `/api/auth/request-code` and `/api/auth/verify` are state-changing endpoints. If called from a browser SPA, ensure:
- frontend first fetches CSRF token
- token header is sent correctly
- unauthenticated users can still obtain CSRF cookie

Sometimes teams forget the endpoint that initializes the CSRF token exposure.

### Recommendation
Confirm frontend flow works consistently for anonymous users.

---

# Good things already present

These are positives:

- `SecureRandom` for code generation
- UUID magic-link token
- expiry enforced server-side
- one-time usage concept with `usedAt`
- invalid token format handled safely
- invite code carried through token rather than trusting later client input
- transactional service methods
- Spring Security authorization rules are fairly tight
- logout clears `JSESSIONID`

---

# Priority fixes

If you only do a few things, do these first:

1. **Make token consumption atomic**
2. **Re-check suspended status during verification**
3. **Add rate limiting / attempt limits**
4. **Invalidate previous unused tokens per email**
5. **Avoid GET-based automatic session creation for magic links**
6. **Return DTO instead of `User` entity from `/me`**

---

# Suggested code-level improvements

## Atomic consume repository method
```java
@Modifying
@Query("""
UPDATE AuthToken t
SET t.usedAt = :now
WHERE t.token = :token
  AND t.usedAt IS NULL
  AND t.expiresAt >= :now
""")
int consumeByToken(@Param("token") UUID token, @Param("now") OffsetDateTime now);
```

And similarly for code+email.

Then verification becomes:
- parse token
- attempt atomic consume
- if 0 rows updated => invalid/used/expired
- fetch token if needed for user creation flow

---

## Re-check suspended users
```java
if (existingUser.isPresent()) {
    User user = existingUser.get();
    if (user.getStatus() == UserStatus.SUSPENDED) {
        throw new ApiException("ACCOUNT_SUSPENDED", "This account has been suspended", 403);
    }
    return user;
}
```

---

## Invalidate old tokens before issuing new one
```java
authTokenRepository.deleteUnusedByEmail(email);
```

---

## Use DTO for `/me`
```java
public record MeResponse(UUID id, String email, UserStatus status) {}

@GetMapping("/me")
public MeResponse me(@CurrentUser User user) {
    return new MeResponse(user.getId(), user.getEmail(), user.getStatus());
}
```

---

# Overall assessment

## Security posture: decent foundation, but not production-hardened yet

This is a reasonable implementation structurally, but I would not call it fully hardened until the race condition and abuse protections are addressed.

## Biggest risk
The biggest real security risks are:
- concurrent token reuse
- brute-force attacks
- magic-link GET auto-consumption
- suspended-user bypass via previously issued token

If you want, I can do a **line-by-line audit with inline comments**, or provide a **patched version of the code** showing the highest-priority fixes.--

### Slice 5 — Safety (P0.5)

**Backend:**
- Flyway migrations: `reports`, `moderation_actions`
- `report/` package: submit report
- `moderation/` package: list reports, update status, take actions
- Rate limiting (Bucket4j or custom interceptor)
- Account deletion (soft delete + anonymization + session invalidation)

**Frontend:**
- Report modal (user/activity/message)
- Moderation dashboard (MODERATOR role)
- Account deletion in profile settings

**Deliverable:** Full P0 + P0.5 feature set.

---

## Testing Strategy

### Approach

- **Unit tests** for services (mocked deps) and validators
- **Integration tests** for full flows against real Postgres (Testcontainers + PostGIS)
- **Controller tests** via `@WebMvcTest` with mocked services
- Gradle `java-test-fixtures` plugin for shared test infrastructure

### Test dependencies

```groovy
// build.gradle
plugins {
    id 'java-test-fixtures'
}

dependencies {
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.testcontainers:postgresql'
    testImplementation 'org.testcontainers:junit-jupiter'

    testFixturesImplementation 'org.springframework.boot:spring-boot-starter-test'
    testFixturesImplementation 'org.springframework.boot:spring-boot-starter-data-jpa'
}
```

### Test structure

```
backend/src/
├── test/java/com/junto/
│   ├── auth/
│   │   ├── AuthControllerTest.java
│   │   └── AuthServiceTest.java
│   ├── activity/
│   │   ├── ActivityControllerTest.java
│   │   ├── ActivityServiceTest.java
│   │   └── validation/
│   │       ├── ClimbingFieldValidatorTest.java
│   │       ├── CyclingFieldValidatorTest.java
│   │       └── RunningFieldValidatorTest.java
│   ├── participant/
│   │   ├── ParticipantControllerTest.java
│   │   └── ParticipantServiceTest.java
│   ├── chat/
│   │   ├── ChatControllerTest.java
│   │   └── ChatServiceTest.java
│   ├── vouch/
│   │   └── VouchServiceTest.java
│   ├── invite/
│   │   └── InviteServiceTest.java
│   ├── job/
│   │   ├── ActivityAutoCompleteJobTest.java
│   │   └── PendingRequestDeclineJobTest.java
│   └── integration/
│       ├── AuthFlowIntegrationTest.java
│       ├── JoinFlowIntegrationTest.java
│       ├── ChatAccessIntegrationTest.java
│       ├── VouchFlowIntegrationTest.java
│       ├── InviteFlowIntegrationTest.java
│       └── ConcurrentJoinIntegrationTest.java
│
└── testFixtures/java/com/junto/
    ├── fixtures/          # Static factory methods with sensible defaults
    │   ├── UserFixture.java
    │   ├── ActivityFixture.java
    │   ├── ActivityTypeFixture.java
    │   ├── ParticipantFixture.java
    │   ├── ChatMessageFixture.java
    │   ├── VouchFixture.java
    │   ├── InviteCodeFixture.java
    │   └── AuthTokenFixture.java
    ├── builders/          # Fluent builders for complex scenarios
    │   ├── UserBuilder.java
    │   ├── ActivityBuilder.java
    │   └── ParticipantBuilder.java
    ├── base/              # Base test classes
    │   ├── BaseIntegrationTest.java
    │   ├── BaseControllerTest.java
    │   └── TestContainerConfig.java
    └── helpers/           # Cross-cutting test utilities
        ├── AuthTestHelper.java
        ├── TimeTestHelper.java
        └── CustomFieldsTestHelper.java
```

### Fixtures — design

Each fixture class provides **static factory methods** with sensible defaults. Every field is overridable via named methods. Fixtures create new UUIDs per call so multiple instances never collide.

```java
// Usage examples
User ana = UserFixture.defaultUser();
User bob = UserFixture.withEmail("bob@example.com");
User mod = UserFixture.moderator();

Activity climbing = ActivityFixture.defaultClimbing(ana, climbingType);
Activity full = ActivityFixture.full(ana, climbingType);
Activity pastDeadline = ActivityFixture.deadlinePassed(ana, climbingType);

ActivityParticipant creator = ParticipantFixture.creator(climbing, ana);
ActivityParticipant pending = ParticipantFixture.pending(climbing, bob);
```

For more complex scenarios where fixtures aren't flexible enough, fluent builders chain overrides:

```java
Activity activity = ActivityBuilder.anActivity()
    .withCreator(ana)
    .withType(cyclingType)
    .approvalRequired()
    .withMaxParticipants(10)
    .withDeadline(TimeTestHelper.hoursFromNow(2))
    .startingAt(TimeTestHelper.tomorrow9am())
    .build();
```

### Base test classes

- **`BaseIntegrationTest`** — `@SpringBootTest` + Testcontainers (PostGIS) + `@Transactional` (auto-rollback). Provides `persist()`, `persistUser()`, `persistActivity()` helpers and `authenticatedSession()`.
- **`BaseControllerTest`** — `@WebMvcTest` with `MockMvc` + `ObjectMapper`. Services are mocked.
- **`TestContainerConfig`** — Shared PostGIS container config via `@DynamicPropertySource`. All integration tests extend this.

### Key test patterns

- **`@Nested` classes** group related test cases (e.g., `JoinOpenActivity`, `SignupDeadline`, `ApproveParticipant`)
- **AssertJ** for fluent assertions (`assertThatThrownBy(...)`, `argThat(...)`)
- **Error code assertions** — tests assert on the `code` field (`"SIGNUP_CLOSED"`, `"ACTIVITY_FULL"`, etc.), not message text
- **Time helpers** — `TimeTestHelper` provides consistent UTC-based time references (`hoursFromNow()`, `daysAgo()`, `tomorrow9am()`)
- **Custom field validation** — `CustomFieldsTestHelper` provides invalid field combinations for negative tests

### Integration test scenarios

| Test | What it validates |
|------|------------------|
| AuthFlowIntegrationTest | Full magic link + OTP flow, new user creation, invite code consumption |
| JoinFlowIntegrationTest | OPEN join, APPROVAL_REQUIRED flow, capacity enforcement, deadline enforcement |
| ChatAccessIntegrationTest | Approved users can chat, pending cannot, read-only after completion |
| VouchFlowIntegrationTest | Vouch after completed shared activity, reject if not completed |
| InviteFlowIntegrationTest | Generate codes, validate, consume, revoke |
| ConcurrentJoinIntegrationTest | `SELECT FOR UPDATE` prevents over-capacity under concurrent joins |

### Critical test cases (non-negotiable for P0)

**Join flow:**
- Can join OPEN activity before deadline
- Cannot join after signup_deadline
- Cannot join after starts_at (when no deadline set)
- Cannot join when full (max_participants reached)
- Cannot join twice
- Concurrent joins don't exceed capacity (integration test)
- APPROVAL_REQUIRED → status is PENDING
- Cannot approve after deadline

**Auth flow:**
- New email without invite code → rejected
- New email with valid invite code → account created
- Existing email ignores invite code
- Expired OTP → rejected
- Used OTP → rejected
- Valid OTP → session created
- Magic link → session created

**Chat access:**
- Approved participant can read/write
- Pending participant cannot access
- Non-participant cannot access
- Completed activity → read-only

### Frontend tests

```
frontend/src/__tests__/
├── components/
│   ├── JoinButton.spec.ts
│   └── ActivityCard.spec.ts
└── stores/
    └── auth.spec.ts
```

Tooling: Vitest + Vue Test Utils.

---

## CI/CD

### CI Pipeline (GitHub Actions — runs on every PR)

```yaml
# .github/workflows/ci.yml
name: CI

on:
  pull_request:
    branches: [main]
  push:
    branches: [main]

jobs:
  backend:
    runs-on: ubuntu-latest
    # No service containers — Testcontainers handles Postgres (same as local)

    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
      - uses: gradle/actions/setup-gradle@v4

      - name: Run backend tests
        working-directory: backend
        env:
          SPRING_PROFILES_ACTIVE: test
        run: ./gradlew test

  frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: 22
          cache: npm
          cache-dependency-path: frontend/package-lock.json

      - name: Install dependencies
        working-directory: frontend
        run: npm ci

      - name: Lint
        working-directory: frontend
        run: npm run lint

      - name: Type check
        working-directory: frontend
        run: npm run typecheck

      - name: Run tests
        working-directory: frontend
        run: npm run test:unit

      - name: Build
        working-directory: frontend
        run: npm run build
```

Notes:
- **Testcontainers only** — same behavior locally and in CI, no service container conflict
- **No Flyway validate step** — tests run migrations via Spring Boot startup, broken migrations fail tests
- **Generated API freshness check** — skipped for P0. Trust dev workflow (regenerate + commit). Type check catches mismatches.

### CD — Auto-deploy on push to main

| Service | Deploy method | Trigger |
|---------|--------------|---------|
| Backend (Railway) | Railway native GitHub integration — auto-detects pushes to `main`, builds from `backend/` | Push to main |
| Frontend (Vercel) | Vercel native GitHub integration — auto-detects pushes to `main`, builds from `frontend/` | Push to main |

No GitHub Actions needed for deployment — both platforms have native GitHub integration. Connect repos, set root directory (`backend/` for Railway, `frontend/` for Vercel), configure env vars in each platform's dashboard.

**Railway config:**
- Root directory: `backend`
- Build: Gradle (`./gradlew bootJar`)
- Start: `java -jar build/libs/*.jar`
- Env vars: `SPRING_PROFILES_ACTIVE=prod`, `DATABASE_URL`, `RESEND_API_KEY`, `ALLOWED_ORIGIN`

**Vercel config:**
- Root directory: `frontend`
- Build command: `npm run build`
- Output directory: `dist`
- Env vars: `VITE_API_BASE_URL` (if needed, though Vite proxy handles dev)

### Pre-push hooks

Using `husky` + `lint-staged`:

```json
// frontend/package.json
{
  "lint-staged": {
    "frontend/**/*.{ts,vue}": ["eslint --fix", "prettier --write"]
  }
}
```

Pre-push hook runs fast checks only (full tests in CI):

```bash
#!/bin/sh
# .husky/pre-push

echo "Running backend compilation check..."
cd backend && ./gradlew classes testClasses --no-daemon -q
result=$?
if [ $result -ne 0 ]; then
  echo "❌ Backend compilation failed. Push aborted."
  exit 1
fi

echo "Running frontend type check..."
cd ../frontend && npm run typecheck
result=$?
if [ $result -ne 0 ]; then
  echo "❌ Frontend type check failed. Push aborted."
  exit 1
fi

echo "✅ Pre-push checks passed. Full tests will run in CI."
```

### Branch rules

| Rule | Setting |
|------|---------|
| Default branch | `main` |
| Branch naming | `feat/`, `fix/`, `chore/` prefix + short description (e.g. `feat/auth-magic-link`) |
| Require PR | Yes — no direct pushes to `main` |
| Required checks | CI pipeline must pass (backend + frontend jobs) |
| Required reviewers | 1 (for pilot — can be self-merged after CI passes) |
| Squash merge | Yes — keeps main history clean |
| Commit style | Conventional commits (`feat:`, `fix:`, `chore:`, `test:`, `docs:`) |

### Conventional commits

```
feat: add magic link + OTP auth flow
fix: prevent joining after signup deadline
chore: update Gradle wrapper
test: add ParticipantService unit tests
docs: update README with dev workflow
refactor: extract CustomFields typed wrapper
ci: add GitHub Actions pipeline
```

---

## Dev Workflow

```bash
# 1. Start Postgres + Mailpit
docker compose up -d

# 2. Backend
cd backend && ./gradlew bootRun

# 3. Frontend
cd frontend && npm run dev

# 4. Regenerate API client (after backend changes)
cd frontend && npm run generate-api

# 5. Useful URLs
# App:          http://localhost:5173
# Backend API:  http://localhost:8080
# Mailpit:      http://localhost:8025
# OpenAPI spec: http://localhost:8080/v3/api-docs
```

### .gitignore (root)

```
.env

backend/build/
backend/.gradle/

frontend/node_modules/
frontend/dist/

# Don't ignore generated API client — commit it
# frontend/src/api/generated/
```
