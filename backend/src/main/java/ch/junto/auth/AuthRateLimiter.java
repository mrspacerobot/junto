package ch.junto.auth;

import ch.junto.core.exception.RateLimitException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Database-backed fixed-window limits shared by every application instance. */
@Component
public class AuthRateLimiter {

  private static final Duration REQUEST_WINDOW = Duration.ofMinutes(15);
  private static final Duration VERIFY_WINDOW = Duration.ofMinutes(10);
  private static final int REQUESTS_PER_ACCOUNT = 3;
  private static final int REQUESTS_PER_SOURCE = 20;
  private static final int VERIFICATIONS_PER_ACCOUNT = 10;
  private static final int VERIFICATIONS_PER_SOURCE = 50;

  private final JdbcTemplate jdbcTemplate;

  public AuthRateLimiter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void checkCodeRequest(String email, String source) {
    check("request-account", email, REQUESTS_PER_ACCOUNT, REQUEST_WINDOW);
    check("request-source", source, REQUESTS_PER_SOURCE, REQUEST_WINDOW);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void checkCodeVerification(String email, String source) {
    check("verify-account", email, VERIFICATIONS_PER_ACCOUNT, VERIFY_WINDOW);
    check("verify-source", source, VERIFICATIONS_PER_SOURCE, VERIFY_WINDOW);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void checkTokenVerification(String token, String source) {
    check("verify-token", token, VERIFICATIONS_PER_ACCOUNT, VERIFY_WINDOW);
    check("verify-source", source, VERIFICATIONS_PER_SOURCE, VERIFY_WINDOW);
  }

  private void check(String action, String identity, int limit, Duration window) {
    String key = sha256(identity == null ? "unknown" : identity.trim().toLowerCase());
    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime resetBefore = now.minus(window);

    Integer count =
        jdbcTemplate.query(
            """
            INSERT INTO auth_rate_limits(rate_key, action, window_started_at, request_count)
            VALUES (?, ?, ?, 1)
            ON CONFLICT (rate_key, action) DO UPDATE SET
                window_started_at = CASE
                    WHEN auth_rate_limits.window_started_at <= ? THEN EXCLUDED.window_started_at
                    ELSE auth_rate_limits.window_started_at
                END,
                request_count = CASE
                    WHEN auth_rate_limits.window_started_at <= ? THEN 1
                    ELSE auth_rate_limits.request_count + 1
                END
            WHERE auth_rate_limits.window_started_at <= ?
               OR auth_rate_limits.request_count < ?
            RETURNING request_count
            """,
            resultSet -> resultSet.next() ? resultSet.getInt(1) : null,
            key,
            action,
            now,
            resetBefore,
            resetBefore,
            resetBefore,
            limit);

    if (count == null) {
      throw new RateLimitException("Too many authentication attempts. Try again later.");
    }
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }
}
