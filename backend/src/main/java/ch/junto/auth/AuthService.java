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

        enforceMaxAttempts(normalizedEmail, code);

        OffsetDateTime now = OffsetDateTime.now();
        int consumed = authTokenRepository.consumeByEmailAndCode(normalizedEmail, code, now);
        if (consumed == 0) {
            authTokenRepository.incrementAttemptCount(normalizedEmail, code);
            throw new ApiException("AUTH_CODE_INVALID",
                "Invalid or already used code", 400);
        }

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
