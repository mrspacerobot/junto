package ch.junto.invite;

import ch.junto.core.exception.ApiException;
import ch.junto.invite.entity.InviteCode;
import ch.junto.user.UserRepository;
import ch.junto.user.UserService;
import ch.junto.user.entity.User;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InviteService {

  private static final Logger log = LoggerFactory.getLogger(InviteService.class);
  private final InviteCodeRepository inviteCodeRepository;
  private final UserService userService;
  private final UserRepository userRepository;

  public InviteService(
      InviteCodeRepository inviteCodeRepository,
      UserService userService,
      UserRepository userRepository) {
    this.inviteCodeRepository = inviteCodeRepository;
    this.userService = userService;
    this.userRepository = userRepository;
  }

  /**
   * Validate that a code exists, is not used, and is not revoked. Throws
   * ApiException("INVITE_CODE_INVALID") if invalid.
   */
  public boolean isValidCode(String code) {
    return code != null && inviteCodeRepository.existsByCodeAndUsedAtIsNullAndRevokedAtIsNull(code);
  }

  public void validateCode(String code) {
    InviteCode inviteCode =
        inviteCodeRepository
            .findByCode(code)
            .orElseThrow(() -> new ApiException("INVITE_CODE_INVALID", "Invalid invite code", 400));

    if (inviteCode.isUsed()) {
      throw new ApiException("INVITE_CODE_INVALID", "Invite code has already been used", 400);
    }
    if (inviteCode.isRevoked()) {
      throw new ApiException("INVITE_CODE_INVALID", "Invite code has been revoked", 400);
    }
  }

  /**
   * Consume an invite code by its exact code string, marking it as used by the new user. Does NOT
   * decrement the inviter's count — that was already done during generation.
   */
  @Transactional
  public void consumeCode(String code, User newUser) {
    int consumed = inviteCodeRepository.consumeIfAvailable(code, newUser, OffsetDateTime.now());
    if (consumed != 1) {
      throw new ApiException(
          "INVITE_CODE_INVALID", "Invite code is invalid, used, or revoked", 400);
    }

    log.info("Invite code consumed");
  }

  @Transactional
  public InviteCode generateCode(UUID userId) {
    User user = userService.findByIdOrThrow(userId);

    if (userRepository.decrementInviteQuota(userId) != 1) {
      throw new ApiException("INVITE_CODE_INVALID", "No invite codes remaining", 400);
    }

    InviteCode inviteCode = new InviteCode();
    inviteCode.setCode("JUNTO-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
    inviteCode.setCreatedBy(user);

    return inviteCodeRepository.save(inviteCode);
  }

  public List<InviteCode> listUserCodes(UUID userId) {
    return inviteCodeRepository.findAllByCreatedById(userId);
  }
}
