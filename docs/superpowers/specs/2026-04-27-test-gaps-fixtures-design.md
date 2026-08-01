# Test Gaps and Fixtures Design Spec

**Date:** 2026-04-27
**Status:** Draft

## Goal

Stabilize the current test suite, close the highest-value test gaps found during analysis, and improve test readability by moving duplicated setup into shared `testFixtures`.

## Analysis Summary

Backend test execution currently compiles and runs, but the suite is not green:

```bash
cd backend
./gradlew test
```

Observed result:

```text
212 tests completed, 1 failed
ChatServiceTest > getMessages_withCursor_fetchesBeforeCursor() FAILED
java.lang.NullPointerException at ChatServiceTest.java:242
```

The failing test builds a cursor `ChatMessage` with an id and timestamp but no activity. `ChatService.getMessages(...)` checks `cursor.getActivity().getId()` to reject cursors from another activity, so the unit fixture is incomplete. The production ownership check is meaningful and should stay covered.

The backend already enables Gradle `java-test-fixtures` and has:

- `backend/src/testFixtures/java/ch/junto/base/BaseIntegrationTest.java`
- `backend/src/testFixtures/java/ch/junto/base/TestContainerConfig.java`
- `backend/src/testFixtures/java/ch/junto/fixtures/UserFixture.java`
- `backend/src/testFixtures/java/ch/junto/fixtures/ActivityFixture.java`
- `backend/src/testFixtures/java/ch/junto/fixtures/AuthTokenFixture.java`
- `backend/src/testFixtures/java/ch/junto/fixtures/InviteCodeFixture.java`
- `backend/src/testFixtures/java/ch/junto/helpers/DatabaseHelper.java`
- `backend/src/testFixtures/java/ch/junto/helpers/TimeTestHelper.java`

However, integration tests still duplicate session, JSON, user, activity, participant, and chat-message setup. Frontend E2E tests rely on generic `.cursor-pointer` selectors, `waitForTimeout`, data-dependent skips, and shared seeded dev state.

## Issues Found

### 1. Failing backend unit test fixture

`backend/src/test/java/ch/junto/chat/ChatServiceTest.java`

`getMessages_withCursor_fetchesBeforeCursor` creates:

```java
ChatMessage cursorMsg = new ChatMessage();
cursorMsg.setId(cursorId);
cursorMsg.setCreatedAt(OffsetDateTime.now());
```

but does not set `cursorMsg.setActivity(activity)`. The service dereferences the activity to verify cursor ownership.

### 2. Duplicated authenticated session setup

The same `MockHttpSession` construction appears in:

- `backend/src/test/java/ch/junto/activity/ActivityIntegrationTest.java`
- `backend/src/test/java/ch/junto/activity/ActivityConcurrencyTest.java`
- `backend/src/test/java/ch/junto/activity/ActivitySpatialQueryIntegrationTest.java`
- `backend/src/test/java/ch/junto/chat/ChatIntegrationTest.java`
- `backend/src/test/java/ch/junto/message/MessageThreadIntegrationTest.java`
- `backend/src/test/java/ch/junto/user/UserLocationIntegrationTest.java`

This makes tests longer and increases the odds of inconsistent authentication setup.

### 3. Duplicated JSON serialization helpers

Local `json(Object dto)` helpers exist in:

- `backend/src/test/java/ch/junto/activity/ActivityIntegrationTest.java`
- `backend/src/test/java/ch/junto/auth/AuthIntegrationTest.java`
- `backend/src/test/java/ch/junto/chat/ChatIntegrationTest.java`

This should be centralized in a base class or fixture helper.

### 4. Duplicated persisted entity builders

Several tests manually construct entities instead of using fixtures:

- `ActivityConcurrencyTest` manually creates users and activities.
- `ActivitySpatialQueryIntegrationTest` manually creates activities with location.
- `ChatIntegrationTest` manually creates approved participants.
- `MessageThreadIntegrationTest` manually creates participants and chat messages.
- `AuthIntegrationTest` manually creates users and invite codes even though `UserFixture` and `InviteCodeFixture` exist.

The test intent is harder to read because each test repeats persistence details.

### 5. Test fixture split is implicit, not enforced by API shape

Existing fixtures distinguish unit and integration usage by method comments:

- `UserFixture.defaultUser()` has an id for unit tests.
- `UserFixture.newUser(...)` has no id for persistence.
- `ActivityFixture.defaultActivity(...)` has an id for unit tests.
- `ActivityFixture.newActivity(...)` has no id for persistence.

The convention is useful, but the naming does not make the unit vs persisted path obvious enough when reading tests.

### 6. Frontend E2E tests are brittle and partially data-dependent

`frontend/e2e/messages.spec.ts` uses:

