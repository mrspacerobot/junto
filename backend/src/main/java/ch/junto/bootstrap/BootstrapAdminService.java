package ch.junto.bootstrap;

import ch.junto.core.TransactionAfterCommit;
import ch.junto.invite.AccessCodeService;
import ch.junto.invite.entity.AccessCode;
import ch.junto.user.EmailNormalizer;
import ch.junto.user.UserRepository;
import ch.junto.user.ValidEmailAddress;
import ch.junto.user.entity.User;
import ch.junto.user.enums.UserRole;
import ch.junto.user.enums.UserStatus;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates, or confirms, the first administrator account and the initial shared access code.
 *
 * <p>Both values are supplied by the operator at execution time and neither is written to a log:
 * the audit trail records only the administrator outcome and current role/status, and identifies
 * the access code by its non-secret row id. Running the command again with the same two values
 * changes nothing; running it with a different administrator or a different initial code is refused
 * rather than silently creating a second one.
 */
public class BootstrapAdminService {

  private static final Logger log = LoggerFactory.getLogger(BootstrapAdminService.class);
  private static final int MINIMUM_ADMIN_INVITE_QUOTA = 1;
  private static final String ADMIN_DISPLAY_NAME = "Administrator";

  private final UserRepository userRepository;
  private final AccessCodeService accessCodeService;
  private final PostgresBootstrapLock bootstrapLock;
  private final Validator validator;

  public BootstrapAdminService(
      UserRepository userRepository,
      AccessCodeService accessCodeService,
      PostgresBootstrapLock bootstrapLock,
      Validator validator) {
    this.userRepository = userRepository;
    this.accessCodeService = accessCodeService;
    this.bootstrapLock = bootstrapLock;
    this.validator = validator;
  }

  @Transactional
  public void bootstrap(String email, String accessCode) {
    String normalizedEmail = EmailNormalizer.normalize(email);
    String normalizedCode = AccessCodeService.normalize(accessCode);
    validate(normalizedEmail, normalizedCode);
    bootstrapLock.acquire();

    Optional<AccessCode> existingAccessCode =
        accessCodeService.findByLabel(AccessCodeService.BOOTSTRAP_LABEL);
    if (existingAccessCode.isPresent()) {
      confirmExistingBootstrap(existingAccessCode.get(), normalizedEmail, normalizedCode);
      return;
    }

    User administrator = ensureAdministrator(normalizedEmail);
    createInitialAccessCode(normalizedCode, administrator);
  }

  private void validate(String normalizedEmail, String normalizedCode) {
    if (!validator.validate(new AdministratorIdentity(normalizedEmail)).isEmpty()) {
      throw new BootstrapAbortedException(
          "Refusing to bootstrap: the administrator address is not a valid email address.");
    }
    if (!AccessCodeService.isValidFormat(normalizedCode)) {
      throw new BootstrapAbortedException(
          "Refusing to bootstrap: the access code must be "
              + AccessCodeService.formatRequirement()
              + ". Case is normalized, so it may be supplied in any case.");
    }
  }

  private User ensureAdministrator(String normalizedEmail) {
    List<User> administrators = userRepository.findAllByRole(UserRole.ADMIN);
    boolean anotherAdministratorExists =
        administrators.stream()
            .anyMatch(administrator -> !normalizedEmail.equals(administrator.getEmail()));
    if (anotherAdministratorExists) {
      throw new BootstrapAbortedException(
          "Refusing to bootstrap: a different administrator account already exists. "
              + "The first administrator is bootstrapped once; grant further administrators "
              + "through normal account administration.");
    }

    Optional<User> existing = userRepository.findByEmail(normalizedEmail);
    if (existing.isEmpty()) {
      User created = new User();
      created.setEmail(normalizedEmail);
      created.setDisplayName(ADMIN_DISPLAY_NAME);
      created.setRole(UserRole.ADMIN);
      created.setStatus(UserStatus.ACTIVE);
      created.setOnboarded(false);
      User saved = userRepository.save(created);
      logAdministrator("CREATED", saved);
      return saved;
    }

    User administrator = existing.get();
    boolean promoted = administrator.getRole() != UserRole.ADMIN;
    boolean reactivated = administrator.getStatus() != UserStatus.ACTIVE;
    boolean inviteQuotaGranted =
        administrator.getInviteCodesRemaining() < MINIMUM_ADMIN_INVITE_QUOTA;
    if (promoted || reactivated || inviteQuotaGranted) {
      administrator.setRole(UserRole.ADMIN);
      administrator.setStatus(UserStatus.ACTIVE);
      if (inviteQuotaGranted) {
        administrator.setInviteCodesRemaining(MINIMUM_ADMIN_INVITE_QUOTA);
      }
      administrator = userRepository.save(administrator);
    }
    logAdministrator(
        promoted || reactivated || inviteQuotaGranted ? "UPDATED" : "ALREADY_PRESENT",
        administrator);
    return administrator;
  }

  private void confirmExistingBootstrap(
      AccessCode accessCode, String normalizedEmail, String normalizedCode) {
    if (!accessCode.getCode().equals(normalizedCode)) {
      throw new BootstrapAbortedException(
          "Refusing to bootstrap: a different initial access code already exists (id="
              + accessCode.getId()
              + "). Revoke it and issue a replacement instead of bootstrapping a second one.");
    }
    User administrator = accessCode.getCreatedBy();
    if (!administrator.getEmail().equals(normalizedEmail)) {
      throw new BootstrapAbortedException(
          "Refusing to bootstrap: the first administrator was already bootstrapped for a different identity.");
    }

    logAdministrator("ALREADY_BOOTSTRAPPED", administrator);
    logAccessCode(
        accessCode.isRevoked() ? "ALREADY_PRESENT_REVOKED" : "ALREADY_PRESENT", accessCode.getId());
  }

  private void createInitialAccessCode(String normalizedCode, User administrator) {
    if (accessCodeService.clashesWithInviteCode(normalizedCode)) {
      throw new BootstrapAbortedException(
          "Refusing to bootstrap: that code already exists as a personal invite code. "
              + "Choose a different initial access code.");
    }
    if (accessCodeService.exists(normalizedCode)) {
      throw new BootstrapAbortedException(
          "Refusing to bootstrap: that code already exists as a shared access code. "
              + "Choose a different initial access code.");
    }

    AccessCode created =
        accessCodeService.create(normalizedCode, AccessCodeService.BOOTSTRAP_LABEL, administrator);
    logAccessCode("CREATED", created.getId());
  }

  private void logAdministrator(String outcome, User administrator) {
    UserRole role = administrator.getRole();
    UserStatus status = administrator.getStatus();
    TransactionAfterCommit.run(
        () -> log.info("Bootstrap: administrator {} (role={}, status={})", outcome, role, status));
  }

  private void logAccessCode(String outcome, UUID accessCodeId) {
    TransactionAfterCommit.run(
        () ->
            log.info(
                "Bootstrap: initial access code {} (id={}, label={}, reusable=true, expiry=none)",
                outcome,
                accessCodeId,
                AccessCodeService.BOOTSTRAP_LABEL));
  }

  private record AdministratorIdentity(@ValidEmailAddress String email) {}
}
