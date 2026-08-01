# Activity Lifecycle Design

## Problem

The activity and participant systems have several unhandled edge cases and missing guards:

1. No status transition guards — `cancel()` and `complete()` can be called on already-terminal activities
2. Completed activities can be edited
3. No reactivation path for cancelled activities
4. `ParticipantStatus.CANCELLED` exists in the enum but is never set (dead code)
5. Capacity check has a race condition — concurrent joins can exceed `maxParticipants`
6. No date validation — `signupDeadline` can be after `startsAt`; `startsAt` can be in the past at creation
7. `leave()` has no timing cutoff — participants can leave after the signup deadline
8. `maxParticipants` and `joinPolicy` are not updatable after creation

---

## Status Graph

```
ACTIVE ──cancel──→ CANCELLED ──reactivate──→ ACTIVE
ACTIVE ──complete──→ COMPLETED  (one-way, cannot be undone)
```

Cancellation has no cascade effect on participants. The activity's status is the signal.
No participant-level status change occurs when an activity is cancelled.

---

## Section 1: Activity Status Transition Guards

Every status-changing operation enforces which source status is valid:

| Operation | Allowed from | Error if not |
|-----------|-------------|--------------|
| `cancel` | `ACTIVE` only | 409 "Activity is not active" |
| `complete` | `ACTIVE` only | 409 "Activity is not active" |
| `reactivate` | `CANCELLED` only | 409 "Activity is not cancelled" |
| `update` | `ACTIVE` or `CANCELLED` | 409 "Completed activities cannot be edited" |

### New Endpoint

`POST /api/activities/{id}/reactivate`

- Creator only (same auth pattern as `/cancel` and `/complete`)
- Sets status from `CANCELLED` → `ACTIVE`
- Returns 403 if caller is not the creator
- Returns 404 if activity not found
- Returns 409 if activity is not `CANCELLED`
- Returns 200 on success (no body)

---

## Section 2: Participant Lifecycle

### Dead Code Removal

`ParticipantStatus.CANCELLED` is removed from the enum. It was never set anywhere in the codebase. The column uses `@Enumerated(EnumType.STRING)` (VARCHAR storage), so removing the value from the Java enum requires no DB migration — the value was simply never written to the database.

### Leave Cutoff

`leave()` gains a deadline check using the same effective deadline logic as `join()`:

```
effectiveDeadline = signupDeadline ?? startsAt
if now > effectiveDeadline → 409 "Signup deadline has passed"
```

Participants can no longer leave after the signup deadline has passed.

### Capacity Race Condition Fix

`join()` and `approve()` acquire a pessimistic write lock on the activity row before performing the capacity check. This serializes concurrent requests at the DB level:

```java
// ActivityRepository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT a FROM Activity a WHERE a.id = :id")
Optional<Activity> findByIdWithLock(@Param("id") UUID id);
```

`ParticipantService.join()` and `ParticipantService.approve()` call `findByIdWithLock` instead of `findById`.

### Everything Else Unchanged

- Declined participants remain blocked from re-joining (current behavior is correct)
- `leave()` continues to hard-delete the participant record
- `OPEN` policy → immediate APPROVED; `APPROVAL_REQUIRED` → PENDING queue
- No cascade when an activity is cancelled — PENDING and APPROVED participants are untouched

---

## Section 3: Validation

### Date Validation

Validated in the service layer (cross-field logic requires service-level checks, not just Bean Validation annotations).

**On create:**
- `startsAt` must be in the future → 400 "startsAt must be in the future"
- `signupDeadline`, if set, must be strictly before `startsAt` → 400 "signupDeadline must be before startsAt"

**On update:**
- `startsAt` future check is skipped only if the activity's existing `startsAt` is already in the past (creator correcting a mistake). If the existing `startsAt` is in the future, the new value must also be in the future → 400 "startsAt must be in the future"
- `signupDeadline`, if set, must be strictly before `startsAt` → 400 "signupDeadline must be before startsAt"

```java
if (request.signupDeadline() != null && !request.signupDeadline().isBefore(request.startsAt())) {
    throw new ApiException("VALIDATION_ERROR", "signupDeadline must be before startsAt", 400);
}
// create:
if (request.startsAt().isBefore(OffsetDateTime.now())) {
    throw new ApiException("VALIDATION_ERROR", "startsAt must be in the future", 400);
}
// update — only enforce future check when existing startsAt is still in the future:
if (existingActivity.getStartsAt().isAfter(OffsetDateTime.now())
        && request.startsAt().isBefore(OffsetDateTime.now())) {
    throw new ApiException("VALIDATION_ERROR", "startsAt must be in the future", 400);
}
```

