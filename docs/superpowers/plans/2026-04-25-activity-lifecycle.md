# Activity Lifecycle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add status transition guards, date validation, leave cutoffs, pessimistic locking for capacity, reactivation endpoint, and updatable maxParticipants/joinPolicy — closing all lifecycle edge cases in the activity and participant systems.

**Architecture:** Guard logic lives in the service layer (ActivityService, ParticipantService). Pessimistic locking is a repository-level query. Date validation is service-level (cross-field). A single Flyway migration adds a CHECK constraint and status index. Dead code (ParticipantStatus.CANCELLED) is removed.

**Tech Stack:** Java 21, Spring Boot 3, JPA/Hibernate, PostgreSQL, Flyway, JUnit 5 + Mockito (unit), Spring MockMvc + Testcontainers (integration)

**Spec:** `docs/superpowers/specs/2026-04-25-activity-lifecycle-design.md`

---

### Task 1: Status Transition Guards on cancel() and complete()

**Files:**
- Modify: `backend/src/main/java/ch/junto/activity/ActivityService.java:114-136`
- Test: `backend/src/test/java/ch/junto/activity/ActivityIntegrationTest.java`

- [ ] **Step 1: Write failing integration tests for cancel guards**

Add to `ActivityIntegrationTest.java`:

```java
@Test @DisplayName("should return 409 when cancelling already-cancelled activity")
void shouldRejectCancelOnCancelled() throws Exception {
    Activity activity = dbHelper.save(ActivityFixture.newActivity(testUser, "climbing"));
    activity.setStatus(ActivityStatus.CANCELLED);
    activityRepository.save(activity);

    mockMvc.perform(post("/api/activities/" + activity.getId() + "/cancel")
            .session(buildSession(testUser)))
        .andExpect(status().isConflict());
}

@Test @DisplayName("should return 409 when cancelling completed activity")
void shouldRejectCancelOnCompleted() throws Exception {
    Activity activity = dbHelper.save(ActivityFixture.newActivity(testUser, "climbing"));
    activity.setStatus(ActivityStatus.COMPLETED);
    activityRepository.save(activity);

    mockMvc.perform(post("/api/activities/" + activity.getId() + "/cancel")
            .session(buildSession(testUser)))
        .andExpect(status().isConflict());
}
```

- [ ] **Step 2: Write failing integration tests for complete guards**

Add to `ActivityIntegrationTest.java`:

```java
@Test @DisplayName("should return 409 when completing cancelled activity")
void shouldRejectCompleteOnCancelled() throws Exception {
    Activity activity = dbHelper.save(ActivityFixture.newActivity(testUser, "climbing"));
    activity.setStatus(ActivityStatus.CANCELLED);
    activityRepository.save(activity);

    mockMvc.perform(post("/api/activities/" + activity.getId() + "/complete")
            .session(buildSession(testUser)))
        .andExpect(status().isConflict());
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `cd backend && ./gradlew test --tests "ch.junto.activity.ActivityIntegrationTest.shouldRejectCancelOnCancelled" --tests "ch.junto.activity.ActivityIntegrationTest.shouldRejectCancelOnCompleted" --tests "ch.junto.activity.ActivityIntegrationTest.shouldRejectCompleteOnCancelled" -i`

Expected: All 3 FAIL — cancel and complete currently accept any status.

- [ ] **Step 4: Add status guards to cancel() and complete()**

In `ActivityService.java`, add a guard after the creator check in both methods:

`cancel()` — after line 119:
```java
if (activity.getStatus() != ActivityStatus.ACTIVE) {
    throw new ConflictException("Activity is not active");
}
```

`complete()` — after line 131:
```java
if (activity.getStatus() != ActivityStatus.ACTIVE) {
    throw new ConflictException("Activity is not active");
}
```

- [ ] **Step 5: Run all cancel/complete tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "ch.junto.activity.ActivityIntegrationTest.shouldCancelActivity" --tests "ch.junto.activity.ActivityIntegrationTest.shouldRejectCancelOnCancelled" --tests "ch.junto.activity.ActivityIntegrationTest.shouldRejectCancelOnCompleted" --tests "ch.junto.activity.ActivityIntegrationTest.shouldCompleteActivity" --tests "ch.junto.activity.ActivityIntegrationTest.shouldRejectCompleteOnCancelled" -i`

Expected: All 5 PASS.

- [ ] **Step 6: Commit**

```bash
cd backend && git add src/main/java/ch/junto/activity/ActivityService.java src/test/java/ch/junto/activity/ActivityIntegrationTest.java && git commit -m "feat: add status transition guards to cancel() and complete()"
```

---

### Task 2: Update Guard (reject COMPLETED)

**Files:**
- Modify: `backend/src/main/java/ch/junto/activity/ActivityService.java:88-112`
- Test: `backend/src/test/java/ch/junto/activity/ActivityIntegrationTest.java`

- [ ] **Step 1: Write failing integration tests for update guards**

Add to `ActivityIntegrationTest.java`:

