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
  private final AccessCodeService accessCodeService;
  private final UserService userService;
  private final UserRepository userRepository;

  public InviteService(
      InviteCodeRepository inviteCodeRepository,
      AccessCodeService accessCodeService,
      UserService userService,
      UserRepository userRepository) {
    this.inviteCodeRepository = inviteCodeRepository;
    this.accessCodeService = accessCodeService;
    this.userService = userService;
    this.userRepository = userRepository;
  }

  /**
   * Keep every unavailable-invite response identical so signup does not disclose whether a code is
   * unknown, used, or revoked, nor whether it is a personal invite or a shared access code.
   */
  public void validateSignupCode(String code) {
    if (accessCodeService.isAvailable(code)) {
      return;
    }

    if (!inviteCodeRepository.existsByCodeAndUsedAtIsNullAndRevokedAtIsNull(code)) {
      throw new ApiException(
          "INVITE_INVALID", "This invite is invalid or unavailable. Ask for a new invite.", 400);
    }
  }

  /**
   * Admit a new user with the submitted code. A shared access code records another redemption and
   * stays usable; a personal invite is consumed once, marked as used by the new user. Does NOT
   * decrement the inviter's count - that was already done during generation.
   */
  @Transactional
  public void consumeCode(String code, User newUser) {
    if (accessCodeService.redeemIfAvailable(code, newUser)) {
      log.info("Shared access code redeemed");
      return;
    }

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
