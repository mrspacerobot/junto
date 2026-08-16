package ch.junto.chat;

import ch.junto.core.exception.RateLimitException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ChatRateLimiter {

  static final int MAX_MESSAGES = 10;
  static final Duration WINDOW = Duration.ofSeconds(10);

  private final JdbcTemplate jdbcTemplate;

  public ChatRateLimiter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void check(UUID activityId, UUID userId) {
    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime resetBefore = now.minus(WINDOW);
    Integer count =
        jdbcTemplate.query(
            """
            INSERT INTO chat_rate_limits(activity_id, user_id, window_started_at, request_count)
            VALUES (?, ?, ?, 1)
            ON CONFLICT (activity_id, user_id) DO UPDATE SET
                window_started_at = CASE
                    WHEN chat_rate_limits.window_started_at <= ? THEN EXCLUDED.window_started_at
                    ELSE chat_rate_limits.window_started_at
                END,
                request_count = CASE
                    WHEN chat_rate_limits.window_started_at <= ? THEN 1
                    ELSE chat_rate_limits.request_count + 1
                END
            WHERE chat_rate_limits.window_started_at <= ?
               OR chat_rate_limits.request_count < ?
            RETURNING request_count
            """,
            resultSet -> resultSet.next() ? resultSet.getInt(1) : null,
            activityId,
            userId,
            now,
            resetBefore,
            resetBefore,
            resetBefore,
            MAX_MESSAGES);
    if (count == null) {
      throw new RateLimitException("Too many messages. Try again shortly.");
    }
  }
}