```java
@Test @DisplayName("should return 409 when updating completed activity")
void shouldRejectUpdateOnCompleted() throws Exception {
    Activity activity = dbHelper.save(ActivityFixture.newActivity(testUser, "climbing"));
    activity.setStatus(ActivityStatus.COMPLETED);
    activityRepository.save(activity);

    ActivityUpdateRequest request = new ActivityUpdateRequest(
        "New Title", "Desc", "Zurich", null, null,
        OffsetDateTime.now().plusDays(3), null, null
    );

    mockMvc.perform(put("/api/activities/" + activity.getId())
            .session(buildSession(testUser))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(request)))
        .andExpect(status().isConflict());
}

@Test @DisplayName("should allow updating cancelled activity")
void shouldAllowUpdateOnCancelled() throws Exception {
    Activity activity = dbHelper.save(ActivityFixture.newActivity(testUser, "climbing"));
    activity.setStatus(ActivityStatus.CANCELLED);
    activityRepository.save(activity);

    ActivityUpdateRequest request = new ActivityUpdateRequest(
        "Fixed Title", "Desc", "Zurich", null, null,
        OffsetDateTime.now().plusDays(3), null, null
    );

    mockMvc.perform(put("/api/activities/" + activity.getId())
            .session(buildSession(testUser))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Fixed Title"));
}
```

- [ ] **Step 2: Run tests to verify the completed test fails**

Run: `cd backend && ./gradlew test --tests "ch.junto.activity.ActivityIntegrationTest.shouldRejectUpdateOnCompleted" --tests "ch.junto.activity.ActivityIntegrationTest.shouldAllowUpdateOnCancelled" -i`

Expected: `shouldRejectUpdateOnCompleted` FAILS (currently returns 200), `shouldAllowUpdateOnCancelled` PASSES.

- [ ] **Step 3: Add status guard to update()**

In `ActivityService.java` `update()`, add after the creator check (after line 94):

```java
if (activity.getStatus() == ActivityStatus.COMPLETED) {
    throw new ConflictException("Completed activities cannot be edited");
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "ch.junto.activity.ActivityIntegrationTest.shouldRejectUpdateOnCompleted" --tests "ch.junto.activity.ActivityIntegrationTest.shouldAllowUpdateOnCancelled" --tests "ch.junto.activity.ActivityIntegrationTest.shouldUpdateActivity" -i`

Expected: All 3 PASS.

- [ ] **Step 5: Commit**

```bash
cd backend && git add src/main/java/ch/junto/activity/ActivityService.java src/test/java/ch/junto/activity/ActivityIntegrationTest.java && git commit -m "feat: reject updates on completed activities"
```

---

### Task 3: Reactivate Endpoint

**Files:**
- Modify: `backend/src/main/java/ch/junto/activity/ActivityController.java`
- Modify: `backend/src/main/java/ch/junto/activity/ActivityService.java`
- Test: `backend/src/test/java/ch/junto/activity/ActivityIntegrationTest.java`

- [ ] **Step 1: Write failing integration tests for reactivate**

Add to `ActivityIntegrationTest.java`:

```java
@Test @DisplayName("should reactivate cancelled activity")
void shouldReactivateCancelledActivity() throws Exception {
    Activity activity = dbHelper.save(ActivityFixture.newActivity(testUser, "climbing"));
    activity.setStatus(ActivityStatus.CANCELLED);
    activityRepository.save(activity);

    mockMvc.perform(post("/api/activities/" + activity.getId() + "/reactivate")
            .session(buildSession(testUser)))
        .andExpect(status().isOk());

    Activity reactivated = activityRepository.findById(activity.getId()).orElseThrow();
    assertThat(reactivated.getStatus()).isEqualTo(ActivityStatus.ACTIVE);
}

@Test @DisplayName("should return 409 when reactivating active activity")
void shouldRejectReactivateOnActive() throws Exception {
    Activity activity = dbHelper.save(ActivityFixture.newActivity(testUser, "climbing"));

    mockMvc.perform(post("/api/activities/" + activity.getId() + "/reactivate")
            .session(buildSession(testUser)))
        .andExpect(status().isConflict());
}

@Test @DisplayName("should return 409 when reactivating completed activity")
void shouldRejectReactivateOnCompleted() throws Exception {
    Activity activity = dbHelper.save(ActivityFixture.newActivity(testUser, "climbing"));
    activity.setStatus(ActivityStatus.COMPLETED);
    activityRepository.save(activity);

    mockMvc.perform(post("/api/activities/" + activity.getId() + "/reactivate")
            .session(buildSession(testUser)))
        .andExpect(status().isConflict());
}

@Test @DisplayName("should return 403 when non-creator tries to reactivate")
void shouldForbidReactivateByNonCreator() throws Exception {
    Activity activity = dbHelper.save(ActivityFixture.newActivity(testUser, "climbing"));
    activity.setStatus(ActivityStatus.CANCELLED);
    activityRepository.save(activity);
    User otherUser = dbHelper.save(UserFixture.newUser("other-" + UUID.randomUUID() + "@junto.app"));

    mockMvc.perform(post("/api/activities/" + activity.getId() + "/reactivate")
            .session(buildSession(otherUser)))
        .andExpect(status().isForbidden());
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./gradlew test --tests "ch.junto.activity.ActivityIntegrationTest.shouldReactivateCancelledActivity" -i`