A DB `CHECK` constraint is added as a safety net (see Section 4).

### `maxParticipants` and `joinPolicy` Now Updatable

`ActivityUpdateRequest` gains two new fields:

```java
public record ActivityUpdateRequest(
    @NotBlank String title,
    String description,
    String city,
    Double locationLat,
    Double locationLng,
    OffsetDateTime startsAt,
    OffsetDateTime signupDeadline,
    @Min(1) Integer maxParticipants,   // added
    JoinPolicy joinPolicy,             // added
    CustomFields customFields
)
```

`ActivityService.update()` applies both fields when present. If `maxParticipants` is reduced below the current approved participant count, the update is accepted — existing approved participants are not removed. The capacity check on new joins will simply block until spots open.

---

## Section 4: DB Changes

Single Flyway migration (next available version):

```sql
-- 1. CHECK constraint: signup_deadline must precede starts_at when set
-- Uses NOT VALID to avoid scanning existing rows (safe if app already enforces this,
-- and avoids migration failure if any legacy rows violate the constraint).
-- A separate VALIDATE CONSTRAINT can be run later to fully validate.
ALTER TABLE activities
  ADD CONSTRAINT chk_signup_before_start
  CHECK (signup_deadline IS NULL OR signup_deadline < starts_at)
  NOT VALID;

-- 2. Index on status to support autoCompleteStartedActivities scans
CREATE INDEX IF NOT EXISTS idx_activities_status ON activities (status);
```

Note: No migration needed for `ParticipantStatus.CANCELLED` — the value was never written to the database (VARCHAR storage via `@Enumerated(EnumType.STRING)`).

---

## Section 5: API Surface Changes

| Endpoint | Change |
|----------|--------|
| `POST /activities/{id}/reactivate` | New endpoint |
| `PUT /activities/{id}` | Accepts `maxParticipants` and `joinPolicy`; rejects if status is COMPLETED |
| `POST /activities/{id}/cancel` | Returns 409 if not ACTIVE |
| `POST /activities/{id}/complete` | Returns 409 if not ACTIVE |
| `POST /activities/{activityId}/participants/leave` | Returns 409 if past effective deadline |
| `POST /activities/{activityId}/participants/join` | Uses `findByIdWithLock` (pessimistic lock) |
| `POST /activities/{activityId}/participants/{id}/approve` | Uses `findByIdWithLock` (pessimistic lock) |

---

## Section 6: Test Plan

| Test | Type | Coverage |
|------|------|----------|
| Cancel ACTIVE → CANCELLED | Integration | Happy path |
| Cancel CANCELLED → 409 | Integration | Guard |
| Cancel COMPLETED → 409 | Integration | Guard |
| Complete ACTIVE → COMPLETED | Integration | Happy path |
| Complete CANCELLED → 409 | Integration | Guard |
| Reactivate CANCELLED → ACTIVE | Integration | Happy path |
| Reactivate ACTIVE → 409 | Integration | Guard |
| Reactivate COMPLETED → 409 | Integration | Guard |
| Update ACTIVE → 200 | Integration | Happy path |
| Update CANCELLED → 200 | Integration | Allowed |
| Update COMPLETED → 409 | Integration | Guard |
| Create with past `startsAt` → 400 | Integration | Date validation |
| Create with `signupDeadline` after `startsAt` → 400 | Integration | Date validation |
| Update with `signupDeadline` after `startsAt` → 400 | Integration | Date validation |
| Leave before deadline → 200 | Integration | Happy path |
| Leave after deadline → 409 | Integration | Leave cutoff |
| Concurrent joins at capacity | Integration | Race condition (pessimistic lock) |
| Update `maxParticipants` and `joinPolicy` | Integration | New update fields |
| Reactivate then join → 200 | Integration | Post-reactivation participant flow |
| Create with `signupDeadline` == `startsAt` → 400 | Integration | Boundary (strictly-before) |
| Update `startsAt` to past on future activity → 400 | Integration | Conditional future check |
| Update `startsAt` to past on already-past activity → 200 | Integration | Correction allowed |
| `ParticipantStatus.CANCELLED` not in enum | Unit | Dead code removed |
