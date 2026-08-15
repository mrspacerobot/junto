package ch.junto.auth;

import ch.junto.auth.entity.AuthToken;
import ch.junto.email.EmailService;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns the transaction that persists and delivers a replacement authentication code. */
@Service
public class AuthCodeService {

  private static final Logger log = LoggerFactory.getLogger(AuthCodeService.class);
  private static final int CODE_EXPIRY_MINUTES = 10;
  private static final SecureRandom secureRandom = new SecureRandom();

  private final AuthTokenRepository authTokenRepository;
  private final PostgresAuthTokenIssuanceLock issuanceLock;
  private final EmailService emailService;

  public AuthCodeService(
      AuthTokenRepository authTokenRepository,
      PostgresAuthTokenIssuanceLock issuanceLock,
      EmailService emailService) {
    this.authTokenRepository = authTokenRepository;
    this.issuanceLock = issuanceLock;
    this.emailService = emailService;
  }

  /**
   * A delivery exception leaves this annotated boundary, so Spring rolls back both deletion of the
   * previous code and insertion of the undelivered one.
   */
  @Transactional
  public void issue(String normalizedEmail, String inviteCode) {
    issuanceLock.acquire(normalizedEmail);
    authTokenRepository.deleteUnusedByEmail(normalizedEmail);

    String code = String.format("%06d", secureRandom.nextInt(1000000));
    UUID token = UUID.randomUUID();

    AuthToken authToken = new AuthToken();
    authToken.setEmail(normalizedEmail);
    authToken.setToken(token);
    authToken.setCode(code);
    authToken.setExpiresAt(OffsetDateTime.now().plusMinutes(CODE_EXPIRY_MINUTES));
    authToken.setInviteCode(inviteCode);

    authTokenRepository.saveAndFlush(authToken);
    try {
      emailService.sendAuthCode(normalizedEmail, code, token.toString());
    } catch (MailException exception) {
      log.error("Failed to deliver authentication email", exception);
      throw new AuthEmailDeliveryException();
    }
  }
}