Expected: FAIL — 404 (endpoint doesn't exist).

- [ ] **Step 3: Add reactivate() to ActivityService**

Add to `ActivityService.java` (after `complete()` method):

```java
public void reactivate(UUID id, User user) {
    Activity activity = activityRepository.findById(id)
        .orElseThrow(() -> new NotFoundException("Activity not found"));

    if (!activity.getCreator().getId().equals(user.getId())) {
        throw new ForbiddenException("Only creator can reactivate activity");
    }

    if (activity.getStatus() != ActivityStatus.CANCELLED) {
        throw new ConflictException("Activity is not cancelled");
    }

    activity.setStatus(ActivityStatus.ACTIVE);
    activityRepository.save(activity);
}
```

- [ ] **Step 4: Add reactivate endpoint to ActivityController**

Add to `ActivityController.java` (after `complete()` endpoint):

```java
@PostMapping("/{id}/reactivate")
@Operation(summary = "Reactivate activity", description = "Only the creator can reactivate a cancelled activity.")
@ApiResponse(responseCode = "200", description = "Activity reactivated")
@ApiResponse(responseCode = "401", description = "Not authenticated")
@ApiResponse(responseCode = "403", description = "Not the activity creator")
@ApiResponse(responseCode = "404", description = "Activity not found")
@ApiResponse(responseCode = "409", description = "Activity is not cancelled")
public void reactivate(@PathVariable UUID id, @CurrentUser User user) {
    activityService.reactivate(id, user);
}
```

- [ ] **Step 5: Run all reactivate tests**

Run: `cd backend && ./gradlew test --tests "ch.junto.activity.ActivityIntegrationTest.shouldReactivateCancelledActivity" --tests "ch.junto.activity.ActivityIntegrationTest.shouldRejectReactivateOnActive" --tests "ch.junto.activity.ActivityIntegrationTest.shouldRejectReactivateOnCompleted" --tests "ch.junto.activity.ActivityIntegrationTest.shouldForbidReactivateByNonCreator" -i`

Expected: All 4 PASS.

- [ ] **Step 6: Commit**

```bash
cd backend && git add src/main/java/ch/junto/activity/ActivityService.java src/main/java/ch/junto/activity/ActivityController.java src/test/java/ch/junto/activity/ActivityIntegrationTest.java && git commit -m "feat: add POST /activities/{id}/reactivate endpoint"
```

---

### Task 4: Remove ParticipantStatus.CANCELLED Dead Code

**Files:**
- Modify: `backend/src/main/java/ch/junto/participant/enums/ParticipantStatus.java`
- Test: `backend/src/test/java/ch/junto/participant/ParticipantStatusTest.java` (create)

- [ ] **Step 1: Write unit test verifying CANCELLED is not in the enum**

Create `backend/src/test/java/ch/junto/participant/ParticipantStatusTest.java`:

```java
package ch.junto.participant;

import ch.junto.participant.enums.ParticipantStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ParticipantStatus")
class ParticipantStatusTest {

    @Test @DisplayName("should not contain CANCELLED value")
    void shouldNotContainCancelled() {
        List<String> values = Arrays.stream(ParticipantStatus.values())
            .map(Enum::name)
            .toList();
        assertThat(values).doesNotContain("CANCELLED");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "ch.junto.participant.ParticipantStatusTest" -i`

Expected: FAIL — CANCELLED is still in the enum.

- [ ] **Step 3: Remove CANCELLED from the enum**

Change `ParticipantStatus.java` from:

```java
public enum ParticipantStatus {
    PENDING,
    APPROVED,
    DECLINED,
    CANCELLED
}
```

To:

```java
public enum ParticipantStatus {
    PENDING,
    APPROVED,
    DECLINED
}
```

- [ ] **Step 4: Verify no compile errors referencing CANCELLED**

Run: `cd backend && ./gradlew compileJava`

Expected: BUILD SUCCESSFUL (nothing references `ParticipantStatus.CANCELLED`).

- [ ] **Step 5: Run the test**

Run: `cd backend && ./gradlew test --tests "ch.junto.participant.ParticipantStatusTest" -i`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
cd backend && git add src/main/java/ch/junto/participant/enums/ParticipantStatus.java src/test/java/ch/junto/participant/ParticipantStatusTest.java && git commit -m "refactor: remove dead ParticipantStatus.CANCELLED"
```

---

### Task 5: Leave Cutoff

**Files:**
- Modify: `backend/src/main/java/ch/junto/participant/ParticipantService.java:80-89`
- Test: `backend/src/test/java/ch/junto/participant/ParticipantServiceTest.java`

- [ ] **Step 1: Write failing unit tests for leave cutoff**

Add to `ParticipantServiceTest.java`:

```java
@Test
void leave_beforeDeadline_deletesParticipant() {
    activity.setSignupDeadline(OffsetDateTime.now().plusHours(1));
    ActivityParticipant participant = new ActivityParticipant();
    participant.setRole(ParticipantRole.PARTICIPANT);
    participant.setActivity(activity);

    when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
    when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId()))
        .thenReturn(Optional.of(participant));

    participantService.leave(activity.getId(), user);

    verify(participantRepository).delete(participant);
}

@Test
void leave_afterDeadline_throwsConflict() {
    activity.setSignupDeadline(OffsetDateTime.now().minusHours(1));
    ActivityParticipant participant = new ActivityParticipant();
    participant.setRole(ParticipantRole.PARTICIPANT);
    participant.setActivity(activity);

    when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
    when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId()))
        .thenReturn(Optional.of(participant));

    assertThatThrownBy(() -> participantService.leave(activity.getId(), user))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("Signup deadline has passed");
}

