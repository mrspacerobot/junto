# Test Gaps and Fixtures Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the current test suite green, close the highest-value chat/message test gaps, and improve test readability with shared backend fixtures plus stable frontend E2E selectors.

**Architecture:** Keep production behavior unchanged unless a test proves it is wrong. Add small test-fixture classes under Gradle `java-test-fixtures`, then migrate duplicated setup gradually by area. Frontend E2E stabilization uses `data-testid` hooks and state-based waits instead of generic classes and sleeps.

**Tech Stack:** Java 21, Spring Boot 4, JUnit 5, Mockito, AssertJ, MockMvc, Gradle `java-test-fixtures`, Vue 3, TypeScript, Playwright.

---

## File Map

Create:

- `backend/src/testFixtures/java/ch/junto/helpers/MockMvcTestHelper.java` - shared MockMvc session and JSON helpers.
- `backend/src/testFixtures/java/ch/junto/fixtures/ActivityParticipantFixture.java` - participant builders for integration and unit tests.
- `backend/src/testFixtures/java/ch/junto/fixtures/ChatMessageFixture.java` - chat message builders with activity/user/type/timestamp set correctly.

Modify:

- `backend/src/testFixtures/java/ch/junto/fixtures/ActivityFixture.java` - add clearer activity builders for type/location scenarios.
- `backend/src/testFixtures/java/ch/junto/fixtures/UserFixture.java` - add role/status-specific persistable user builders.
- `backend/src/test/java/ch/junto/chat/ChatServiceTest.java` - fix failing cursor fixture and add missing service gap tests.
- `backend/src/test/java/ch/junto/chat/ChatIntegrationTest.java` - use shared fixtures and add cross-activity cursor test.
- `backend/src/test/java/ch/junto/message/MessageThreadIntegrationTest.java` - use shared fixtures and add pending-participant visibility test.
- `backend/src/test/java/ch/junto/activity/ActivityIntegrationTest.java` - use shared session/JSON helpers.
- `backend/src/test/java/ch/junto/activity/ActivitySpatialQueryIntegrationTest.java` - use shared activity/session helpers.
- `backend/src/test/java/ch/junto/activity/ActivityConcurrencyTest.java` - use shared user/activity/session helpers.
- `backend/src/test/java/ch/junto/user/UserLocationIntegrationTest.java` - use shared session helper.
- `backend/src/test/java/ch/junto/auth/AuthIntegrationTest.java` - use shared JSON/user/invite helpers.
- `frontend/src/views/messages/ThreadList.vue` - add stable thread-row test id.
- `frontend/src/components/activity/ActivityCard.vue` - add stable activity-card test id.
- `frontend/src/views/messages/ChatPanel.vue` - add stable message/input test ids for the Messages screen.
- `frontend/src/components/chat/ChatInput.vue` - add stable input test id for the Activity Detail chat.
- `frontend/src/components/chat/ChatMessage.vue` - add stable message test id for the Activity Detail chat.
- `frontend/e2e/messages.spec.ts` - replace brittle selectors, sleeps, skips, and shared users.

---

### Task 1: Fix Chat Cursor Unit Failure

**Files:**
- Modify: `backend/src/test/java/ch/junto/chat/ChatServiceTest.java`

- [ ] **Step 1: Run the focused failing test**

Run:

```bash
cd backend
./gradlew test --tests ch.junto.chat.ChatServiceTest.getMessages_withCursor_fetchesBeforeCursor
```

Expected: FAIL with a `NullPointerException` from `ChatService.getMessages(...)` because the cursor message has no activity.

- [ ] **Step 2: Fix the cursor fixture in the test**

In `backend/src/test/java/ch/junto/chat/ChatServiceTest.java`, update `getMessages_withCursor_fetchesBeforeCursor` so the cursor belongs to the same activity:

```java
@Test
void getMessages_withCursor_fetchesBeforeCursor() {
    ActivityParticipant participant = new ActivityParticipant();
    participant.setStatus(ParticipantStatus.APPROVED);

    UUID cursorId = UUID.randomUUID();
    ChatMessage cursorMsg = new ChatMessage();
    cursorMsg.setId(cursorId);
    cursorMsg.setActivity(activity);
    cursorMsg.setCreatedAt(OffsetDateTime.now());

    when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId())).thenReturn(Optional.of(participant));
    when(chatMessageRepository.findById(cursorId)).thenReturn(Optional.of(cursorMsg));
    when(chatMessageRepository.findByActivityIdBeforeCursor(any(), any(), any(), anyInt())).thenReturn(List.of());

    List<ChatMessageResponse> result = chatService.getMessages(activity.getId(), user, 50, cursorId);

    assertThat(result).isEmpty();
    verify(chatMessageRepository).findByActivityIdBeforeCursor(eq(activity.getId()), eq(cursorMsg.getCreatedAt()), eq(cursorId), eq(50));
}
```

- [ ] **Step 3: Run the focused test**

Run:

```bash
cd backend
./gradlew test --tests ch.junto.chat.ChatServiceTest.getMessages_withCursor_fetchesBeforeCursor
```

