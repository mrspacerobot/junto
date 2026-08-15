package ch.junto.auth;

import ch.junto.invite.InviteService;
import ch.junto.auth.entity.AuthToken;
import ch.junto.user.entity.User;
import ch.junto.user.enums.UserStatus;
import ch.junto.core.exception.ApiException;
import ch.junto.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int MAX_ATTEMPTS = 5;

    private final AuthTokenRepository authTokenRepository;
    private final UserService userService;
    private final InviteService inviteService;
    private final AuthCodeService authCodeService;

    public AuthService(AuthTokenRepository authTokenRepository,
                       UserService userService,
                       InviteService inviteService,
                       AuthCodeService authCodeService) {
        this.authTokenRepository = authTokenRepository;
        this.userService = userService;
        this.inviteService = inviteService;
        this.authCodeService = authCodeService;
    }

    /**
     * Sign-in deliberately keeps the same public response for known and unknown
     * email addresses. Only active existing accounts receive a code.
     */
    public void requestSignInCode(String email) {
        String normalizedEmail = normalizeEmail(email);
        Optional<User> existingUser = userService.findByEmail(normalizedEmail);
        if (existingUser.isEmpty() || existingUser.get().getStatus() != UserStatus.ACTIVE) {
            log.info("Sign-in code request accepted without issuing a token");
            return;
        }

        try {
            authCodeService.issue(normalizedEmail, null);
        } catch (AuthEmailDeliveryException exception) {
            // AuthCodeService's transaction has already rolled back. Suppress the
            // typed failure here so sign-in remains account-enumeration-safe.
            log.warn("Sign-in code delivery failed; returning generic response");
        }
    }

    /**
     * Signup errors depend only on explicit create-account intent and the
     * submitted invite, never on whether the email already has an account.
     */
    public void requestSignupCode(String email, String inviteCode) {
        if (inviteCode == null || inviteCode.isBlank()) {
            throw new ApiException("INVITE_REQUIRED",
                "An invite code is required to create an account.", 400);
        }

        String normalizedInviteCode = inviteCode.trim();
        inviteService.validateSignupCode(normalizedInviteCode);
        authCodeService.issue(normalizeEmail(email), normalizedInviteCode);
    }

    @Transactional(noRollbackFor = AuthAttemptRejectedException.class)
    public User verifyCode(String email, String code) {
        String normalizedEmail = normalizeEmail(email);
        String submittedCode = code.trim();
        OffsetDateTime now = OffsetDateTime.now();

        int consumed = authTokenRepository.consumeByEmailAndCode(
            normalizedEmail, submittedCode, now, MAX_ATTEMPTS);
        if (consumed == 0) {
            int incremented = authTokenRepository.incrementWrongAttempt(
                normalizedEmail, submittedCode, now, MAX_ATTEMPTS);
            if (incremented == 1) {
                throw new AuthAttemptRejectedException(
                    "AUTH_CODE_INVALID", "Invalid or already used code", 400);
            }

            Optional<AuthToken> current = authTokenRepository
                .findFirstByEmailAndUsedAtIsNullOrderByCreatedAtDesc(normalizedEmail);
            if (current.isPresent() && current.get().getAttemptCount() >= MAX_ATTEMPTS) {
                throw new AuthAttemptRejectedException(
                    "AUTH_CODE_INVALID", "Too many attempts. Please request a new code.", 429);
            }
            if (current.isPresent() && current.get().getExpiresAt().isBefore(now)) {
                throw new AuthAttemptRejectedException(
                    "AUTH_CODE_EXPIRED", "Code has expired", 400);
            }
            throw new AuthAttemptRejectedException(
                "AUTH_CODE_INVALID", "Invalid or already used code", 400);
        }

        AuthToken authToken = authTokenRepository.findByEmailAndCode(normalizedEmail, submittedCode)
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
        log.info("New user created");
        return newUser;
    }

    private String normalizeEmail(String email) {
        if (email == null) return null;
        return email.trim().toLowerCase();
    }

    @Transactional
    public User refreshUser(UUID userId) {
        return userService.getById(userId);
    }

    @Transactional
    public int cleanExpiredTokens() {
        return authTokenRepository.deleteExpiredBefore(OffsetDateTime.now());
    }

    /** Marker exception whose failed-attempt update must commit. */
    static final class AuthAttemptRejectedException extends ApiException {
        AuthAttemptRejectedException(String code, String message, int status) {
            super(code, message, status);
        }
    }
}