@Test
void leave_noDeadline_afterStartsAt_throwsConflict() {
    activity.setSignupDeadline(null);
    activity.setStartsAt(OffsetDateTime.now().minusHours(1));
    ActivityParticipant participant = new ActivityParticipant();
    participant.setRole(ParticipantRole.PARTICIPANT);
    participant.setActivity(activity);

    when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
    when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId()))
        .thenReturn(Optional.of(participant));

    assertThatThrownBy(() -> participantService.leave(activity.getId(), user))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("Signup deadline has passed");
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./gradlew test --tests "ch.junto.participant.ParticipantServiceTest.leave_afterDeadline_throwsConflict" --tests "ch.junto.participant.ParticipantServiceTest.leave_noDeadline_afterStartsAt_throwsConflict" -i`

Expected: Both FAIL — leave() currently has no deadline check.

- [ ] **Step 3: Add deadline check to leave()**

Replace `ParticipantService.leave()` with:

```java
public void leave(UUID activityId, User user) {
    Activity activity = activityRepository.findById(activityId)
        .orElseThrow(() -> new NotFoundException("Activity not found"));

    OffsetDateTime deadline = activity.getSignupDeadline() != null ? activity.getSignupDeadline() : activity.getStartsAt();
    if (OffsetDateTime.now().isAfter(deadline)) {
        throw new ConflictException("Signup deadline has passed");
    }

    ActivityParticipant participant = participantRepository.findByActivityIdAndUserId(activityId, user.getId())
        .orElseThrow(() -> new NotFoundException("Not a participant"));

    if (participant.getRole() == ParticipantRole.CREATOR) {
        throw new ConflictException("Creator cannot leave activity");
    }

    participantRepository.delete(participant);
}
```

Note: The activity lookup is now needed for the deadline check. This changes the method signature's behavior — it now loads the activity first. The existing `leave_participant_removesParticipant` test must be updated to mock `activityRepository.findById`.

- [ ] **Step 4: Update existing leave tests to mock activityRepository**

Update `leave_participant_removesParticipant` in `ParticipantServiceTest.java`:

```java
@Test
void leave_participant_removesParticipant() {
    ActivityParticipant participant = new ActivityParticipant();
    participant.setRole(ParticipantRole.PARTICIPANT);
    participant.setActivity(activity);

    when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
    when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId()))
        .thenReturn(Optional.of(participant));

    participantService.leave(activity.getId(), user);

    verify(participantRepository).delete(participant);
}
```

Update `leave_creator_throwsConflict`:

```java
@Test
void leave_creator_throwsConflict() {
    ActivityParticipant participant = new ActivityParticipant();
    participant.setRole(ParticipantRole.CREATOR);
    participant.setActivity(activity);

    when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
    when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId()))
        .thenReturn(Optional.of(participant));

    assertThatThrownBy(() -> participantService.leave(activity.getId(), user))
        .isInstanceOf(ConflictException.class);
}
```

Update `leave_notParticipant_throwsNotFoundException`:

```java
@Test
void leave_notParticipant_throwsNotFoundException() {
    when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
    when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> participantService.leave(activity.getId(), user))
        .isInstanceOf(NotFoundException.class);
}
```

- [ ] **Step 5: Run all leave tests**

Run: `cd backend && ./gradlew test --tests "ch.junto.participant.ParticipantServiceTest.leave*" -i`

Expected: All 6 leave tests PASS.

- [ ] **Step 6: Commit**

```bash
cd backend && git add src/main/java/ch/junto/participant/ParticipantService.java src/test/java/ch/junto/participant/ParticipantServiceTest.java && git commit -m "feat: add leave cutoff using effective signup deadline"
```

---

### Task 6: Pessimistic Lock for Capacity

**Files:**
- Modify: `backend/src/main/java/ch/junto/activity/ActivityRepository.java`
- Modify: `backend/src/main/java/ch/junto/participant/ParticipantService.java:43-78,91-110`
- Test: `backend/src/test/java/ch/junto/participant/ParticipantServiceTest.java`

- [ ] **Step 1: Add findByIdWithLock to ActivityRepository**

Add to `ActivityRepository.java`:

```java
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
```

And the method:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT a FROM Activity a WHERE a.id = :id")
Optional<Activity> findByIdWithLock(@Param("id") UUID id);
```

- [ ] **Step 2: Switch join() to use findByIdWithLock**

In `ParticipantService.join()`, change:

```java
Activity activity = activityRepository.findById(activityId)
    .orElseThrow(() -> new NotFoundException("Activity not found"));
```

To:

```java
Activity activity = activityRepository.findByIdWithLock(activityId)
    .orElseThrow(() -> new NotFoundException("Activity not found"));
```

- [ ] **Step 3: Switch approve() to use findByIdWithLock**

In `ParticipantService.approve()`, after the participant lookup, add a lock on the activity:

```java
public void approve(UUID participantId, User creator) {
    ActivityParticipant participant = participantRepository.findById(participantId)
        .orElseThrow(() -> new NotFoundException("Participant not found"));

    if (!participant.getActivity().getCreator().getId().equals(creator.getId())) {
        throw new ForbiddenException("Only creator can approve participants");
    }

    if (participant.getStatus() != ParticipantStatus.PENDING) {
        throw new ConflictException("Participant is not pending");
    }

    // Lock the activity row before capacity check
    Activity activity = activityRepository.findByIdWithLock(participant.getActivity().getId())
        .orElseThrow(() -> new NotFoundException("Activity not found"));

    long count = participantRepository.countByActivityIdAndStatus(activity.getId(), ParticipantStatus.APPROVED);
    if (count >= activity.getMaxParticipants()) {
        throw new ConflictException("Activity is full");
    }

    participant.setStatus(ParticipantStatus.APPROVED);
    participantRepository.save(participant);
}
```

- [ ] **Step 4: Update unit tests to mock findByIdWithLock instead of findById**

In `ParticipantServiceTest.java`, replace all `when(activityRepository.findById(activity.getId()))` calls in join tests with `when(activityRepository.findByIdWithLock(activity.getId()))`. The affected tests are:

- `join_openActivity_approvesImmediately`
- `join_approvalRequired_setsToPending`
- `join_afterDeadline_throwsException`
- `join_alreadyParticipant_throwsConflict`
- `join_activityFull_throwsConflict`
- `join_inactiveActivity_throwsConflict`