Expected: PASS.

- [ ] **Step 4: Run the full chat service test class**

Run:

```bash
cd backend
./gradlew test --tests ch.junto.chat.ChatServiceTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/ch/junto/chat/ChatServiceTest.java
git commit -m "test: fix chat cursor unit fixture"
```

---

### Task 2: Add Backend Test Fixture Helpers

**Files:**
- Create: `backend/src/testFixtures/java/ch/junto/helpers/MockMvcTestHelper.java`
- Create: `backend/src/testFixtures/java/ch/junto/fixtures/ActivityParticipantFixture.java`
- Create: `backend/src/testFixtures/java/ch/junto/fixtures/ChatMessageFixture.java`
- Modify: `backend/src/testFixtures/java/ch/junto/fixtures/ActivityFixture.java`
- Modify: `backend/src/testFixtures/java/ch/junto/fixtures/UserFixture.java`

- [ ] **Step 1: Create MockMvcTestHelper**

Create `backend/src/testFixtures/java/ch/junto/helpers/MockMvcTestHelper.java`:

```java
package ch.junto.helpers;

import ch.junto.user.entity.User;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

public final class MockMvcTestHelper {

    private MockMvcTestHelper() {}

    public static MockHttpSession sessionFor(User user) {
        MockHttpSession session = new MockHttpSession();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    public static String json(ObjectMapper objectMapper, Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

- [ ] **Step 2: Create ActivityParticipantFixture**

Create `backend/src/testFixtures/java/ch/junto/fixtures/ActivityParticipantFixture.java`:

```java
package ch.junto.fixtures;

import ch.junto.activity.entity.Activity;
import ch.junto.participant.entity.ActivityParticipant;
import ch.junto.participant.enums.ParticipantRole;
import ch.junto.participant.enums.ParticipantStatus;
import ch.junto.user.entity.User;

public final class ActivityParticipantFixture {

    private ActivityParticipantFixture() {}

    public static ActivityParticipant approvedCreator(Activity activity, User user) {
        return participant(activity, user, ParticipantRole.CREATOR, ParticipantStatus.APPROVED);
    }

    public static ActivityParticipant approvedParticipant(Activity activity, User user) {
        return participant(activity, user, ParticipantRole.PARTICIPANT, ParticipantStatus.APPROVED);
    }

    public static ActivityParticipant pendingParticipant(Activity activity, User user) {
        return participant(activity, user, ParticipantRole.PARTICIPANT, ParticipantStatus.PENDING);
    }

    public static ActivityParticipant participant(
        Activity activity,
        User user,
        ParticipantRole role,
        ParticipantStatus status
    ) {
        ActivityParticipant participant = new ActivityParticipant();
        participant.setActivity(activity);
        participant.setUser(user);
        participant.setRole(role);
        participant.setStatus(status);
        return participant;
    }
}
```

- [ ] **Step 3: Create ChatMessageFixture**

Create `backend/src/testFixtures/java/ch/junto/fixtures/ChatMessageFixture.java`:

```java
package ch.junto.fixtures;

import ch.junto.activity.entity.Activity;
import ch.junto.chat.entity.ChatMessage;
import ch.junto.chat.enums.MessageType;
import ch.junto.user.entity.User;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class ChatMessageFixture {

    private ChatMessageFixture() {}

    public static ChatMessage userMessage(Activity activity, User user, String content) {
        return message(activity, user, MessageType.USER, content, null);
    }

    public static ChatMessage systemMessage(Activity activity, String content) {
        return message(activity, null, MessageType.SYSTEM, content, null);
    }

    public static ChatMessage cursorMessage(Activity activity, OffsetDateTime createdAt) {
        ChatMessage message = message(activity, null, MessageType.USER, "Cursor", createdAt);
        message.setId(UUID.randomUUID());
        return message;
    }

    public static ChatMessage message(
        Activity activity,
        User user,
        MessageType type,
        String content,
        OffsetDateTime createdAt
    ) {
        ChatMessage message = new ChatMessage();
        message.setActivity(activity);
        message.setUser(user);
        message.setType(type);
        message.setContent(content);
        if (createdAt != null) {
            message.setCreatedAt(createdAt);
            message.setUpdatedAt(createdAt);
        }
        return message;
    }
}
```

- [ ] **Step 4: Extend ActivityFixture**

Add these methods to `backend/src/testFixtures/java/ch/junto/fixtures/ActivityFixture.java` before the final closing brace:

```java
public static Activity newClimbingActivity(User creator) {
    return newActivity(creator, "climbing");
}

public static Activity newCyclingActivity(User creator) {
    return newActivity(creator, "cycling");
}

public static Activity newActivityAt(
    User creator,
    String typeSlug,
    String title,
    double lat,
    double lng,
    OffsetDateTime startsAt
) {
    Activity activity = newActivity(creator, typeSlug);
    activity.setTitle(title);
    activity.setLocationLat(lat);
    activity.setLocationLng(lng);
    activity.setStartsAt(startsAt);
    return activity;
}

