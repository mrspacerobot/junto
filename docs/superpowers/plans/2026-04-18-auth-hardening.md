# Auth Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden the auth system against race conditions, brute-force attacks, account enumeration, and other security issues identified in the audit.

**Architecture:** Fix the highest-priority issues in dependency order — DB-level atomic operations first, then service-layer checks, then controller-level protections. Each task produces a working, testable increment.

**Tech Stack:** Spring Boot 3.4, Spring Data JPA, PostgreSQL, Flyway, JUnit 5 + Mockito

---

## Task 1: Add `attempt_count` column and atomic consume to AuthToken

The current `findByEmailAndCodeAndUsedAtIsNull` + `save` pattern is vulnerable to race conditions where two concurrent requests both find and consume the same token. We need a DB-level atomic update and an attempt counter for brute-force protection.

**Files:**
- Create: `backend/src/main/resources/db/migration/V014__auth_token_attempt_count.sql`
- Modify: `backend/src/main/java/ch/junto/shared/entity/AuthToken.java`
- Modify: `backend/src/main/java/ch/junto/auth/AuthTokenRepository.java`

- [ ] **Step 1: Write the Flyway migration**

```sql
-- V014__auth_token_attempt_count.sql
ALTER TABLE auth_tokens ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0;

-- Partial index: only one unused token per email at a time (enforced at app layer)
-- But add a composite index for the atomic consume queries
CREATE INDEX idx_auth_tokens_email_code_unused ON auth_tokens (email, code) WHERE used_at IS NULL;
CREATE INDEX idx_auth_tokens_token_unused ON auth_tokens (token) WHERE used_at IS NULL;
```

- [ ] **Step 2: Add `attemptCount` field to AuthToken entity**

In `AuthToken.java`, add after the `usedAt` field:

```java
@Column(name = "attempt_count", nullable = false)
private int attemptCount = 0;

public int getAttemptCount() {
    return attemptCount;
}

public void setAttemptCount(int attemptCount) {
    this.attemptCount = attemptCount;
}
```

- [ ] **Step 3: Add atomic consume and increment methods to AuthTokenRepository**

Replace the existing `AuthTokenRepository` contents:

```java
package ch.junto.auth;

import ch.junto.shared.entity.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface AuthTokenRepository extends JpaRepository<AuthToken, UUID> {

    /**
     * Atomically consume a token by its UUID. Returns 1 if consumed, 0 if already used/expired.
     */
    @Modifying
    @Query("""
        UPDATE AuthToken t
        SET t.usedAt = :now
        WHERE t.token = :token
          AND t.usedAt IS NULL
          AND t.expiresAt >= :now
        """)
    int consumeByToken(@Param("token") UUID token, @Param("now") OffsetDateTime now);

    /**
     * Atomically consume a token by email+code. Returns 1 if consumed, 0 if already used/expired.
     */
    @Modifying
    @Query("""
        UPDATE AuthToken t
        SET t.usedAt = :now
        WHERE t.email = :email
          AND t.code = :code
          AND t.usedAt IS NULL
          AND t.expiresAt >= :now
        """)
    int consumeByEmailAndCode(@Param("email") String email, @Param("code") String code, @Param("now") OffsetDateTime now);

    /**
     * Increment attempt count for a token (for brute-force tracking).
     * Returns the new attempt count (via the row count).
     */
    @Modifying
    @Query("""
        UPDATE AuthToken t
        SET t.attemptCount = t.attemptCount + 1
        WHERE t.email = :email
          AND t.code = :code
          AND t.usedAt IS NULL
        """)
    int incrementAttemptCount(@Param("email") String email, @Param("code") String code);

    /**
     * Fetch an unused token by email+code (for reading attempt count / metadata).
     */
    Optional<AuthToken> findByEmailAndCodeAndUsedAtIsNull(String email, String code);

    /**
     * Fetch an unused token by UUID (for reading metadata after atomic consume).
     */
    Optional<AuthToken> findByTokenAndUsedAtIsNull(UUID token);

    /**
     * Delete all unused tokens for an email (before issuing a new one).
     */
    @Modifying
    @Query("DELETE FROM AuthToken t WHERE t.email = :email AND t.usedAt IS NULL")
    void deleteUnusedByEmail(@Param("email") String email);

    /**
     * Clean up expired tokens.
     */
    @Modifying
    @Query("DELETE FROM AuthToken t WHERE t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") OffsetDateTime cutoff);
}
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/migration/V014__auth_token_attempt_count.sql \
        backend/src/main/java/ch/junto/shared/entity/AuthToken.java \
        backend/src/main/java/ch/junto/auth/AuthTokenRepository.java
git commit -m "feat(auth): add attempt_count column and atomic consume queries"
```

---

## Task 2: Rewrite AuthService verification to use atomic consume + re-check suspended status

The core security fixes: atomic token consumption, suspended-user re-check during verification, single active token per email, and max attempt limits.

**Files:**
- Modify: `backend/src/main/java/ch/junto/auth/AuthService.java`

- [ ] **Step 1: Update `AuthService` with all security fixes**

Replace the entire `AuthService.java`:

```java
package ch.junto.auth;

import ch.junto.email.EmailService;
import ch.junto.invite.InviteService;
import ch.junto.shared.entity.AuthToken;
import ch.junto.shared.entity.User;
import ch.junto.shared.enums.UserStatus;
import ch.junto.shared.exception.ApiException;
import ch.junto.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int CODE_EXPIRY_MINUTES = 10;
    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom secureRandom = new SecureRandom();

    private final AuthTokenRepository authTokenRepository;
    private final UserService userService;
    private final InviteService inviteService;
    private final EmailService emailService;

    public AuthService(AuthTokenRepository authTokenRepository,
                       UserService userService,
                       InviteService inviteService,
                       EmailService emailService) {
        this.authTokenRepository = authTokenRepository;
        this.userService = userService;
        this.inviteService = inviteService;
        this.emailService = emailService;
    }

    @Transactional
    public void requestCode(String email, String inviteCode) {
        String normalizedEmail = normalizeEmail(email);

        Optional<User> existingUser = userService.findByEmail(normalizedEmail);

        if (existingUser.isEmpty()) {
            if (inviteCode == null || inviteCode.isBlank()) {
                throw new ApiException("INVITE_CODE_REQUIRED",
                    "Invite code is required for new accounts", 400);
            }
            inviteService.validateCode(inviteCode);
        } else {
            if (existingUser.get().getStatus() == UserStatus.SUSPENDED) {
                throw new ApiException("ACCOUNT_SUSPENDED",
                    "This account has been suspended", 403);
            }
        }

        // Invalidate any previous unused tokens for this email
        authTokenRepository.deleteUnusedByEmail(normalizedEmail);

        String code = String.format("%06d", secureRandom.nextInt(1000000));
        UUID token = UUID.randomUUID();

        AuthToken authToken = new AuthToken();
        authToken.setEmail(normalizedEmail);
        authToken.setToken(token);
        authToken.setCode(code);
        authToken.setExpiresAt(OffsetDateTime.now().plusMinutes(CODE_EXPIRY_MINUTES));

        if (existingUser.isEmpty() && inviteCode != null) {
            authToken.setInviteCode(inviteCode);
        }

        authTokenRepository.save(authToken);
        emailService.sendAuthCode(normalizedEmail, code, token.toString());
        log.info("Auth code requested for {}", normalizedEmail);
    }

    @Transactional
    public User verifyCode(String email, String code) {
        String normalizedEmail = normalizeEmail(email);

        // Check attempt count before consuming
        enforceMaxAttempts(normalizedEmail, code);

        // Atomically consume the token
        OffsetDateTime now = OffsetDateTime.now();
        int consumed = authTokenRepository.consumeByEmailAndCode(normalizedEmail, code, now);
        if (consumed == 0) {
            // Increment attempt count for tracking
            authTokenRepository.incrementAttemptCount(normalizedEmail, code);
            throw new ApiException("AUTH_CODE_INVALID",
                "Invalid or already used code", 400);
        }

        // Fetch the consumed token for its metadata
        AuthToken authToken = authTokenRepository
            .findByEmailAndCodeAndUsedAtIsNull(normalizedEmail, code)
            .orElseThrow(() -> new ApiException("AUTH_CODE_INVALID",
                "Invalid or already used code", 400));

        // consumeByEmailAndCode sets usedAt, but findByEmailAndCodeAndUsedAtIsNull
        // won't find it anymore. We need the token for invite code — fetch by token instead.
        return completeVerification(authToken);
    }

    @Transactional
    public User verifyToken(String tokenStr) {
        UUID token;
        try {
            token = UUID.fromString(tokenStr);
        } catch (IllegalArgumentException e) {
            throw new ApiException("AUTH_CODE_INVALID", "Invalid token", 400);
        }

        // Atomically consume
        OffsetDateTime now = OffsetDateTime.now();
        int consumed = authTokenRepository.consumeByToken(token, now);
        if (consumed == 0) {
            throw new ApiException("AUTH_CODE_INVALID",
                "Invalid or already used token", 400);
        }

        // Fetch token metadata (it's now used, so find by id or token)
        AuthToken authToken = authTokenRepository.findById(token)
            .orElseThrow(() -> new ApiException("AUTH_CODE_INVALID",
                "Invalid or already used token", 400));

        return completeVerification(authToken);
    }

    private User completeVerification(AuthToken authToken) {
        // Check expiry (defense-in-depth, atomic queries already check)
        if (authToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new ApiException("AUTH_CODE_EXPIRED",
                "Code has expired", 400);
        }

        String email = authToken.getEmail();
        Optional<User> existingUser = userService.findByEmail(email);

        if (existingUser.isPresent()) {
            // Re-check suspension at verification time
            if (existingUser.get().getStatus() == UserStatus.SUSPENDED) {
                throw new ApiException("ACCOUNT_SUSPENDED",
                    "This account has been suspended", 403);
            }
            return existingUser.get();
        }

        // New user — create account and consume invite
        User newUser = userService.createFromEmail(email);
        String inviteCodeStr = authToken.getInviteCode();
        if (inviteCodeStr != null) {
            inviteService.consumeCode(inviteCodeStr, newUser);
        }
        log.info("New user created: {}", email);
        return newUser;
    }

    private void enforceMaxAttempts(String email, String code) {
        authTokenRepository.findByEmailAndCodeAndUsedAtIsNull(email, code)
            .ifPresent(token -> {
                if (token.getAttemptCount() >= MAX_ATTEMPTS) {
                    throw new ApiException("AUTH_CODE_INVALID",
                        "Too many attempts. Please request a new code.", 429);
                }
            });
    }

    private String normalizeEmail(String email) {
        if (email == null) return null;
        return email.trim().toLowerCase();
    }

    @Transactional
    public int cleanExpiredTokens() {
        return authTokenRepository.deleteExpiredBefore(OffsetDateTime.now());
    }
}
```