For `join_activityNotFound_throwsNotFoundException`, change:
```java
when(activityRepository.findById(id)).thenReturn(Optional.empty());
```
To:
```java
when(activityRepository.findByIdWithLock(id)).thenReturn(Optional.empty());
```

Note: Leave tests still use `findById` since leave() does not need the pessimistic lock (no capacity check).

- [ ] **Step 5: Run all participant tests**

Run: `cd backend && ./gradlew test --tests "ch.junto.participant.ParticipantServiceTest" -i`

Expected: All PASS.

- [ ] **Step 6: Commit**

```bash
cd backend && git add src/main/java/ch/junto/activity/ActivityRepository.java src/main/java/ch/junto/participant/ParticipantService.java src/test/java/ch/junto/participant/ParticipantServiceTest.java && git commit -m "feat: pessimistic lock on activity row for join and approve capacity checks"
```

---

### Task 7: Concurrent Joins Integration Test

**Files:**
- Create: `backend/src/test/java/ch/junto/activity/ActivityConcurrencyTest.java`

This test needs its own class because `BaseIntegrationTest` is `@Transactional` (auto-rollback), which forces all threads into a single DB transaction — defeating the concurrency test. This class extends `TestContainerConfig` directly and does manual cleanup.

- [ ] **Step 1: Create the concurrency test class**

Create `backend/src/test/java/ch/junto/activity/ActivityConcurrencyTest.java`:

```java
package ch.junto.activity;

import ch.junto.activity.entity.Activity;
import ch.junto.activity.enums.ActivityStatus;
import ch.junto.activity.enums.JoinPolicy;
import ch.junto.activity.entity.CustomFields;
import ch.junto.base.TestContainerConfig;
import ch.junto.participant.ParticipantRepository;
import ch.junto.participant.enums.ParticipantStatus;
import ch.junto.user.UserRepository;
import ch.junto.user.entity.User;
import ch.junto.user.enums.UserRole;
import ch.junto.user.enums.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Activity Concurrency")
class ActivityConcurrencyTest extends TestContainerConfig {

    @Autowired private MockMvc mockMvc;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ParticipantRepository participantRepository;

    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<UUID> createdActivityIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        createdActivityIds.forEach(id -> {
            participantRepository.deleteAll(participantRepository.findAllByActivityId(id));
            activityRepository.deleteById(id);
        });
        createdUserIds.forEach(userRepository::deleteById);
    }

    private User createAndTrackUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName(email.split("@")[0]);
        user.setRole(UserRole.MEMBER);
        user.setStatus(UserStatus.ACTIVE);
        user.setOnboarded(true);
        user.setInviteCodesRemaining(5);
        user = userRepository.save(user);
        createdUserIds.add(user.getId());
        return user;
    }

    private Activity createAndTrackActivity(User creator, int maxParticipants) {
        Activity activity = new Activity();
        activity.setCreator(creator);
        activity.setTypeSlug("climbing");
        activity.setTitle("Concurrency Test");
        activity.setDescription("Test");
        activity.setCity("Zurich");
        activity.setStartsAt(OffsetDateTime.now().plusDays(1));
        activity.setMaxParticipants(maxParticipants);
        activity.setJoinPolicy(JoinPolicy.OPEN);
        activity.setStatus(ActivityStatus.ACTIVE);
        CustomFields fields = new CustomFields();
        fields.put("style", "Bouldering");
        fields.put("environment", "Indoor");
        fields.put("experienceLevel", "Beginner");
        activity.setCustomFields(fields);
        activity = activityRepository.save(activity);
        createdActivityIds.add(activity.getId());
        return activity;
    }

    private MockHttpSession buildSession(User user) {
        MockHttpSession session = new MockHttpSession();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    @Test @DisplayName("concurrent joins should not exceed maxParticipants")
    void concurrentJoinsShouldRespectCapacity() throws Exception {
        User creator = createAndTrackUser("creator-" + UUID.randomUUID() + "@junto.app");
        // maxParticipants=3, creator takes 1 spot via addCreator in service,
        // but we're creating directly here so no creator participant — set to 2 for clean math
        Activity activity = createAndTrackActivity(creator, 2);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            User joiner = createAndTrackUser("joiner-" + i + "-" + UUID.randomUUID() + "@junto.app");
            MockHttpSession session = buildSession(joiner);
            UUID activityId = activity.getId();

            futures.add(executor.submit(() -> {
                ready.countDown();
                go.await();
                return mockMvc.perform(post("/api/activities/" + activityId + "/participants/join")
                        .session(session))
                    .andReturn().getResponse().getStatus();
            }));
        }

        ready.await();
        go.countDown();

        List<Integer> statuses = futures.stream().map(f -> {
            try { return f.get(10, TimeUnit.SECONDS); }
            catch (Exception e) { throw new RuntimeException(e); }
        }).toList();
        executor.shutdown();

        long successes = statuses.stream().filter(s -> s == 200).count();
        long conflicts = statuses.stream().filter(s -> s == 409).count();

        assertThat(successes).isEqualTo(2);
        assertThat(conflicts).isEqualTo(8);

        // Double-check at the DB level
        long approvedCount = participantRepository.countByActivityIdAndStatus(activity.getId(), ParticipantStatus.APPROVED);
        assertThat(approvedCount).isEqualTo(2);
    }
}
```

- [ ] **Step 2: Run the test**

Run: `cd backend && ./gradlew test --tests "ch.junto.activity.ActivityConcurrencyTest" -i`

Expected: PASS — pessimistic lock serializes the joins, exactly 2 succeed.

- [ ] **Step 3: Commit**