- `.cursor-pointer` to select thread rows and activity cards.
- `waitForTimeout(...)` for readiness.
- `test.skip(...)` when seeded activities or threads are missing.
- Shared emails such as `anna@example.com`, `marco@example.com`, and `TEST_EMAIL = 'anna@example.com'`.

This can hide regressions and make CI failures depend on seed data rather than test-controlled setup.

## Proposed Architecture

Keep the current Gradle `java-test-fixtures` setup and add focused fixture helpers under `backend/src/testFixtures/java/ch/junto`.

### Backend fixture helpers

Create `ch.junto.helpers.MockMvcTestHelper`:

- `MockHttpSession sessionFor(User user)`
- `String json(ObjectMapper objectMapper, Object value)`

Create `ch.junto.fixtures.ActivityParticipantFixture`:

- `approvedCreator(Activity activity, User user)`
- `approvedParticipant(Activity activity, User user)`
- `pendingParticipant(Activity activity, User user)`
- `participant(Activity activity, User user, ParticipantRole role, ParticipantStatus status)`

Create `ch.junto.fixtures.ChatMessageFixture`:

- `userMessage(Activity activity, User user, String content)`
- `systemMessage(Activity activity, String content)`
- `cursorMessage(Activity activity, OffsetDateTime createdAt)`
- `message(Activity activity, User user, MessageType type, String content, OffsetDateTime createdAt)`

Create `ch.junto.fixtures.ActivityFixture` additions:

- `newClimbingActivity(User creator)`
- `newCyclingActivity(User creator)`
- `newActivityAt(User creator, String typeSlug, String title, double lat, double lng, OffsetDateTime startsAt)`
- `newActivityWithoutLocation(User creator, String typeSlug, String title, OffsetDateTime startsAt)`

Create `ch.junto.fixtures.UserFixture` additions:

- `newAdmin(String email)`
- `newMember(String email)`
- `newSuspended(String email)`
- `persistableWithUniqueEmail(String prefix)`

These helpers should only create objects. Persistence remains explicit through `DatabaseHelper.save(...)` so tests still show when database state is being created.

### Naming convention

Use these names consistently:

- `new...` returns an entity without id for persistence.
- `existing...` or `default...` returns an entity with id for unit tests.
- Helper names should include business meaning, e.g. `approvedParticipant(...)`, not only technical fields.

Do not rename all existing fixture methods in this pass. Add clearer methods and migrate touched tests only, then optionally deprecate ambiguous methods later.

## Backend Test Plan

### Task 1: Fix the failing chat cursor unit test

Modify `backend/src/test/java/ch/junto/chat/ChatServiceTest.java`.

Acceptance:

- `getMessages_withCursor_fetchesBeforeCursor` sets the cursor message activity.
- Add or keep a separate test that verifies a cursor from another activity throws `NotFoundException`.
- Run:

```bash
cd backend
./gradlew test --tests ch.junto.chat.ChatServiceTest
```

Expected:

```text
BUILD SUCCESSFUL
```

### Task 2: Add shared test fixture helpers

Create:

- `backend/src/testFixtures/java/ch/junto/helpers/MockMvcTestHelper.java`
- `backend/src/testFixtures/java/ch/junto/fixtures/ActivityParticipantFixture.java`
- `backend/src/testFixtures/java/ch/junto/fixtures/ChatMessageFixture.java`

Modify:

- `backend/src/testFixtures/java/ch/junto/fixtures/ActivityFixture.java`
- `backend/src/testFixtures/java/ch/junto/fixtures/UserFixture.java`

Acceptance:

- No fixture helper persists implicitly.
- Fixture methods produce valid entities that satisfy current domain defaults.
- Unit fixtures with ids and persistence fixtures without ids remain available.

Run:

```bash
cd backend
./gradlew compileTestFixturesJava
```

Expected:

```text
BUILD SUCCESSFUL
```

### Task 3: Migrate chat/message integration tests to fixtures

Modify:

- `backend/src/test/java/ch/junto/chat/ChatIntegrationTest.java`
- `backend/src/test/java/ch/junto/message/MessageThreadIntegrationTest.java`

Acceptance:

- Remove local `buildSession(...)` and use `MockMvcTestHelper.sessionFor(...)`.
- Remove local JSON helper in `ChatIntegrationTest`.
- Replace participant construction with `ActivityParticipantFixture`.
- Replace chat message construction with `ChatMessageFixture`.
- Tests still clearly show which user, activity, participant status, and message content are relevant to each scenario.

Run:

```bash
cd backend
./gradlew test --tests ch.junto.chat.ChatIntegrationTest --tests ch.junto.message.MessageThreadIntegrationTest
```

Expected:

```text
BUILD SUCCESSFUL
```

### Task 4: Migrate activity integration tests to fixtures

Modify:

- `backend/src/test/java/ch/junto/activity/ActivityIntegrationTest.java`
- `backend/src/test/java/ch/junto/activity/ActivitySpatialQueryIntegrationTest.java`
- `backend/src/test/java/ch/junto/activity/ActivityConcurrencyTest.java`
- `backend/src/test/java/ch/junto/user/UserLocationIntegrationTest.java`

Acceptance:

- Remove duplicated `buildSession(...)` helpers.
- Use `ActivityFixture` location helpers in spatial tests.
- Use `UserFixture` methods instead of manual user construction in concurrency tests.
- Preserve `ActivityConcurrencyTest` cleanup behavior or replace it with an equally explicit transactional/test-isolation strategy.

Run:

```bash
cd backend
./gradlew test --tests ch.junto.activity.ActivityIntegrationTest --tests ch.junto.activity.ActivitySpatialQueryIntegrationTest --tests ch.junto.activity.ActivityConcurrencyTest --tests ch.junto.user.UserLocationIntegrationTest
```

Expected:

```text
BUILD SUCCESSFUL
```

### Task 5: Migrate auth integration tests to fixtures

Modify:

- `backend/src/test/java/ch/junto/auth/AuthIntegrationTest.java`

Acceptance:

- Use `UserFixture.newAdmin(...)` or equivalent for inviter setup.
- Use `InviteCodeFixture` for invite-code setup.
- Use shared JSON helper.
- Keep assertions around signup, invite usage, session auth, code reuse, and superseded codes unchanged.

Run:

```bash
cd backend
./gradlew test --tests ch.junto.auth.AuthIntegrationTest
```

Expected:

```text
BUILD SUCCESSFUL
```

### Task 6: Close targeted backend gap tests

Add focused tests while the fixtures are being introduced:

- `ChatServiceTest`: cursor not found throws `NotFoundException`.
- `ChatServiceTest`: `limit` is clamped to 1 when zero or negative.
- `ChatServiceTest`: `limit` is clamped to 100 when above max.
- `ChatIntegrationTest`: `before` cursor from another activity returns 404.
- `MessageThreadIntegrationTest`: pending participant cannot see a thread even when the activity has user messages.

Acceptance:

- Each new test is named by behavior, not implementation.
- Tests use the new fixtures.
- No sleeps or shared mutable test data are introduced.

Run:

```bash
cd backend
./gradlew test
```

Expected:

```text
BUILD SUCCESSFUL
```

## Frontend E2E Test Plan

### Task 7: Replace brittle selectors with stable test ids

Modify frontend components used by `messages.spec.ts`:

- Thread rows should expose `data-testid="message-thread-row"`.
- Activity cards used by the E2E join flow should expose `data-testid="activity-card"`.
- Chat input should expose `data-testid="chat-message-input"`.
- Message bubbles should expose `data-testid="chat-message"`.

Modify:

- `frontend/e2e/messages.spec.ts`

Acceptance:

- No selectors depend on `.cursor-pointer`.
- Tests use `page.getByTestId(...)` or accessible role/name selectors.

Run:

```bash
cd frontend
npm run build
```

Expected:

```text
vite build
```

### Task 8: Replace fixed waits with state-based waits

Modify:

- `frontend/e2e/messages.spec.ts`

Acceptance:

- Remove `waitForTimeout(...)` calls.
- Use assertions such as `await expect(page.getByTestId('message-thread-row').first()).toBeVisible()`.
- Use route/load assertions only where they represent real readiness.

Run:

```bash
cd frontend
npm run build
```

Expected:

```text
vite build
```

### Task 9: Make E2E data setup explicit

Preferred approach:

- Add backend dev/test setup endpoints only if they can be restricted to `dev` or `test` profiles.
- Otherwise, create Playwright setup helpers that call existing dev-login plus existing REST APIs to create or join deterministic activities before assertions.

Acceptance:

- Remove `test.skip(...)` branches caused by missing activities or threads.
- Use unique emails per test run, e.g. `messages-${Date.now()}-${test.info().parallelIndex}@example.com`.
- Each test owns the user/activity/thread state it asserts.

Run:

```bash
cd frontend
npm run test:e2e
```

Expected:

```text
passed
```

## Non-Goals

- Do not refactor production chat, activity, auth, or participant behavior beyond what is needed to make tests accurate.
- Do not rename all fixture methods globally in one large sweep.
- Do not add frontend unit-test infrastructure in this pass.
- Do not make E2E tests depend on production-only endpoints.

## Verification Checklist

Run backend verification:

```bash
cd backend
./gradlew test
```

Run frontend verification:

```bash
cd frontend
npm run build
npm run test:e2e
```

Final expected state:

- Backend test suite passes.
- Frontend build passes.
- E2E messages tests use deterministic setup and stable selectors.
- Repeated integration setup is centralized in `testFixtures`.
- Test files read in terms of domain scenarios rather than object-construction boilerplate.