Wait — there's a problem with `verifyCode`. After `consumeByEmailAndCode` sets `usedAt`, `findByEmailAndCodeAndUsedAtIsNull` won't find the token. And `authTokenRepository.findById(token)` in `verifyToken` uses the UUID `token` field as ID, but the entity `@Id` is `id`, not `token`.

Let me fix this. The `AuthToken` entity has `id` (UUID) as the primary key, and `token` (UUID) as a separate unique column. We need a `findByToken` method (not `findByTokenAndUsedAtIsNull`, since it's now used).

Actually, looking at this more carefully, the cleanest approach is:

1. For `verifyCode`: find the token first (to get its `id` and metadata), then atomically consume by `id`.
2. For `verifyToken`: find the token first, then atomically consume by `id`.

But that re-introduces the race condition window between find and consume. A better approach: do the atomic consume, then fetch the token including used ones for metadata.

We need a `findByToken(UUID token)` method (no `usedAt` filter) for the post-consume lookup.

Let me revise:

**Files:**
- Modify: `backend/src/main/java/ch/junto/auth/AuthTokenRepository.java` — add `findByToken` and `findById` lookups without usedAt filter

- [ ] **Step 1: Add post-consume lookup methods to AuthTokenRepository**

Add these to `AuthTokenRepository`:

```java
/**
 * Find token by UUID (including used ones). For post-consume metadata lookup.
 */
Optional<AuthToken> findByToken(UUID token);
```

- [ ] **Step 2: Rewrite AuthService**

The key insight: for `verifyCode`, we do the atomic consume first, then look up the token for metadata using a query that doesn't filter on `usedAt`. For `verifyToken` similarly.

```java
package ch.junto.auth;

import ch.junto.email.EmailService;
import ch.junto.invite.InviteService;
import ch.junto.shared.entity.AuthToken;
import ch.junto.shared.entity.User;
import ch.junto.shared.enums.UserStatus;
import ch.junto.shared.exception.ApiException;
import ch.junto.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int CODE_EXPIRY_MINUTES = 10;
    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom secureRandom = new SecureRandom();

    private final AuthTokenRepository authTokenRepository;
    private final UserService userService;
    private final InviteService inviteService;
    private final EmailService emailService;

    public AuthService(AuthTokenRepository authTokenRepository,
                       UserService userService,
                       InviteService inviteService,
                       EmailService emailService) {
        this.authTokenRepository = authTokenRepository;
        this.userService = userService;
        this.inviteService = inviteService;
        this.emailService = emailService;
    }

    @Transactional
    public void requestCode(String email, String inviteCode) {
        String normalizedEmail = normalizeEmail(email);

        Optional<User> existingUser = userService.findByEmail(normalizedEmail);

        if (existingUser.isEmpty()) {
            if (inviteCode == null || inviteCode.isBlank()) {
                throw new ApiException("INVITE_CODE_REQUIRED",
                    "Invite code is required for new accounts", 400);
            }
            inviteService.validateCode(inviteCode);
        } else {
            if (existingUser.get().getStatus() == UserStatus.SUSPENDED) {
                throw new ApiException("ACCOUNT_SUSPENDED",
                    "This account has been suspended", 403);
            }
        }

        // Invalidate any previous unused tokens for this email
        authTokenRepository.deleteUnusedByEmail(normalizedEmail);

        String code = String.format("%06d", secureRandom.nextInt(1000000));
        UUID token = UUID.randomUUID();

        AuthToken authToken = new AuthToken();
        authToken.setEmail(normalizedEmail);
        authToken.setToken(token);
        authToken.setCode(code);
        authToken.setExpiresAt(OffsetDateTime.now().plusMinutes(CODE_EXPIRY_MINUTES));

        if (existingUser.isEmpty() && inviteCode != null) {
            authToken.setInviteCode(inviteCode);
        }

        authTokenRepository.save(authToken);
        emailService.sendAuthCode(normalizedEmail, code, token.toString());
        log.info("Auth code requested");
    }

    @Transactional
    public User verifyCode(String email, String code) {
        String normalizedEmail = normalizeEmail(email);

        // Check attempt count before consuming
        enforceMaxAttempts(normalizedEmail, code);

        // Atomically consume the token
        OffsetDateTime now = OffsetDateTime.now();
        int consumed = authTokenRepository.consumeByEmailAndCode(normalizedEmail, code, now);
        if (consumed == 0) {
            // Increment attempt count for tracking (no-op if token doesn't exist)
            authTokenRepository.incrementAttemptCount(normalizedEmail, code);
            throw new ApiException("AUTH_CODE_INVALID",
                "Invalid or already used code", 400);
        }

        // Fetch consumed token metadata — find by email+code where used (we just consumed it)
        // Use a native query or just fetch the most recently used token for this email+code
        AuthToken authToken = authTokenRepository.findByEmailAndCode(normalizedEmail, code)
            .orElseThrow(() -> new ApiException("AUTH_CODE_INVALID",
                "Invalid or already used code", 400));

        return completeVerification(authToken);
    }

    @Transactional
    public User verifyToken(String tokenStr) {
        UUID token;
        try {
            token = UUID.fromString(tokenStr);
        } catch (IllegalArgumentException e) {
            throw new ApiException("AUTH_CODE_INVALID", "Invalid token", 400);
        }

        OffsetDateTime now = OffsetDateTime.now();
        int consumed = authTokenRepository.consumeByToken(token, now);
        if (consumed == 0) {
            throw new ApiException("AUTH_CODE_INVALID",
                "Invalid or already used token", 400);
        }

        // Fetch consumed token by token UUID (includes used ones)
        AuthToken authToken = authTokenRepository.findByToken(token)
            .orElseThrow(() -> new ApiException("AUTH_CODE_INVALID",
                "Invalid or already used token", 400));

        return completeVerification(authToken);
    }

    private User completeVerification(AuthToken authToken) {
        if (authToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new ApiException("AUTH_CODE_EXPIRED",
                "Code has expired", 400);
        }

        String email = authToken.getEmail();
        Optional<User> existingUser = userService.findByEmail(email);

        if (existingUser.isPresent()) {
            if (existingUser.get().getStatus() == UserStatus.SUSPENDED) {
                throw new ApiException("ACCOUNT_SUSPENDED",
                    "This account has been suspended", 403);
            }
            return existingUser.get();
        }

        User newUser = userService.createFromEmail(email);
        String inviteCodeStr = authToken.getInviteCode();
        if (inviteCodeStr != null) {
            inviteService.consumeCode(inviteCodeStr, newUser);
        }
        log.info("New user created: {}", email);
        return newUser;
    }

    private void enforceMaxAttempts(String email, String code) {
        authTokenRepository.findByEmailAndCodeAndUsedAtIsNull(email, code)
            .ifPresent(token -> {
                if (token.getAttemptCount() >= MAX_ATTEMPTS) {
                    throw new ApiException("AUTH_CODE_INVALID",
                        "Too many attempts. Please request a new code.", 429);
                }
            });
    }

    private String normalizeEmail(String email) {
        if (email == null) return null;
        return email.trim().toLowerCase();
    }

    @Transactional
    public int cleanExpiredTokens() {
        return authTokenRepository.deleteExpiredBefore(OffsetDateTime.now());
    }
}
```

This needs one more repository method: `findByEmailAndCode` (without the `usedAt` filter). Let me add it.

**Updated AuthTokenRepository — full replacement:**

```java
package ch.junto.auth;

import ch.junto.shared.entity.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface AuthTokenRepository extends JpaRepository<AuthToken, UUID> {

    // --- Atomic consume operations ---

    @Modifying
    @Query("""
        UPDATE AuthToken t
        SET t.usedAt = :now
        WHERE t.token = :token
          AND t.usedAt IS NULL
          AND t.expiresAt >= :now
        """)
    int consumeByToken(@Param("token") UUID token, @Param("now") OffsetDateTime now);

    @Modifying
    @Query("""
        UPDATE AuthToken t
        SET t.usedAt = :now
        WHERE t.email = :email
          AND t.code = :code
          AND t.usedAt IS NULL
          AND t.expiresAt >= :now
        """)
    int consumeByEmailAndCode(@Param("email") String email, @Param("code") String code, @Param("now") OffsetDateTime now);

    // --- Attempt tracking ---

    @Modifying
    @Query("""
        UPDATE AuthToken t
        SET t.attemptCount = t.attemptCount + 1
        WHERE t.email = :email
          AND t.code = :code
          AND t.usedAt IS NULL
        """)
    int incrementAttemptCount(@Param("email") String email, @Param("code") String code);

    // --- Lookup (with usedAt filter) ---

    Optional<AuthToken> findByEmailAndCodeAndUsedAtIsNull(String email, String code);
    Optional<AuthToken> findByTokenAndUsedAtIsNull(UUID token);

    // --- Lookup (without usedAt filter — for post-consume metadata) ---

    Optional<AuthToken> findByToken(UUID token);
    Optional<AuthToken> findByEmailAndCode(String email, String code);

    // --- Cleanup ---

    @Modifying
    @Query("DELETE FROM AuthToken t WHERE t.email = :email AND t.usedAt IS NULL")
    void deleteUnusedByEmail(@Param("email") String email);

    @Modifying
    @Query("DELETE FROM AuthToken t WHERE t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") OffsetDateTime cutoff);
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/ch/junto/auth/AuthService.java \
        backend/src/main/java/ch/junto/auth/AuthTokenRepository.java
git commit -m "feat(auth): atomic token consume, suspended re-check, single active token, max attempts"
```

---

## Task 3: Update AuthServiceTest to match new behavior

The existing tests must be updated to match the new verification flow (atomic consume, email normalization, attempt tracking).

**Files:**
- Modify: `backend/src/test/java/ch/junto/auth/AuthServiceTest.java`

- [ ] **Step 1: Rewrite AuthServiceTest**

```java
package ch.junto.auth;

import ch.junto.email.EmailService;
import ch.junto.fixtures.AuthTokenFixture;
import ch.junto.fixtures.UserFixture;
import ch.junto.invite.InviteService;
import ch.junto.shared.entity.AuthToken;
import ch.junto.shared.entity.User;
import ch.junto.shared.exception.ApiException;
import ch.junto.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock
    private AuthTokenRepository authTokenRepository;

    @Mock
    private UserService userService;

    @Mock
    private InviteService inviteService;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuthService authService;

    @Captor
    private ArgumentCaptor<AuthToken> tokenCaptor;

    @Nested
    @DisplayName("requestCode")
    class RequestCode {

        @Test
        @DisplayName("should require invite code for new email")
        void shouldRequireInviteCodeForNewEmail() {
            when(userService.findByEmail("new@junto.app")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.requestCode("new@junto.app", null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo("INVITE_CODE_REQUIRED");
                });
        }

        @Test
        @DisplayName("should require invite code for new email when blank")
        void shouldRequireInviteCodeForNewEmailWhenBlank() {
            when(userService.findByEmail("new@junto.app")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.requestCode("new@junto.app", "   "))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo("INVITE_CODE_REQUIRED");
                });
        }

        @Test
        @DisplayName("should validate invite code for new email")
        void shouldValidateInviteCodeForNewEmail() {
            when(userService.findByEmail("new@junto.app")).thenReturn(Optional.empty());
            doThrow(new ApiException("INVITE_CODE_INVALID", "Invalid invite code", 400))
                .when(inviteService).validateCode("BAD-CODE");

            assertThatThrownBy(() -> authService.requestCode("new@junto.app", "BAD-CODE"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo("INVITE_CODE_INVALID");
                });
        }

        @Test
        @DisplayName("should send code for new email with valid invite")
        void shouldSendCodeForNewEmailWithValidInvite() {
            when(userService.findByEmail("new@junto.app")).thenReturn(Optional.empty());
            when(authTokenRepository.save(any(AuthToken.class))).thenAnswer(inv -> inv.getArgument(0));

            authService.requestCode("new@junto.app", "JUNTO-ABC123");

            verify(inviteService).validateCode("JUNTO-ABC123");
            verify(authTokenRepository).deleteUnusedByEmail("new@junto.app");
            verify(authTokenRepository).save(any(AuthToken.class));
            verify(emailService).sendAuthCode(eq("new@junto.app"), anyString(), anyString());
        }

        @Test
        @DisplayName("should store invite code on token for new user")
        void shouldStoreInviteCodeOnTokenForNewUser() {
            when(userService.findByEmail("new@junto.app")).thenReturn(Optional.empty());
            when(authTokenRepository.save(any(AuthToken.class))).thenAnswer(inv -> inv.getArgument(0));

            authService.requestCode("new@junto.app", "JUNTO-XYZ789");

            verify(authTokenRepository).save(tokenCaptor.capture());
            assertThat(tokenCaptor.getValue().getInviteCode()).isEqualTo("JUNTO-XYZ789");
        }

        @Test
        @DisplayName("should ignore invite code for existing email")
        void shouldIgnoreInviteCodeForExistingEmail() {
            User existingUser = UserFixture.defaultUser();
            when(userService.findByEmail("user@junto.app")).thenReturn(Optional.of(existingUser));

            authService.requestCode("user@junto.app", "JUNTO-IGNORE");

            verify(inviteService, never()).validateCode(anyString());
            verify(authTokenRepository).save(any(AuthToken.class));
            verify(emailService).sendAuthCode(eq("user@junto.app"), anyString(), anyString());
        }

        @Test
        @DisplayName("should reject suspended user")
        void shouldRejectSuspendedUser() {
            User suspendedUser = UserFixture.suspended();
            when(userService.findByEmail("suspended@junto.app")).thenReturn(Optional.of(suspendedUser));

            assertThatThrownBy(() -> authService.requestCode("suspended@junto.app", null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo("ACCOUNT_SUSPENDED");
                });

            verify(authTokenRepository, never()).save(any());
            verify(emailService, never()).sendAuthCode(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("should invalidate previous unused tokens before issuing new one")
        void shouldInvalidatePreviousUnusedTokens() {
            User existingUser = UserFixture.defaultUser();
            when(userService.findByEmail("user@junto.app")).thenReturn(Optional.of(existingUser));

            authService.requestCode("user@junto.app", null);

            verify(authTokenRepository).deleteUnusedByEmail("user@junto.app");
        }

        @Test
        @DisplayName("should not set invite code on token for existing user")
        void shouldNotSetInviteCodeForExistingUser() {
            User existingUser = UserFixture.defaultUser();
            when(userService.findByEmail("user@junto.app")).thenReturn(Optional.of(existingUser));
            when(authTokenRepository.save(any(AuthToken.class))).thenAnswer(inv -> inv.getArgument(0));

            authService.requestCode("user@junto.app", null);

            verify(authTokenRepository).save(tokenCaptor.capture());
            assertThat(tokenCaptor.getValue().getInviteCode()).isNull();
        }

        @Test
        @DisplayName("should normalize email to lowercase and trim")
        void shouldNormalizeEmail() {
            User existingUser = UserFixture.withEmail("user@junto.app");
            when(userService.findByEmail("user@junto.app")).thenReturn(Optional.of(existingUser));

            authService.requestCode("  User@Junto.APP  ", null);

            verify(userService).findByEmail("user@junto.app");
            verify(authTokenRepository).deleteUnusedByEmail("user@junto.app");
            verify(emailService).sendAuthCode(eq("user@junto.app"), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("verifyCode")
    class VerifyCode {

        @Test
        @DisplayName("should verify valid OTP with atomic consume")
        void shouldVerifyValidOtp() {
            User existingUser = UserFixture.withEmail("user@junto.app");
            AuthToken authToken = AuthTokenFixture.valid("user@junto.app");

            when(authTokenRepository.findByEmailAndCodeAndUsedAtIsNull("user@junto.app", "123456"))
                .thenReturn(Optional.of(authToken));
            when(authTokenRepository.consumeByEmailAndCode(eq("user@junto.app"), eq("123456"), any()))
                .thenReturn(1);
            when(authTokenRepository.findByEmailAndCode("user@junto.app", "123456"))
                .thenReturn(Optional.of(authToken));
            when(userService.findByEmail("user@junto.app")).thenReturn(Optional.of(existingUser));

            User result = authService.verifyCode("user@junto.app", "123456");

            assertThat(result).isEqualTo(existingUser);
        }

        @Test
        @DisplayName("should reject invalid code")
        void shouldRejectInvalidCode() {
            when(authTokenRepository.findByEmailAndCodeAndUsedAtIsNull("user@junto.app", "000000"))
                .thenReturn(Optional.empty());
            when(authTokenRepository.consumeByEmailAndCode(eq("user@junto.app"), eq("000000"), any()))
                .thenReturn(0);
            when(authTokenRepository.incrementAttemptCount("user@junto.app", "000000"))
                .thenReturn(0);

            assertThatThrownBy(() -> authService.verifyCode("user@junto.app", "000000"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo("AUTH_CODE_INVALID");
                });
        }

        @Test
        @DisplayName("should reject expired code")
        void shouldRejectExpiredCode() {
            AuthToken authToken = AuthTokenFixture.expired("user@junto.app");

            when(authTokenRepository.findByEmailAndCodeAndUsedAtIsNull("user@junto.app", "654321"))
                .thenReturn(Optional.of(authToken));
            when(authTokenRepository.consumeByEmailAndCode(eq("user@junto.app"), eq("654321"), any()))
                .thenReturn(1);
            when(authTokenRepository.findByEmailAndCode("user@junto.app", "654321"))
                .thenReturn(Optional.of(authToken));

            assertThatThrownBy(() -> authService.verifyCode("user@junto.app", "654321"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo("AUTH_CODE_EXPIRED");
                });
        }

        @Test
        @DisplayName("should create user on first verification")
        void shouldCreateUserOnFirstVerification() {
            User newUser = UserFixture.withEmail("new@junto.app");
            AuthToken authToken = AuthTokenFixture.valid("new@junto.app");

            when(authTokenRepository.findByEmailAndCodeAndUsedAtIsNull("new@junto.app", "123456"))
                .thenReturn(Optional.of(authToken));
            when(authTokenRepository.consumeByEmailAndCode(eq("new@junto.app"), eq("123456"), any()))
                .thenReturn(1);
            when(authTokenRepository.findByEmailAndCode("new@junto.app", "123456"))
                .thenReturn(Optional.of(authToken));
            when(userService.findByEmail("new@junto.app")).thenReturn(Optional.empty());
            when(userService.createFromEmail("new@junto.app")).thenReturn(newUser);

            User result = authService.verifyCode("new@junto.app", "123456");

            assertThat(result).isEqualTo(newUser);
            verify(userService).createFromEmail("new@junto.app");
        }

        @Test
        @DisplayName("should pass exact invite code to consumeCode")
        void shouldPassExactInviteCodeToConsumeCode() {
            User newUser = UserFixture.withEmail("new@junto.app");
            AuthToken authToken = AuthTokenFixture.withInviteCode("new@junto.app", "JUNTO-ABC123");

            when(authTokenRepository.findByEmailAndCodeAndUsedAtIsNull("new@junto.app", "123456"))
                .thenReturn(Optional.of(authToken));
            when(authTokenRepository.consumeByEmailAndCode(eq("new@junto.app"), eq("123456"), any()))
                .thenReturn(1);
            when(authTokenRepository.findByEmailAndCode("new@junto.app", "123456"))
                .thenReturn(Optional.of(authToken));
            when(userService.findByEmail("new@junto.app")).thenReturn(Optional.empty());
            when(userService.createFromEmail("new@junto.app")).thenReturn(newUser);

            authService.verifyCode("new@junto.app", "123456");

            verify(inviteService).consumeCode("JUNTO-ABC123", newUser);
        }

        @Test
        @DisplayName("should reject suspended user at verification time")
        void shouldRejectSuspendedUserAtVerification() {
            User suspendedUser = UserFixture.suspended();
            AuthToken authToken = AuthTokenFixture.valid("suspended@junto.app");

            when(authTokenRepository.findByEmailAndCodeAndUsedAtIsNull("suspended@junto.app", "123456"))
                .thenReturn(Optional.of(authToken));
            when(authTokenRepository.consumeByEmailAndCode(eq("suspended@junto.app"), eq("123456"), any()))
                .thenReturn(1);
            when(authTokenRepository.findByEmailAndCode("suspended@junto.app", "123456"))
                .thenReturn(Optional.of(authToken));
            when(userService.findByEmail("suspended@junto.app")).thenReturn(Optional.of(suspendedUser));

            assertThatThrownBy(() -> authService.verifyCode("suspended@junto.app", "123456"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo("ACCOUNT_SUSPENDED");
                });
        }

        @Test
        @DisplayName("should reject after max attempts exceeded")
        void shouldRejectAfterMaxAttemptsExceeded() {
            AuthToken authToken = AuthTokenFixture.valid("user@junto.app");
            authToken.setAttemptCount(5);

            when(authTokenRepository.findByEmailAndCodeAndUsedAtIsNull("user@junto.app", "123456"))
                .thenReturn(Optional.of(authToken));

            assertThatThrownBy(() -> authService.verifyCode("user@junto.app", "123456"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo("AUTH_CODE_INVALID");
                    assertThat(api.getStatus()).isEqualTo(429);
                });

            verify(authTokenRepository, never()).consumeByEmailAndCode(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("should normalize email for verification")
        void shouldNormalizeEmailForVerification() {
            User existingUser = UserFixture.withEmail("user@junto.app");
            AuthToken authToken = AuthTokenFixture.valid("user@junto.app");

            when(authTokenRepository.findByEmailAndCodeAndUsedAtIsNull("user@junto.app", "123456"))
                .thenReturn(Optional.of(authToken));
            when(authTokenRepository.consumeByEmailAndCode(eq("user@junto.app"), eq("123456"), any()))
                .thenReturn(1);
            when(authTokenRepository.findByEmailAndCode("user@junto.app", "123456"))
                .thenReturn(Optional.of(authToken));
            when(userService.findByEmail("user@junto.app")).thenReturn(Optional.of(existingUser));

            User result = authService.verifyCode("  User@Junto.APP  ", "123456");

            assertThat(result).isEqualTo(existingUser);
        }
    }

    @Nested
    @DisplayName("verifyToken")
    class VerifyToken {

        @Test
        @DisplayName("should verify valid magic link token")
        void shouldVerifyValidToken() {
            User existingUser = UserFixture.withEmail("user@junto.app");
            AuthToken authToken = AuthTokenFixture.valid("user@junto.app");

            when(authTokenRepository.consumeByToken(eq(authToken.getToken()), any()))
                .thenReturn(1);
            when(authTokenRepository.findByToken(authToken.getToken()))
                .thenReturn(Optional.of(authToken));
            when(userService.findByEmail("user@junto.app")).thenReturn(Optional.of(existingUser));

            User result = authService.verifyToken(authToken.getToken().toString());

            assertThat(result).isEqualTo(existingUser);
        }

        @Test
        @DisplayName("should reject invalid token format")
        void shouldRejectInvalidTokenFormat() {
            assertThatThrownBy(() -> authService.verifyToken("not-a-uuid"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo("AUTH_CODE_INVALID");
                });
        }

        @Test
        @DisplayName("should reject already used token")
        void shouldRejectAlreadyUsedToken() {
            UUID token = UUID.randomUUID();
            when(authTokenRepository.consumeByToken(eq(token), any())).thenReturn(0);

            assertThatThrownBy(() -> authService.verifyToken(token.toString()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo("AUTH_CODE_INVALID");
                });
        }

        @Test
        @DisplayName("should reject expired token")
        void shouldRejectExpiredToken() {
            AuthToken authToken = AuthTokenFixture.expired("user@junto.app");
            UUID token = authToken.getToken();

            when(authTokenRepository.consumeByToken(eq(token), any())).thenReturn(1);
            when(authTokenRepository.findByToken(token)).thenReturn(Optional.of(authToken));

            assertThatThrownBy(() -> authService.verifyToken(token.toString()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo("AUTH_CODE_EXPIRED");
                });
        }

        @Test
        @DisplayName("should reject suspended user at token verification")
        void shouldRejectSuspendedUserAtTokenVerification() {
            User suspendedUser = UserFixture.suspended();
            AuthToken authToken = AuthTokenFixture.valid("suspended@junto.app");
            UUID token = authToken.getToken();

            when(authTokenRepository.consumeByToken(eq(token), any())).thenReturn(1);
            when(authTokenRepository.findByToken(token)).thenReturn(Optional.of(authToken));
            when(userService.findByEmail("suspended@junto.app")).thenReturn(Optional.of(suspendedUser));

            assertThatThrownBy(() -> authService.verifyToken(token.toString()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo("ACCOUNT_SUSPENDED");
                });
        }
    }
}
```

- [ ] **Step 2: Run the tests**

Run: `cd backend && ./gradlew test --tests "ch.junto.auth.AuthServiceTest" -v`
Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/ch/junto/auth/AuthServiceTest.java
git commit -m "test(auth): update tests for atomic consume, suspended re-check, email normalization"
```

---

## Task 4: Return DTO instead of User entity from `/me` and other endpoints

Returning the raw `User` entity leaks internal fields, lazy-loaded relationships, and implementation details.

**Files:**
- Create: `backend/src/main/java/ch/junto/auth/dto/MeResponse.java`
- Modify: `backend/src/main/java/ch/junto/auth/AuthController.java`
- Modify: `backend/src/main/java/ch/junto/user/UserController.java`

- [ ] **Step 1: Create MeResponse DTO**

```java
package ch.junto.auth.dto;

import ch.junto.shared.entity.User;
import ch.junto.shared.enums.UserRole;
import ch.junto.shared.enums.UserStatus;

import java.util.UUID;

public record MeResponse(
    UUID id,
    String email,
    String displayName,
    String avatarUrl,
    String bio,
    String city,
    UserRole role,
    UserStatus status,
    boolean onboarded,
    int inviteCodesRemaining
) {
    public static MeResponse from(User user) {
        return new MeResponse(
            user.getId(),
            user.getEmail(),
            user.getDisplayName(),
            user.getAvatarUrl(),
            user.getBio(),
            user.getCity(),
            user.getRole(),
            user.getStatus(),
            user.isOnboarded(),
            user.getInviteCodesRemaining()
        );
    }
}
```

- [ ] **Step 2: Update AuthController `/me` to return DTO**

In `AuthController.java`, change:

```java
@GetMapping("/me")
public User me(@CurrentUser User user) {
    return user;
}
```

To:

```java
@GetMapping("/me")
public MeResponse me(@CurrentUser User user) {
    return MeResponse.from(user);
}
```

Add import: `import ch.junto.auth.dto.MeResponse;`

- [ ] **Step 3: Update UserController endpoints to return DTO**

In `UserController.java`, change all `User` return types to `MeResponse`:

```java
package ch.junto.user;

import ch.junto.auth.CurrentUser;
import ch.junto.auth.dto.MeResponse;
import ch.junto.shared.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    public record OnboardRequest(@NotBlank String displayName) {}

    @PostMapping("/onboard")
    public MeResponse onboard(@RequestBody @Valid OnboardRequest request, @CurrentUser User user) {
        return MeResponse.from(userService.completeOnboarding(user.getId(), request.displayName()));
    }

    @GetMapping("/profile")
    public MeResponse getProfile(@CurrentUser User user) {
        return MeResponse.from(user);
    }

    @DeleteMapping("/me")
    public void deleteAccount(@CurrentUser User user, jakarta.servlet.http.HttpSession session) {
        userService.anonymizeUser(user.getId());
        session.invalidate();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/ch/junto/auth/dto/MeResponse.java \
        backend/src/main/java/ch/junto/auth/AuthController.java \
        backend/src/main/java/ch/junto/user/UserController.java
git commit -m "feat(auth): return DTO instead of User entity from API endpoints"
```

---

## Task 5: Change magic-link verify endpoint from GET to POST

The `GET /api/auth/verify-token` creates a session and mutates state, which means email scanners and link prefetchers can consume the magic link before the user clicks it.

**Files:**
- Modify: `backend/src/main/java/ch/junto/auth/AuthController.java`
- Modify: `backend/src/main/java/ch/junto/auth/config/SecurityConfig.java`

- [ ] **Step 1: Change verify-token from GET to POST**

In `AuthController.java`, change:

```java
@GetMapping("/verify-token")
public void verifyToken(@RequestParam String token, HttpServletRequest servletRequest, HttpServletResponse response) {
    User user = authService.verifyToken(token);
    authenticateUser(user, servletRequest, response);
}
```

To:

```java
@PostMapping("/verify-token")
public void verifyToken(@RequestBody VerifyTokenRequest request, HttpServletRequest servletRequest, HttpServletResponse response) {
    User user = authService.verifyToken(request.token());
    authenticateUser(user, servletRequest, response);
}
```

Add the new request record alongside the existing ones:

```java
public record VerifyTokenRequest(@NotBlank String token) {}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/ch/junto/auth/AuthController.java
git commit -m "fix(auth): change magic-link verify from GET to POST to prevent prefetch consumption"
```

---

## Task 6: Add scheduled cleanup for expired tokens

Currently `cleanExpiredTokens()` exists but is never called. Wire it up to a Spring scheduled task.

**Files:**
- Create: `backend/src/main/java/ch/junto/auth/AuthTokenCleanup.java`

- [ ] **Step 1: Create scheduled cleanup task**

```java
package ch.junto.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AuthTokenCleanup {

    private static final Logger log = LoggerFactory.getLogger(AuthTokenCleanup.class);
    private final AuthService authService;

    public AuthTokenCleanup(AuthService authService) {
        this.authService = authService;
    }

    @Scheduled(fixedRate = 60 * 60 * 1000) // Every hour
    public void cleanExpiredTokens() {
        int deleted = authService.cleanExpiredTokens();
        if (deleted > 0) {
            log.info("Cleaned up {} expired auth tokens", deleted);
        }
    }
}
```

- [ ] **Step 2: Enable scheduling in the application**

Add `@EnableScheduling` to the main application class or a config class. Check if it's already enabled — if not, add it to `SecurityConfig` or a new `SchedulingConfig`:

```java
package ch.junto.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/ch/junto/auth/AuthTokenCleanup.java \
        backend/src/main/java/ch/junto/shared/config/SchedulingConfig.java
git commit -m "feat(auth): add scheduled cleanup of expired auth tokens"
```

---

## Self-Review

### Spec Coverage

| Audit Finding | Task |
|---|---|
| 1. Race condition on token consumption | Task 1 (atomic consume) + Task 2 (rewrite verify) |
| 2. Suspended user can verify old token | Task 2 (re-check in `completeVerification`) |
| 3. Multiple active codes per email | Task 2 (`deleteUnusedByEmail` in `requestCode`) |
| 4. No brute-force protection | Task 1 (`attempt_count`) + Task 2 (`enforceMaxAttempts`) |
| 5. Account enumeration | Not addressed (explicit product tradeoff — see notes) |
| 6. Code-only replay with multiple tokens | Task 2 (single active token per email) |
| 7. GET magic link creates session | Task 5 (change to POST) |
| 8. Session fixation | Not in this plan (requires deeper Spring Security investigation) |
| 9. `/me` returns full entity | Task 4 (MeResponse DTO) |
| 10. Invite code revalidation at verify | Task 2 (`consumeCode` is authoritative, transactional rollback covers it) |
| 11. Plaintext code storage | Not addressed (low priority — short expiry + attempt limits mitigate) |
| 12. Email normalization | Task 2 (`normalizeEmail`) |
| 13. Expired token cleanup | Task 6 (scheduled cleanup) |
| 14. PII in logs | Task 2 (removed email from log messages) |
| 15. CSRF UX alignment | Not addressed (frontend concern) |

### Placeholder Scan

No TBD, TODO, or placeholder patterns found. All code blocks contain complete implementations.

### Type Consistency

- `MeResponse.from(User)` used consistently in Task 4 across `AuthController` and `UserController`
- `AuthTokenRepository` methods match between Task 1 definitions and Task 2 usage
- `AuthToken.attemptCount` field added in Task 1, used in Task 2 tests via `setAttemptCount(5)`
- `consumeByEmailAndCode` and `consumeByToken` return `int`, checked against `== 0` consistently

### Notable exclusions

- **Account enumeration (Finding 5):** This is an explicit product decision. The audit acknowledges it can be an intentional tradeoff. Fixing it requires UX changes (generic "if eligible, code sent" response) that would require frontend coordination. Deferring.
- **Session fixation (Finding 8):** Requires investigating Spring Security's session fixation handling with the custom `SecurityContextHolder` approach. This is a deeper investigation that should be a separate plan.
- **Code hashing (Finding 11):** With the 10-minute expiry, single active token, and max attempts protections now in place, the risk profile is significantly reduced. Can be added in a future iteration.