```bash
cd backend && git add src/test/java/ch/junto/activity/ActivityConcurrencyTest.java && git commit -m "test: add concurrent join capacity integration test"
```

---

### Task 8: Date Validation

**Files:**
- Modify: `backend/src/main/java/ch/junto/activity/ActivityService.java:54-79,88-112`
- Test: `backend/src/test/java/ch/junto/activity/ActivityIntegrationTest.java`

- [ ] **Step 1: Write failing integration tests for create date validation**

Add to `ActivityIntegrationTest.java`:

```java
@Test @DisplayName("should reject creation with past startsAt")
void shouldRejectCreateWithPastStartsAt() throws Exception {
    ActivityCreateRequest request = new ActivityCreateRequest(
        "Past Event", "Desc", "climbing", "Zurich", null, null,
        OffsetDateTime.now().minusDays(1), null, 10, JoinPolicy.OPEN, null
    );

    mockMvc.perform(post("/api/activities")
            .session(buildSession(testUser))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(request)))
        .andExpect(status().isBadRequest());
}

@Test @DisplayName("should reject creation with signupDeadline after startsAt")
void shouldRejectCreateWithDeadlineAfterStart() throws Exception {
    OffsetDateTime startsAt = OffsetDateTime.now().plusDays(1);
    OffsetDateTime deadline = startsAt.plusHours(1);

    ActivityCreateRequest request = new ActivityCreateRequest(
        "Bad Deadline", "Desc", "climbing", "Zurich", null, null,
        startsAt, deadline, 10, JoinPolicy.OPEN, null
    );

    mockMvc.perform(post("/api/activities")
            .session(buildSession(testUser))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(request)))
        .andExpect(status().isBadRequest());
}

@Test @DisplayName("should reject creation with signupDeadline equal to startsAt")
void shouldRejectCreateWithDeadlineEqualToStart() throws Exception {
    OffsetDateTime startsAt = OffsetDateTime.now().plusDays(1);

    ActivityCreateRequest request = new ActivityCreateRequest(
        "Equal Deadline", "Desc", "climbing", "Zurich", null, null,
        startsAt, startsAt, 10, JoinPolicy.OPEN, null
    );

    mockMvc.perform(post("/api/activities")
            .session(buildSession(testUser))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(request)))
        .andExpect(status().isBadRequest());
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./gradlew test --tests "ch.junto.activity.ActivityIntegrationTest.shouldRejectCreateWithPastStartsAt" --tests "ch.junto.activity.ActivityIntegrationTest.shouldRejectCreateWithDeadlineAfterStart" --tests "ch.junto.activity.ActivityIntegrationTest.shouldRejectCreateWithDeadlineEqualToStart" -i`

Expected: All 3 FAIL — no date validation exists.

- [ ] **Step 3: Add date validation to create()**

In `ActivityService.create()`, add before the `Activity activity = new Activity()` line:

```java
if (request.startsAt().isBefore(OffsetDateTime.now())) {
    throw new ApiException("VALIDATION_ERROR", "startsAt must be in the future", 400);
}
if (request.signupDeadline() != null && !request.signupDeadline().isBefore(request.startsAt())) {
    throw new ApiException("VALIDATION_ERROR", "signupDeadline must be before startsAt", 400);
}
```

- [ ] **Step 4: Run create validation tests**

Run: `cd backend && ./gradlew test --tests "ch.junto.activity.ActivityIntegrationTest.shouldRejectCreateWithPastStartsAt" --tests "ch.junto.activity.ActivityIntegrationTest.shouldRejectCreateWithDeadlineAfterStart" --tests "ch.junto.activity.ActivityIntegrationTest.shouldRejectCreateWithDeadlineEqualToStart" --tests "ch.junto.activity.ActivityIntegrationTest.shouldCreateActivity" -i`

Expected: All 4 PASS.

- [ ] **Step 5: Write failing integration tests for update date validation**

Add to `ActivityIntegrationTest.java`:

```java
@Test @DisplayName("should reject update with signupDeadline after startsAt")
void shouldRejectUpdateWithDeadlineAfterStart() throws Exception {
    Activity activity = dbHelper.save(ActivityFixture.newActivity(testUser, "climbing"));
    OffsetDateTime startsAt = OffsetDateTime.now().plusDays(3);
    OffsetDateTime deadline = startsAt.plusHours(1);

    ActivityUpdateRequest request = new ActivityUpdateRequest(
        "Title", "Desc", "Zurich", null, null,
        startsAt, deadline, null
    );

    mockMvc.perform(put("/api/activities/" + activity.getId())
            .session(buildSession(testUser))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(request)))
        .andExpect(status().isBadRequest());
}

@Test @DisplayName("should reject update moving future startsAt to past")
void shouldRejectUpdateFutureStartsAtToPast() throws Exception {
    Activity activity = dbHelper.save(ActivityFixture.newActivity(testUser, "climbing"));
    // activity.startsAt is now+1day (future)

    ActivityUpdateRequest request = new ActivityUpdateRequest(
        "Title", "Desc", "Zurich", null, null,
        OffsetDateTime.now().minusDays(1), null, null
    );

    mockMvc.perform(put("/api/activities/" + activity.getId())
            .session(buildSession(testUser))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(request)))
        .andExpect(status().isBadRequest());
}

@Test @DisplayName("should allow update on already-past activity with past startsAt")
void shouldAllowUpdatePastStartsAtOnPastActivity() throws Exception {
    Activity activity = dbHelper.save(ActivityFixture.newActivity(testUser, "climbing"));
    // Manually set startsAt to the past to simulate an already-past activity
    activity.setStartsAt(OffsetDateTime.now().minusDays(2));
    activityRepository.save(activity);

    ActivityUpdateRequest request = new ActivityUpdateRequest(
        "Corrected Title", "Desc", "Zurich", null, null,
        OffsetDateTime.now().minusDays(1), null, null
    );

    mockMvc.perform(put("/api/activities/" + activity.getId())
            .session(buildSession(testUser))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Corrected Title"));
}
```

