package com.onda.auth;

import com.onda.email.EmailService;
import com.onda.invite.InviteService;
import com.onda.shared.entity.AuthToken;
import com.onda.shared.entity.User;
import com.onda.shared.exception.ApiException;
import com.onda.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Random;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int CODE_EXPIRY_MINUTES = 10;

    private final AuthTokenRepository authTokenRepository;
    private final UserService userService;
    private final InviteService inviteService;
    private final EmailService emailService;
    private final Random random = new Random();

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
        boolean isNewUser = userService.findByEmail(email).isEmpty();

        if (isNewUser) {
            if (inviteCode == null || inviteCode.isBlank()) {
                throw new ApiException("INVITE_CODE_REQUIRED",
                    "Invite code is required for new accounts", 400);
            }
            inviteService.validateCode(inviteCode);
        }

        if (userService.isSuspended(email)) {
            throw new ApiException("ACCOUNT_SUSPENDED",
                "This account has been suspended", 403);
        }

        String code = String.format("%06d", random.nextInt(1000000));
        UUID token = UUID.randomUUID();

        AuthToken authToken = new AuthToken();
        authToken.setEmail(email);
        authToken.setToken(token);
        authToken.setCode(code);
        authToken.setExpiresAt(OffsetDateTime.now().plusMinutes(CODE_EXPIRY_MINUTES));
        authTokenRepository.save(authToken);

        emailService.sendAuthCode(email, code, token.toString());
        log.info("Auth code requested for {}", email);
    }

    @Transactional
    public User verifyCode(String email, String code) {
        AuthToken authToken = authTokenRepository
            .findByEmailAndCodeAndUsedAtIsNull(email, code)
            .orElseThrow(() -> new ApiException("AUTH_CODE_INVALID",
                "Invalid or already used code", 400));

        return verifyAuthToken(authToken);
    }

    @Transactional
    public User verifyToken(String tokenStr) {
        UUID token;
        try {
            token = UUID.fromString(tokenStr);
        } catch (IllegalArgumentException e) {
            throw new ApiException("AUTH_CODE_INVALID", "Invalid token", 400);
        }

        AuthToken authToken = authTokenRepository
            .findByTokenAndUsedAtIsNull(token)
            .orElseThrow(() -> new ApiException("AUTH_CODE_INVALID",
                "Invalid or already used token", 400));

        return verifyAuthToken(authToken);
    }

    private User verifyAuthToken(AuthToken authToken) {
        if (authToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new ApiException("AUTH_CODE_EXPIRED",
                "Code has expired", 400);
        }

        authToken.setUsedAt(OffsetDateTime.now());
        authTokenRepository.save(authToken);

        String email = authToken.getEmail();
        return userService.findByEmail(email)
            .orElseGet(() -> {
                User newUser = userService.createFromEmail(email);
                inviteService.consumeCodeForEmail(email);
                log.info("New user created: {}", email);
                return newUser;
            });
    }

    @Transactional
    public int cleanExpiredTokens() {
        return authTokenRepository.deleteByExpiresAtBefore(OffsetDateTime.now());
    }
}
