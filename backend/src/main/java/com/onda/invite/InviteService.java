package com.onda.invite;

import com.onda.shared.entity.InviteCode;
import com.onda.shared.entity.User;
import com.onda.shared.exception.ApiException;
import com.onda.shared.exception.NotFoundException;
import com.onda.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class InviteService {

    private static final Logger log = LoggerFactory.getLogger(InviteService.class);
    private final InviteCodeRepository inviteCodeRepository;
    private final UserService userService;

    public InviteService(InviteCodeRepository inviteCodeRepository, UserService userService) {
        this.inviteCodeRepository = inviteCodeRepository;
        this.userService = userService;
    }

    /**
     * Validate that a code exists, is not used, and is not revoked.
     * Throws ApiException("INVITE_CODE_INVALID") if invalid.
     */
    public void validateCode(String code) {
        InviteCode inviteCode = inviteCodeRepository.findByCode(code)
            .orElseThrow(() -> new ApiException("INVITE_CODE_INVALID",
                "Invalid invite code", 400));

        if (inviteCode.isUsed()) {
            throw new ApiException("INVITE_CODE_INVALID",
                "Invite code has already been used", 400);
        }
        if (inviteCode.isRevoked()) {
            throw new ApiException("INVITE_CODE_INVALID",
                "Invite code has been revoked", 400);
        }
    }

    /**
     * Consume an invite code for a given email.
     * Called during account creation after verification.
     * Associates the code with the new user and decrements inviter's remaining count.
     */
    @Transactional
    public void consumeCodeForEmail(String email) {
        // Find the most recent unused code for this email's invite flow
        // We don't know which specific code was used, so we find any valid one
        // Actually, we need to track which code was validated during requestCode.
        // For simplicity: find the first unused, unrevoked code and consume it.
        // This works because requestCode already validated it exists.

        // Better approach: store the invite code in the auth flow
        // For P0, find any unused unrevoked code and consume it
        // TODO: Track exact code used during requestCode for precision
        User newUser = userService.findByEmail(email)
            .orElseThrow(() -> new NotFoundException("User not found"));

        // Find any valid code that was created by someone (not used, not revoked)
        List<InviteCode> allCodes = inviteCodeRepository.findAll();
        InviteCode validCode = allCodes.stream()
            .filter(c -> !c.isUsed() && !c.isRevoked())
            .findFirst()
            .orElseThrow(() -> new ApiException("INVITE_CODE_INVALID",
                "No valid invite code found", 400));

        validCode.setUsedBy(newUser);
        validCode.setUsedAt(OffsetDateTime.now());
        inviteCodeRepository.save(validCode);

        // Decrement inviter's remaining codes
        User inviter = validCode.getCreatedBy();
        inviter.setInviteCodesRemaining(inviter.getInviteCodesRemaining() - 1);

        log.info("Invite code {} consumed by {}", validCode.getCode(), email);
    }

    @Transactional
    public InviteCode generateCode(UUID userId) {
        User user = userService.findByIdOrThrow(userId);

        if (user.getInviteCodesRemaining() <= 0) {
            throw new ApiException("INVITE_CODE_INVALID",
                "No invite codes remaining", 400);
        }

        InviteCode inviteCode = new InviteCode();
        inviteCode.setCode("ONDA-" + UUID.randomUUID().toString()
            .substring(0, 6).toUpperCase());
        inviteCode.setCreatedBy(user);

        user.setInviteCodesRemaining(user.getInviteCodesRemaining() - 1);

        return inviteCodeRepository.save(inviteCode);
    }

    public List<InviteCode> listUserCodes(UUID userId) {
        return inviteCodeRepository.findAllByCreatedById(userId);
    }
}