- [ ] **Step 6: Add date validation to update()**

In `ActivityService.update()`, add after the status guard (after the COMPLETED check):

```java
if (request.signupDeadline() != null && !request.signupDeadline().isBefore(request.startsAt())) {
    throw new ApiException("VALIDATION_ERROR", "signupDeadline must be before startsAt", 400);
}
if (activity.getStartsAt().isAfter(OffsetDateTime.now())
        && request.startsAt().isBefore(OffsetDateTime.now())) {
    throw new ApiException("VALIDATION_ERROR", "startsAt must be in the future", 400);
}
```

- [ ] **Step 7: Run all update validation tests**

Run: `cd backend && ./gradlew test --tests "ch.junto.activity.ActivityIntegrationTest.shouldRejectUpdateWithDeadlineAfterStart" --tests "ch.junto.activity.ActivityIntegrationTest.shouldRejectUpdateFutureStartsAtToPast" --tests "ch.junto.activity.ActivityIntegrationTest.shouldAllowUpdatePastStartsAtOnPastActivity" --tests "ch.junto.activity.ActivityIntegrationTest.shouldUpdateActivity" -i`

Expected: All 4 PASS.

- [ ] **Step 8: Commit**

```bash
cd backend && git add src/main/java/ch/junto/activity/ActivityService.java src/test/java/ch/junto/activity/ActivityIntegrationTest.java && git commit -m "feat: add date validation for create and update"
```

---

### Task 9: maxParticipants and joinPolicy Updatable

**Files:**
- Modify: `backend/src/main/java/ch/junto/activity/dto/ActivityUpdateRequest.java`
- Modify: `backend/src/main/java/ch/junto/activity/ActivityService.java:88-112`
- Test: `backend/src/test/java/ch/junto/activity/ActivityIntegrationTest.java`

- [ ] **Step 1: Write failing integration test for updating maxParticipants and joinPolicy**

Add to `ActivityIntegrationTest.java` (needs new import: `import ch.junto.activity.enums.JoinPolicy;` — already imported):

```java
@Test @DisplayName("should update maxParticipants and joinPolicy")
void shouldUpdateMaxParticipantsAndJoinPolicy() throws Exception {
    Activity activity = dbHelper.save(ActivityFixture.newActivity(testUser, "climbing"));

    ActivityUpdateRequest request = new ActivityUpdateRequest(
        "Title", "Desc", "Zurich", null, null,
        OffsetDateTime.now().plusDays(3), null,
        20, JoinPolicy.APPROVAL_REQUIRED, null
    );

    mockMvc.perform(put("/api/activities/" + activity.getId())
            .session(buildSession(testUser))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(request)))
        .andExpect(status().isOk());

    Activity updated = activityRepository.findById(activity.getId()).orElseThrow();
    assertThat(updated.getMaxParticipants()).isEqualTo(20);
    assertThat(updated.getJoinPolicy()).isEqualTo(JoinPolicy.APPROVAL_REQUIRED);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "ch.junto.activity.ActivityIntegrationTest.shouldUpdateMaxParticipantsAndJoinPolicy" -i`

Expected: FAIL — `ActivityUpdateRequest` constructor doesn't accept these fields yet.

- [ ] **Step 3: Add fields to ActivityUpdateRequest**

Update `ActivityUpdateRequest.java`:

```java
package ch.junto.activity.dto;

import ch.junto.activity.entity.CustomFields;
import ch.junto.activity.enums.JoinPolicy;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;

public record ActivityUpdateRequest(
    @NotBlank String title,
    String description,
    String city,
    Double locationLat,
    Double locationLng,
    OffsetDateTime startsAt,
    OffsetDateTime signupDeadline,
    @Min(1) Integer maxParticipants,
    JoinPolicy joinPolicy,
    CustomFields customFields
) {}
```

- [ ] **Step 4: Update all existing ActivityUpdateRequest usages in tests**

Every test that constructs `ActivityUpdateRequest` with 8 args must now pass 10 args. Update:

`shouldUpdateActivity`:
```java
ActivityUpdateRequest request = new ActivityUpdateRequest(
    "Updated Title", "Updated Desc", "Geneva", null, null,
    OffsetDateTime.now().plusDays(3), null, null, null, null
);
```

`shouldForbidUpdateByNonCreator`:
```java
ActivityUpdateRequest request = new ActivityUpdateRequest(
    "Hacked Title", "Desc", "City", null, null,
    OffsetDateTime.now().plusDays(1), null, null, null, null
);
```

`shouldRejectUpdateOnCompleted`:
```java
ActivityUpdateRequest request = new ActivityUpdateRequest(
    "New Title", "Desc", "Zurich", null, null,
    OffsetDateTime.now().plusDays(3), null, null, null, null
);
```

`shouldAllowUpdateOnCancelled`:
```java
ActivityUpdateRequest request = new ActivityUpdateRequest(
    "Fixed Title", "Desc", "Zurich", null, null,
    OffsetDateTime.now().plusDays(3), null, null, null, null
);
```

`shouldRejectUpdateWithDeadlineAfterStart`:
```java
ActivityUpdateRequest request = new ActivityUpdateRequest(
    "Title", "Desc", "Zurich", null, null,
    startsAt, deadline, null, null, null
);
```