public static Activity newActivityWithoutLocation(
    User creator,
    String typeSlug,
    String title,
    OffsetDateTime startsAt
) {
    Activity activity = newActivity(creator, typeSlug);
    activity.setTitle(title);
    activity.setStartsAt(startsAt);
    activity.setLocationLat(null);
    activity.setLocationLng(null);
    return activity;
}
```

- [ ] **Step 5: Extend UserFixture**

Add these methods to `backend/src/testFixtures/java/ch/junto/fixtures/UserFixture.java` before the final closing brace:

```java
public static User newMember(String email) {
    return newUser(email);
}

public static User newAdmin(String email) {
    User user = newUser(email);
    user.setRole(UserRole.ADMIN);
    return user;
}

public static User newSuspended(String email) {
    User user = newUser(email);
    user.setStatus(UserStatus.SUSPENDED);
    return user;
}

public static User persistableWithUniqueEmail(String prefix) {
    return newUser(prefix + "-" + UUID.randomUUID() + "@junto.app");
}
```

- [ ] **Step 6: Compile test fixtures**

Run:

```bash
cd backend
./gradlew compileTestFixturesJava
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add backend/src/testFixtures/java/ch/junto/helpers/MockMvcTestHelper.java backend/src/testFixtures/java/ch/junto/fixtures/ActivityParticipantFixture.java backend/src/testFixtures/java/ch/junto/fixtures/ChatMessageFixture.java backend/src/testFixtures/java/ch/junto/fixtures/ActivityFixture.java backend/src/testFixtures/java/ch/junto/fixtures/UserFixture.java
git commit -m "test: add shared backend fixtures"
```

---

### Task 3: Add Chat Service Gap Tests With Fixtures

**Files:**
- Modify: `backend/src/test/java/ch/junto/chat/ChatServiceTest.java`

- [ ] **Step 1: Import new fixtures**

Add imports to `ChatServiceTest.java`:

```java
import ch.junto.fixtures.ActivityParticipantFixture;
import ch.junto.fixtures.ChatMessageFixture;
```

- [ ] **Step 2: Replace local approvedParticipant helper usage**

Where tests need an approved participant, prefer:

```java
ActivityParticipant participant = ActivityParticipantFixture.approvedParticipant(activity, user);
```

At the end of the task, remove this local helper if no usages remain:

```java
private ActivityParticipant approvedParticipant() {
    ActivityParticipant participant = new ActivityParticipant();
    participant.setStatus(ParticipantStatus.APPROVED);
    return participant;
}
```

- [ ] **Step 3: Add cursor-not-found test**

Add this test method to `ChatServiceTest.java`:

```java
@Test
void getMessages_cursorNotFound_throwsNotFoundException() {
    UUID cursorId = UUID.randomUUID();

    when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId()))
        .thenReturn(Optional.of(ActivityParticipantFixture.approvedParticipant(activity, user)));
    when(chatMessageRepository.findById(cursorId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> chatService.getMessages(activity.getId(), user, 50, cursorId))
        .isInstanceOf(NotFoundException.class);

    verify(chatMessageRepository, never()).findByActivityIdBeforeCursor(any(), any(), any(), anyInt());
}
```

- [ ] **Step 4: Add lower limit clamp test**

Add this test method:

```java
@Test
void getMessages_limitBelowOne_clampsToOne() {
    when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId()))
        .thenReturn(Optional.of(ActivityParticipantFixture.approvedParticipant(activity, user)));
    when(chatMessageRepository.findLatestByActivityId(activity.getId(), 1)).thenReturn(List.of());

    List<ChatMessageResponse> result = chatService.getMessages(activity.getId(), user, 0, null);

    assertThat(result).isEmpty();
    verify(chatMessageRepository).findLatestByActivityId(activity.getId(), 1);
}
```

- [ ] **Step 5: Add upper limit clamp test**

Add this test method:

```java
@Test
void getMessages_limitAboveOneHundred_clampsToOneHundred() {
    when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId()))
        .thenReturn(Optional.of(ActivityParticipantFixture.approvedParticipant(activity, user)));
    when(chatMessageRepository.findLatestByActivityId(activity.getId(), 100)).thenReturn(List.of());

    List<ChatMessageResponse> result = chatService.getMessages(activity.getId(), user, 500, null);

    assertThat(result).isEmpty();
    verify(chatMessageRepository).findLatestByActivityId(activity.getId(), 100);
}
```

- [ ] **Step 6: Use ChatMessageFixture for cross-activity cursor**

In `getMessages_cursorFromDifferentActivity_throwsNotFound`, replace manual cursor construction with:

```java
Activity otherActivity = ActivityFixture.defaultActivity(UserFixture.userWithEmail("other-host@ex.com"), "cycling");
ChatMessage cursorMsg = ChatMessageFixture.cursorMessage(otherActivity, OffsetDateTime.now());
```

- [ ] **Step 7: Run chat service tests**

Run:

```bash
cd backend
./gradlew test --tests ch.junto.chat.ChatServiceTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add backend/src/test/java/ch/junto/chat/ChatServiceTest.java
git commit -m "test: cover chat message cursor limits"
```

---

### Task 4: Migrate Chat Integration Tests and Add Cross-Activity Cursor Coverage

**Files:**
- Modify: `backend/src/test/java/ch/junto/chat/ChatIntegrationTest.java`

- [ ] **Step 1: Add fixture imports**

Add imports:

```java
import ch.junto.chat.entity.ChatMessage;
import ch.junto.chat.enums.MessageType;
import ch.junto.fixtures.ActivityParticipantFixture;
import ch.junto.fixtures.ChatMessageFixture;
import ch.junto.helpers.MockMvcTestHelper;
import jakarta.persistence.EntityManager;
```

Add field:

```java
@Autowired private EntityManager entityManager;
```

- [ ] **Step 2: Replace participant helper implementation**

Change `addApprovedParticipant(...)` to:

```java
private void addApprovedParticipant(Activity act, User user, ParticipantRole role) {
    dbHelper.save(ActivityParticipantFixture.participant(act, user, role, ParticipantStatus.APPROVED));
}
```

- [ ] **Step 3: Replace JSON and session helpers**

Replace the helper bodies:

```java
private String json(Object dto) {
    return MockMvcTestHelper.json(objectMapper, dto);
}

