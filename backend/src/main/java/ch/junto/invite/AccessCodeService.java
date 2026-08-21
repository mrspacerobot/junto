package ch.junto.invite;

import ch.junto.invite.entity.AccessCode;
import ch.junto.user.entity.User;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared access codes: reusable, never expiring, disabled only by operator revocation.
 *
 * <p>They exist beside personal invite codes rather than inside them. {@link InviteCodeRepository}
 * redeems an invite with an atomic {@code used_at IS NULL} update, and that guard is what makes a
 * personal invite single-use; reuse is therefore modelled as many redemption rows against one
 * access code instead of by relaxing the guard.
 */
@Service
public class AccessCodeService {

  /** Label of the single code created by the one-time first-administrator bootstrap. */
  public static final String BOOTSTRAP_LABEL = "bootstrap";

  private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9-]{8,64}$");
  private static final Pattern PERSONAL_INVITE_CODE_PATTERN =
      Pattern.compile("^JUNTO-[A-Z0-9]{6}$");
  private static final Logger log = LoggerFactory.getLogger(AccessCodeService.class);

  private final AccessCodeRepository accessCodeRepository;
  private final AccessCodeRedemptionRepository accessCodeRedemptionRepository;
  private final InviteCodeRepository inviteCodeRepository;

  public AccessCodeService(
      AccessCodeRepository accessCodeRepository,
      AccessCodeRedemptionRepository accessCodeRedemptionRepository,
      InviteCodeRepository inviteCodeRepository) {
    this.accessCodeRepository = accessCodeRepository;
    this.accessCodeRedemptionRepository = accessCodeRedemptionRepository;
    this.inviteCodeRepository = inviteCodeRepository;
  }

  /** A shared code is typed by hand from a message or a conversation, so case never matters. */
  public static String normalize(String code) {
    return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
  }

  public static boolean isValidFormat(String normalizedCode) {
    return normalizedCode != null
        && CODE_PATTERN.matcher(normalizedCode).matches()
        && !PERSONAL_INVITE_CODE_PATTERN.matcher(normalizedCode).matches();
  }

  public static String formatRequirement() {
    return "8 to 64 characters of A-Z, 0-9 or '-', outside the reserved JUNTO-XXXXXX personal invite format";
  }

  /** True when a submitted signup code names an access code that has not been revoked. */
  public boolean isAvailable(String submittedCode) {
    String normalized = normalize(submittedCode);
    return normalized != null && accessCodeRepository.existsByCodeAndRevokedAtIsNull(normalized);
  }

  /**
   * Records an admission for the submitted code, or reports that no available access code matches.
   * The caller must answer identically for an unknown and a revoked code.
   */
  @Transactional
  public boolean redeemIfAvailable(String submittedCode, User user) {
    String normalized = normalize(submittedCode);
    if (normalized == null) {
      return false;
    }
    return accessCodeRedemptionRepository.redeemIfAvailable(
            normalized, user.getId(), OffsetDateTime.now())
        == 1;
  }

  public Optional<AccessCode> findByLabel(String label) {
    return accessCodeRepository.findByLabel(label);
  }

  /**
   * True when the code string is already an invite code, which signup would resolve ambiguously.
   */
  public boolean clashesWithInviteCode(String normalizedCode) {
    return normalizedCode != null && inviteCodeRepository.findByCode(normalizedCode).isPresent();
  }

  @Transactional
  public AccessCode create(String normalizedCode, String label, User createdBy) {
    if (!isValidFormat(normalizedCode)) {
      throw new IllegalArgumentException("Access code must be " + formatRequirement());
    }

    AccessCode accessCode = new AccessCode();
    accessCode.setCode(normalizedCode);
    accessCode.setLabel(label);
    accessCode.setCreatedBy(createdBy);
    AccessCode saved = accessCodeRepository.save(accessCode);
    log.info("Shared access code created (id={}, label={})", saved.getId(), label);
    return saved;
  }
}