`shouldRejectUpdateFutureStartsAtToPast`:
```java
ActivityUpdateRequest request = new ActivityUpdateRequest(
    "Title", "Desc", "Zurich", null, null,
    OffsetDateTime.now().minusDays(1), null, null, null, null
);
```

`shouldAllowUpdatePastStartsAtOnPastActivity`:
```java
ActivityUpdateRequest request = new ActivityUpdateRequest(
    "Corrected Title", "Desc", "Zurich", null, null,
    OffsetDateTime.now().minusDays(1), null, null, null, null
);
```

- [ ] **Step 5: Apply maxParticipants and joinPolicy in ActivityService.update()**

In `ActivityService.update()`, add after `activity.setSignupDeadline(request.signupDeadline());`:

```java
if (request.maxParticipants() != null) {
    activity.setMaxParticipants(request.maxParticipants());
}
if (request.joinPolicy() != null) {
    activity.setJoinPolicy(request.joinPolicy());
}
```

- [ ] **Step 6: Run all update tests**

Run: `cd backend && ./gradlew test --tests "ch.junto.activity.ActivityIntegrationTest.shouldUpdateMaxParticipantsAndJoinPolicy" --tests "ch.junto.activity.ActivityIntegrationTest.shouldUpdateActivity" --tests "ch.junto.activity.ActivityIntegrationTest.shouldForbidUpdateByNonCreator" --tests "ch.junto.activity.ActivityIntegrationTest.shouldRejectUpdateOnCompleted" --tests "ch.junto.activity.ActivityIntegrationTest.shouldAllowUpdateOnCancelled" -i`

Expected: All 5 PASS.

- [ ] **Step 7: Commit**

```bash
cd backend && git add src/main/java/ch/junto/activity/dto/ActivityUpdateRequest.java src/main/java/ch/junto/activity/ActivityService.java src/test/java/ch/junto/activity/ActivityIntegrationTest.java && git commit -m "feat: make maxParticipants and joinPolicy updatable"
```

---

### Task 10: Flyway Migration

**Files:**
- Create: `backend/src/main/resources/db/migration/V017__activity_lifecycle_constraints.sql`

- [ ] **Step 1: Create migration file**

Create `backend/src/main/resources/db/migration/V017__activity_lifecycle_constraints.sql`:

```sql
-- Activity lifecycle constraints
-- Spec: docs/superpowers/specs/2026-04-25-activity-lifecycle-design.md, Section 4

-- CHECK constraint: signup_deadline must precede starts_at when set.
-- NOT VALID avoids scanning existing rows — safe if app already enforces this,
-- and prevents migration failure if any legacy rows violate the constraint.
ALTER TABLE activities
  ADD CONSTRAINT chk_signup_before_start
  CHECK (signup_deadline IS NULL OR signup_deadline < starts_at)
  NOT VALID;

-- Index on status to support autoCompleteStartedActivities scans.
CREATE INDEX IF NOT EXISTS idx_activities_status ON activities (status);
```

- [ ] **Step 2: Run the application or tests to verify migration applies**

Run: `cd backend && ./gradlew test --tests "ch.junto.activity.ActivityIntegrationTest.shouldCreateActivity" -i`

Expected: PASS — migration applies cleanly, existing test still works.

- [ ] **Step 3: Commit**

```bash
cd backend && git add src/main/resources/db/migration/V017__activity_lifecycle_constraints.sql && git commit -m "feat: add CHECK constraint and status index migration"
```

---

### Task 11: Reactivate-then-Join Integration Test

**Files:**
- Test: `backend/src/test/java/ch/junto/activity/ActivityIntegrationTest.java`

- [ ] **Step 1: Write integration test for reactivate-then-join flow**

Add to `ActivityIntegrationTest.java`:

```java
@Test @DisplayName("should allow join after reactivation")
void shouldAllowJoinAfterReactivation() throws Exception {
    Activity activity = dbHelper.save(ActivityFixture.newActivity(testUser, "climbing"));

    // Cancel
    mockMvc.perform(post("/api/activities/" + activity.getId() + "/cancel")
            .session(buildSession(testUser)))
        .andExpect(status().isOk());

    // Reactivate
    mockMvc.perform(post("/api/activities/" + activity.getId() + "/reactivate")
            .session(buildSession(testUser)))
        .andExpect(status().isOk());

    // New user joins
    User joiner = dbHelper.save(UserFixture.newUser("joiner-" + UUID.randomUUID() + "@junto.app"));
    mockMvc.perform(post("/api/activities/" + activity.getId() + "/participants/join")
            .session(buildSession(joiner)))
        .andExpect(status().isOk());
}
```

- [ ] **Step 2: Run test**

Run: `cd backend && ./gradlew test --tests "ch.junto.activity.ActivityIntegrationTest.shouldAllowJoinAfterReactivation" -i`

Expected: PASS — this is a smoke test confirming the full flow works end-to-end.

- [ ] **Step 3: Commit**

```bash
cd backend && git add src/test/java/ch/junto/activity/ActivityIntegrationTest.java && git commit -m "test: add reactivate-then-join integration test"
```

---

### Task 12: Full Test Suite Verification

- [ ] **Step 1: Run all tests**

Run: `cd backend && ./gradlew test -i`

Expected: All tests PASS. No regressions.

- [ ] **Step 2: If any failures, fix them before proceeding**

Diagnose and fix. Do not skip.

- [ ] **Step 3: Final commit if any fixes were needed**

Only if fixes were required in Step 2.