private MockHttpSession buildSession(User user) {
    return MockMvcTestHelper.sessionFor(user);
}
```

- [ ] **Step 4: Add deterministic message persistence helper**

Add this helper near `sendMessage(...)`:

```java
private ChatMessage saveMessage(Activity targetActivity, User sender, String content, OffsetDateTime createdAt) {
    ChatMessage message = ChatMessageFixture.message(targetActivity, sender, MessageType.USER, content, createdAt);
    dbHelper.save(message);

    entityManager.createNativeQuery("""
        UPDATE chat_messages
        SET created_at = :createdAt, updated_at = :createdAt
        WHERE id = :id
        """)
        .setParameter("createdAt", createdAt)
        .setParameter("id", message.getId())
        .executeUpdate();
    entityManager.flush();
    return message;
}
```

- [ ] **Step 5: Add failing integration test for cursor from another activity**

Add this method inside `CursorPagination`:

```java
@Test
@DisplayName("should return 404 when cursor belongs to another activity")
void shouldRejectCursorFromAnotherActivity() throws Exception {
    Activity otherActivity = dbHelper.save(ActivityFixture.newActivity(participant, "cycling"));
    addApprovedParticipant(otherActivity, participant, ParticipantRole.CREATOR);
    ChatMessage cursor = saveMessage(otherActivity, participant, "Other activity message", OffsetDateTime.now().minusMinutes(1));

    mockMvc.perform(get(messagesUrl())
            .session(buildSession(participant))
            .param("before", cursor.getId().toString()))
        .andExpect(status().isNotFound());
}
```

- [ ] **Step 6: Run the focused integration test**

Run:

```bash
cd backend
./gradlew test --tests 'ch.junto.chat.ChatIntegrationTest$CursorPagination.shouldRejectCursorFromAnotherActivity'
```

Expected: PASS. This confirms existing production behavior is covered.

- [ ] **Step 7: Run all chat integration tests**

Run:

```bash
cd backend
./gradlew test --tests ch.junto.chat.ChatIntegrationTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add backend/src/test/java/ch/junto/chat/ChatIntegrationTest.java
git commit -m "test: improve chat integration fixtures"
```

---

### Task 5: Migrate Message Thread Integration Tests

**Files:**
- Modify: `backend/src/test/java/ch/junto/message/MessageThreadIntegrationTest.java`

- [ ] **Step 1: Add fixture imports**

Add imports:

```java
import ch.junto.fixtures.ActivityParticipantFixture;
import ch.junto.fixtures.ChatMessageFixture;
import ch.junto.helpers.MockMvcTestHelper;
```

- [ ] **Step 2: Replace buildSession helper**

Replace `buildSession(User user)` with:

```java
private MockHttpSession buildSession(User user) {
    return MockMvcTestHelper.sessionFor(user);
}
```

- [ ] **Step 3: Replace addParticipant helper**

Replace `addParticipant(...)` with:

```java
private void addParticipant(Activity activity, User user, ParticipantRole role, ParticipantStatus status) {
    dbHelper.save(ActivityParticipantFixture.participant(activity, user, role, status));
}
```

- [ ] **Step 4: Replace saveMessage construction**

In `saveMessage(...)`, replace manual `new ChatMessage()` field setup with:

```java
ChatMessage message = ChatMessageFixture.message(activity, sender, type, content, createdAt);
dbHelper.save(message);
```

Keep the native timestamp update block after `dbHelper.save(message)`.

- [ ] **Step 5: Add pending participant thread visibility test**

Add this test before `getThreads_returns401WhenUnauthenticated`:

```java
@Test
void getThreads_excludesPendingParticipantEvenWhenActivityHasUserMessages() throws Exception {
    User host = dbHelper.save(UserFixture.newUser("host-" + UUID.randomUUID() + "@junto.app"));
    Activity activity = dbHelper.save(ActivityFixture.newActivity(host, "climbing"));
    addParticipant(activity, host, ParticipantRole.CREATOR, ParticipantStatus.APPROVED);
    addParticipant(activity, user, ParticipantRole.PARTICIPANT, ParticipantStatus.PENDING);
    saveMessage(activity, host, MessageType.USER, "Approved host message", OffsetDateTime.now());

    mockMvc.perform(get("/api/messages/threads").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
}
```

- [ ] **Step 6: Run message thread tests**

Run:

```bash
cd backend
./gradlew test --tests ch.junto.message.MessageThreadIntegrationTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add backend/src/test/java/ch/junto/message/MessageThreadIntegrationTest.java
git commit -m "test: improve message thread fixtures"
```

---

### Task 6: Migrate Activity and User Integration Test Helpers

**Files:**
- Modify: `backend/src/test/java/ch/junto/activity/ActivityIntegrationTest.java`
- Modify: `backend/src/test/java/ch/junto/activity/ActivitySpatialQueryIntegrationTest.java`
- Modify: `backend/src/test/java/ch/junto/activity/ActivityConcurrencyTest.java`
- Modify: `backend/src/test/java/ch/junto/user/UserLocationIntegrationTest.java`

- [ ] **Step 1: Update ActivityIntegrationTest helpers**

In `ActivityIntegrationTest.java`, add:

```java
import ch.junto.helpers.MockMvcTestHelper;
```

Replace helper bodies:

```java
private String json(Object dto) {
    return MockMvcTestHelper.json(objectMapper, dto);
}

private MockHttpSession buildSession(User user) {
    return MockMvcTestHelper.sessionFor(user);
}
```

- [ ] **Step 2: Update ActivitySpatialQueryIntegrationTest session helper**

In `ActivitySpatialQueryIntegrationTest.java`, add:

```java
import ch.junto.fixtures.ActivityFixture;
import ch.junto.helpers.MockMvcTestHelper;
```

Replace `buildSession(User user)`:

```java
private MockHttpSession buildSession(User user) {
    return MockMvcTestHelper.sessionFor(user);
}
```

Replace `createActivityAt(...)`:

```java
private Activity createActivityAt(String title, double lat, double lng, OffsetDateTime startsAt) {
    return dbHelper.save(ActivityFixture.newActivityAt(testUser, "climbing", title, lat, lng, startsAt));
}
```

Replace `createActivityWithoutLocation(...)`:

```java
private Activity createActivityWithoutLocation(String title, OffsetDateTime startsAt) {
    return dbHelper.save(ActivityFixture.newActivityWithoutLocation(testUser, "climbing", title, startsAt));
}
```

- [ ] **Step 3: Update ActivityConcurrencyTest builders**

In `ActivityConcurrencyTest.java`, add:

```java
import ch.junto.fixtures.ActivityFixture;
import ch.junto.fixtures.UserFixture;
import ch.junto.helpers.MockMvcTestHelper;
```

Replace `createAndTrackUser(...)` body:

```java
private User createAndTrackUser(String email) {
    User user = userRepository.save(UserFixture.newMember(email));
    createdUserIds.add(user.getId());
    return user;
}
```

Replace `createAndTrackActivity(...)` body:

```java
private Activity createAndTrackActivity(User creator, int maxParticipants) {
    Activity activity = ActivityFixture.newClimbingActivity(creator);
    activity.setTitle("Concurrency Test");
    activity.setDescription("Test");
    activity.setCity("Zurich");
    activity.setMaxParticipants(maxParticipants);
    activity = activityRepository.save(activity);
    createdActivityIds.add(activity.getId());
    return activity;
}
```

Replace `buildSession(User user)`:

```java
private MockHttpSession buildSession(User user) {
    return MockMvcTestHelper.sessionFor(user);
}
```

- [ ] **Step 4: Update UserLocationIntegrationTest session helper**

In `UserLocationIntegrationTest.java`, add:

```java
import ch.junto.helpers.MockMvcTestHelper;
```

Replace `buildSession(User user)`:

```java
private MockHttpSession buildSession(User user) {
    return MockMvcTestHelper.sessionFor(user);
}
```

- [ ] **Step 5: Run affected integration tests**

Run:

```bash
cd backend
./gradlew test --tests ch.junto.activity.ActivityIntegrationTest --tests ch.junto.activity.ActivitySpatialQueryIntegrationTest --tests ch.junto.activity.ActivityConcurrencyTest --tests ch.junto.user.UserLocationIntegrationTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add backend/src/test/java/ch/junto/activity/ActivityIntegrationTest.java backend/src/test/java/ch/junto/activity/ActivitySpatialQueryIntegrationTest.java backend/src/test/java/ch/junto/activity/ActivityConcurrencyTest.java backend/src/test/java/ch/junto/user/UserLocationIntegrationTest.java
git commit -m "test: reuse fixtures in activity integration tests"
```

---

### Task 7: Migrate Auth Integration Test Helpers

**Files:**
- Modify: `backend/src/test/java/ch/junto/auth/AuthIntegrationTest.java`

- [ ] **Step 1: Add helper imports**

Add imports:

```java
import ch.junto.fixtures.InviteCodeFixture;
import ch.junto.fixtures.UserFixture;
import ch.junto.helpers.MockMvcTestHelper;
```

- [ ] **Step 2: Replace invite seed setup**

Replace `seedInviteCode()` body:

```java
@BeforeEach
void seedInviteCode() {
    User inviter = userRepository.save(UserFixture.newAdmin("inviter@junto.app"));

    InviteCode code = InviteCodeFixture.valid(inviter);
    code.setCode(INVITE_CODE);
    inviteCodeRepository.save(code);
}
```

- [ ] **Step 3: Replace json helper**

Replace `json(Object dto)`:

```java
private String json(Object dto) {
    return MockMvcTestHelper.json(objectMapper, dto);
}
```

- [ ] **Step 4: Replace seedUser helper**

Replace `seedUser(String email)`:

```java
private User seedUser(String email) {
    return userRepository.save(UserFixture.newMember(email));
}
```

- [ ] **Step 5: Run auth integration tests**

Run:

```bash
cd backend
./gradlew test --tests ch.junto.auth.AuthIntegrationTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add backend/src/test/java/ch/junto/auth/AuthIntegrationTest.java
git commit -m "test: reuse fixtures in auth integration tests"
```

---

### Task 8: Verify Backend Suite

**Files:**
- Test only.

- [ ] **Step 1: Run full backend suite**

Run:

```bash
cd backend
./gradlew test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Check no duplicated helper methods remain in targeted tests**

Run:

```bash
rg -n "private (String json|MockHttpSession buildSession|void addParticipant|void addApprovedParticipant|User seedUser|new User\\(|new ActivityParticipant\\()" backend/src/test/java/ch/junto
```

Expected: remaining matches are either outside the targeted files or are intentionally local because the test needs custom construction. Do not refactor unrelated tests in this task.

- [ ] **Step 3: Commit backend cleanup if needed**

```bash
git add backend/src/test/java/ch/junto backend/src/testFixtures/java/ch/junto
git commit -m "test: finish backend fixture migration"
```

---

### Task 9: Add Stable Frontend Test IDs

**Files:**
- Modify: `frontend/src/views/messages/ThreadList.vue`
- Modify: `frontend/src/components/activity/ActivityCard.vue`
- Modify: `frontend/src/views/messages/ChatPanel.vue`
- Modify: `frontend/src/components/chat/ChatInput.vue`
- Modify: `frontend/src/components/chat/ChatMessage.vue`

- [ ] **Step 1: Add message thread row test id**

In `frontend/src/views/messages/ThreadList.vue`, update the `v-for="thread in filteredThreads"` row:

```vue
<div
  v-for="thread in filteredThreads"
  :key="thread.activityId"
  data-testid="message-thread-row"
  @click="$emit('select', thread)"
  :class="[
    'flex gap-2.5 px-4 py-3 cursor-pointer transition-colors',
    activeId === thread.activityId
      ? 'bg-accent-bg border-l-[3px] border-primary-700'
      : 'border-l-[3px] border-transparent hover:bg-warm-50'
  ]"
>
```

- [ ] **Step 2: Add activity card test id**

In `frontend/src/components/activity/ActivityCard.vue`, update the root element:

```vue
<div
  data-testid="activity-card"
  class="bg-white rounded-[14px] shadow-card hover:shadow-card-hover transition-all border border-warm-200 overflow-hidden group flex flex-col md:flex-row cursor-pointer"
  @click="$router.push({ name: 'activity-detail', params: { id: activity.id } })"
>
```

- [ ] **Step 3: Add Messages-screen chat test ids**

In `frontend/src/views/messages/ChatPanel.vue`, add `data-testid="chat-message"` to each rendered message container:

```vue
<div v-if="msg.type === 'SYSTEM'" data-testid="chat-message" class="text-center text-xs text-[#9D998F] italic py-1">
```

```vue
<div v-else-if="msg.userId === currentUserId" data-testid="chat-message" class="flex justify-end gap-2 items-end">
```

```vue
<div v-else data-testid="chat-message" class="flex gap-2 items-end">
```

Add `data-testid="chat-message-input"` to the input:

```vue
<input
  data-testid="chat-message-input"
  v-model="input"
  @keydown.enter="handleSend"
  type="text"
  placeholder="Write a message…"
  class="flex-1 bg-warm-50 border border-warm-300 rounded-full px-4 py-2.5 text-sm outline-none focus:border-primary-500 text-slate-900 placeholder-[#9D998F] font-sans"
/>
```

- [ ] **Step 4: Add Activity Detail chat input test id**

In `frontend/src/components/chat/ChatInput.vue`, update the input:

```vue
<input
  data-testid="chat-message-input"
  v-model="text"
  @keyup.enter="handleSend"
  type="text"
  placeholder="Type a message..."
  class="flex-grow bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-sm focus:ring-2 focus:ring-primary-500 outline-none transition-all"
/>
```

- [ ] **Step 5: Add Activity Detail chat message test id**

In `frontend/src/components/chat/ChatMessage.vue`, update the root element:

```vue
<div data-testid="chat-message" :class="['flex w-full mb-4', isMe ? 'justify-end' : 'justify-start']">
```

- [ ] **Step 6: Run frontend build**

Run:

```bash
cd frontend
npm run build
```

Expected: `vite build` completes successfully.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/views/messages/ThreadList.vue frontend/src/components/activity/ActivityCard.vue frontend/src/views/messages/ChatPanel.vue frontend/src/components/chat/ChatInput.vue frontend/src/components/chat/ChatMessage.vue
git commit -m "test: add stable frontend selectors"
```

---

### Task 10: Stabilize Messages E2E Selectors and Waits

**Files:**
- Modify: `frontend/e2e/messages.spec.ts`

- [ ] **Step 1: Replace helper constants and selectors**

At the top of `frontend/e2e/messages.spec.ts`, replace `const TEST_EMAIL = 'anna@example.com'` with:

```ts
const uniqueEmail = (name: string) => `${name}-${Date.now()}-${Math.random().toString(36).slice(2)}@example.com`

const threadRows = (page: import('@playwright/test').Page) =>
  page.getByTestId('message-thread-row')

const chatInput = (page: import('@playwright/test').Page) =>
  page.getByTestId('chat-message-input')

const activityCards = (page: import('@playwright/test').Page) =>
  page.getByTestId('activity-card')
```

- [ ] **Step 2: Update two-user chat emails and selectors**

In `two users can chat in real time`, replace hardcoded logins:

```ts
const annaEmail = uniqueEmail('anna')
const marcoEmail = uniqueEmail('marco')

await login(anna, annaEmail)
await login(marco, marcoEmail)
```

Replace thread selection:

```ts
await page.goto('/messages')
await page.waitForLoadState('domcontentloaded')
await expect(threadRows(page).first()).toBeVisible()
await threadRows(page).first().click()
await expect(chatInput(page)).toBeVisible()
```

Replace local `msgInput` helper usage with `chatInput(page)`.

- [ ] **Step 3: Update serial describe setup**

Remove `const TEST_EMAIL = 'anna@example.com'`.

Replace `beforeEach`:

```ts
test.beforeEach(async ({ page }, testInfo) => {
  await login(page, uniqueEmail(`messages-${testInfo.parallelIndex}`))
})
```

- [ ] **Step 4: Replace fixed waits in layout test**

Keep:

```ts
await page.goto('/messages')
await page.waitForLoadState('domcontentloaded')
await expect(page.locator('h1:has-text("Messages")')).toBeVisible()
await expect(page.locator('input[placeholder="Search messages"]')).toBeVisible()
await expect(page.getByText('Select a conversation')).toBeVisible()
```

Remove `waitForTimeout(...)` from this test.

- [ ] **Step 5: Replace activity card selector in join test**

In `joins an activity to create a message thread`, replace:

```ts
const planCard = page.locator('.cursor-pointer').first()
```

with:

```ts
await expect(activityCards(page).first()).toBeVisible()
const planCard = activityCards(page).first()
```

Remove the `test.skip(...)` branch for missing activity cards.

- [ ] **Step 6: Replace thread-row skip branches**

In thread-opening and send-message tests, replace:

```ts
const threadRow = page.locator('.cursor-pointer').first()
if ((await threadRow.count()) === 0) {
  test.skip(true, 'No message threads available')
  return
}

await threadRow.click()
await page.waitForTimeout(1000)
```

with:

```ts
await expect(threadRows(page).first()).toBeVisible()
await threadRows(page).first().click()
await expect(chatInput(page)).toBeVisible()
```

- [ ] **Step 7: Replace send-message wait**

In `can send a message in a thread`, replace:

```ts
const msgInput = page.locator('input[placeholder="Write a message…"]')
await msgInput.fill('Hello from Playwright!')
await page.keyboard.press('Enter')
await page.waitForTimeout(1500)
await expect(page.getByText('Hello from Playwright!').first()).toBeVisible()
```

with:

```ts
await chatInput(page).fill('Hello from Playwright!')
await page.keyboard.press('Enter')
await expect(page.getByText('Hello from Playwright!').first()).toBeVisible()
```

- [ ] **Step 8: Replace search assertion selector**

In `search filters thread list`, replace:

```ts
await page.waitForTimeout(500)
const threads = page.locator('.cursor-pointer')
await expect(threads).toHaveCount(0)
```

with:

```ts
await expect(threadRows(page)).toHaveCount(0)
```

- [ ] **Step 9: Run frontend build**

Run:

```bash
cd frontend
npm run build
```

Expected: `vite build` completes successfully.

- [ ] **Step 10: Commit**

```bash
git add frontend/e2e/messages.spec.ts
git commit -m "test: stabilize messages e2e selectors"
```

---

### Task 11: Verify E2E Data Reliability

**Files:**
- Modify: `frontend/e2e/messages.spec.ts`
- Modify only if needed: backend dev/test setup files under `backend/src/main/java/ch/junto/auth` or a new dev-only setup controller.

- [ ] **Step 1: Run current messages E2E**

Run with the frontend/backend dev stack already running:

```bash
cd frontend
npm run test:e2e -- messages.spec.ts
```

Expected: tests either pass or fail because the new unique users do not have deterministic activities/threads.

- [ ] **Step 2: If tests fail due to missing threads, add an E2E setup helper using existing REST APIs**

In `frontend/e2e/messages.spec.ts`, add this helper after selectors:

```ts
async function createActivity(page: import('@playwright/test').Page, title: string) {
  const startsAt = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString()
  const deadline = new Date(Date.now() + 12 * 60 * 60 * 1000).toISOString()

  const result = await page.evaluate(async ({ title, startsAt, deadline }) => {
    const csrfMatch = document.cookie.match(/XSRF-TOKEN=([^;]+)/)
    const csrf = csrfMatch ? decodeURIComponent(csrfMatch[1]) : ''
    const res = await fetch('/api/activities', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(csrf ? { 'X-XSRF-TOKEN': csrf } : {}),
      },
      credentials: 'include',
      body: JSON.stringify({
        title,
        description: 'Created by Playwright',
        activityTypeSlug: 'cycling',
        city: 'Zurich',
        locationLat: 47.3769,
        locationLng: 8.5417,
        startsAt,
        signupDeadline: deadline,
        maxParticipants: 4,
        joinPolicy: 'OPEN',
        customFields: {},
      }),
    })
    if (!res.ok) {
      return { ok: false, status: res.status, body: await res.text() }
    }
    return { ok: true, status: res.status, body: await res.json() }
  }, { title, startsAt, deadline })

  if (!result.ok) {
    throw new Error(`activity creation failed: ${result.status} ${result.body}`)
  }
  return result.body as { id: string }
}
```

- [ ] **Step 3: If tests fail due to missing shared thread, create thread setup in the test**

In each test that needs a thread, create the activity as the logged-in user before visiting `/messages`:

```ts
const activity = await createActivity(page, `Messages test ${Date.now()}`)
await page.goto(`/activities/${activity.id}`)
await page.goto('/messages')
await expect(threadRows(page).first()).toBeVisible()
```

For the two-user chat test, create the activity as Anna, then have Marco visit it and join before both open `/messages`:

```ts
const activity = await createActivity(anna, `Two user chat ${Date.now()}`)
await marco.goto(`/activities/${activity.id}`)
const joinButton = marco.getByRole('button', { name: /join/i })
if (await joinButton.isVisible()) {
  await joinButton.click()
}
```

- [ ] **Step 4: Run messages E2E**

Run:

```bash
cd frontend
npm run test:e2e -- messages.spec.ts
```

Expected: tests pass without `test.skip(...)` branches and without `waitForTimeout(...)`.

- [ ] **Step 5: Commit**

```bash
git add frontend/e2e/messages.spec.ts
git commit -m "test: make messages e2e data deterministic"
```

---

### Task 12: Final Verification

**Files:**
- Test only.

- [ ] **Step 1: Run full backend test suite**

Run:

```bash
cd backend
./gradlew test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run frontend build**

