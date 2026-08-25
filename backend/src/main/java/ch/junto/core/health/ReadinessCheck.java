package ch.junto.core.health;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.ValidateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Decides whether this instance may serve traffic. Readiness holds only while the database answers
 * and the applied schema matches the migrations this build ships, so a deployment whose migrations
 * did not apply never becomes a traffic candidate.
 *
 * <p>Mail delivery is deliberately not a dependency: an SMTP outage degrades one feature and must
 * not take the application out of rotation.
 *
 * <p>Failures are described only in the application log. The caller learns readiness and nothing
 * else; see {@link ReadinessController}.
 */
@Component
public class ReadinessCheck {

  private static final Logger log = LoggerFactory.getLogger(ReadinessCheck.class);
  private static final long CACHE_DURATION_NANOS = TimeUnit.SECONDS.toNanos(1);
  private static final int QUERY_TIMEOUT_SECONDS = 1;
  private static final String REQUIRED_SCHEMA_PROBE =
      """
      SELECT activity_id, user_id, window_started_at, request_count
      FROM chat_rate_limits
      LIMIT 0
      """;

  private final DataSource dataSource;
  private final ObjectProvider<Flyway> flyway;
  private volatile CachedResult cachedResult;

  public ReadinessCheck(DataSource dataSource, ObjectProvider<Flyway> flyway) {
    this.dataSource = dataSource;
    this.flyway = flyway;
  }

  public boolean isReady() {
    long now = System.nanoTime();
    CachedResult cached = cachedResult;
    if (cached != null && cached.isCurrentAt(now)) {
      return cached.ready();
    }

    synchronized (this) {
      now = System.nanoTime();
      cached = cachedResult;
      if (cached != null && cached.isCurrentAt(now)) {
        return cached.ready();
      }

      boolean ready = databaseProvidesRequiredSchema() && schemaMatchesThisBuild();
      cachedResult = new CachedResult(ready, System.nanoTime() + CACHE_DURATION_NANOS);
      return ready;
    }
  }

  private boolean databaseProvidesRequiredSchema() {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
      try (ResultSet ignored = statement.executeQuery(REQUIRED_SCHEMA_PROBE)) {
        return true;
      }
    } catch (Exception e) {
      log.warn("Readiness failed: the database or required schema did not answer", e);
      return false;
    }
  }

  private boolean schemaMatchesThisBuild() {
    Flyway configured = flyway.getIfAvailable();
    if (configured == null) {
      log.warn("Readiness failed: without Flyway the applied schema cannot be verified");
      return false;
    }
    try {
      Flyway currentBuild =
          Flyway.configure()
              .configuration(configured.getConfiguration())
              .target(MigrationVersion.LATEST)
              .load();
      ValidateResult validation = currentBuild.validateWithResult();
      if (!validation.validationSuccessful) {
        log.warn(
            "Readiness failed: schema validation reported {}", validation.getAllErrorMessages());
      }
      return validation.validationSuccessful;
    } catch (Exception e) {
      log.warn("Readiness failed: schema validation could not run", e);
      return false;
    }
  }

  private record CachedResult(boolean ready, long expiresAt) {
    private boolean isCurrentAt(long now) {
      return now < expiresAt;
    }
  }
}