Run:

```bash
cd frontend
npm run build
```

Expected: TypeScript and Vite build complete successfully.

- [ ] **Step 3: Run frontend E2E**

Run with the app stack running:

```bash
cd frontend
npm run test:e2e
```

Expected: Playwright reports all tests passed.

- [ ] **Step 4: Scan for brittle E2E patterns**

Run:

```bash
rg -n "waitForTimeout|test\\.skip|\\.cursor-pointer" frontend/e2e frontend/src
```

Expected: no matches in `frontend/e2e/messages.spec.ts`; matches elsewhere should be reviewed but not expanded into unrelated work.

- [ ] **Step 5: Check worktree**

Run:

```bash
git status --short
```

Expected: only intentional files from this plan are modified.

---

## Rollback Notes

If frontend E2E deterministic setup exposes production API contract issues, keep Tasks 1-10 and split Task 11 into a separate dev/test setup spec. Do not block the backend fixture migration on frontend E2E data setup if the frontend stack is not runnable locally.

## Self-Review

Spec coverage:

- Current failing backend test is covered by Task 1.
- Backend fixture readability is covered by Tasks 2-8.
- Targeted backend gap tests are covered by Tasks 3-5.
- Frontend selectors and waits are covered by Tasks 9-10.
- E2E deterministic data setup is covered by Task 11.
- Final verification is covered by Task 12.

Placeholder scan:

- No banned placeholder tokens found.

Type consistency:

- Fixture classes use existing packages: `ch.junto.fixtures` and `ch.junto.helpers`.
- Test helpers use the repo's `tools.jackson.databind.ObjectMapper`.
- Frontend test ids match the selectors used by the Playwright plan.
