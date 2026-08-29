package ch.junto.core.health;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
  private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(2);
  private static final int QUERY_TIMEOUT_SECONDS = 1;
  private static final List<String> REQUIRED_CHAT_RATE_LIMIT_COLUMNS =
      List.of("activity_id", "user_id", "window_started_at", "request_count");
  private static final List<String> REQUIRED_CHAT_RATE_LIMIT_KEY_COLUMNS =
      List.of("activity_id", "user_id");
  private static final ExecutorService READINESS_EXECUTOR =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread thread = new Thread(r, "readiness-check");
            thread.setDaemon(true);
            return thread;
          });

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

      boolean ready = evaluateReadinessWithTimeout();
      cachedResult = new CachedResult(ready, System.nanoTime() + CACHE_DURATION_NANOS);
      return ready;
    }
  }

  private boolean evaluateReadinessWithTimeout() {
    try {
      return READINESS_EXECUTOR
          .submit(this::databaseProvidesRequiredSchemaAndMatchesBuild)
          .get(OPERATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      log.warn("Readiness failed: the database readiness operation timed out", e);
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Readiness failed: the database readiness operation was interrupted", e);
      return false;
    } catch (ExecutionException e) {
      log.warn("Readiness failed: the database readiness operation could not complete", e);
      return false;
    }
  }

  private boolean databaseProvidesRequiredSchemaAndMatchesBuild() {
    return databaseProvidesRequiredSchema() && schemaMatchesThisBuild();
  }

  private boolean databaseProvidesRequiredSchema() {
    try (Connection connection = dataSource.getConnection()) {
      statementWithin(connection);
      return requiredSchemaColumnsAndKeyExist(connection);
    } catch (Exception e) {
      log.warn("Readiness failed: the database or required schema did not answer", e);
      return false;
    }
  }

  private void statementWithin(Connection connection) throws Exception {
    try (Statement statement = connection.createStatement()) {
      statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
      try (ResultSet ignored =
          statement.executeQuery(
              "SELECT activity_id, user_id, window_started_at, request_count FROM chat_rate_limits LIMIT 0")) {
        // probe only
      }
    }
  }

  private boolean requiredSchemaColumnsAndKeyExist(Connection connection) throws Exception {
    DatabaseMetaData metaData = connection.getMetaData();
    try (ResultSet columns = metaData.getColumns(null, null, "chat_rate_limits", null)) {
      Set<String> foundColumns = new HashSet<>();
      while (columns.next()) {
        foundColumns.add(columns.getString("COLUMN_NAME"));
      }
      if (!foundColumns.containsAll(REQUIRED_CHAT_RATE_LIMIT_COLUMNS)) {
        return false;
      }
    }
    try (ResultSet keyColumns = metaData.getPrimaryKeys(null, null, "chat_rate_limits")) {
      List<String> foundKeyColumns = new ArrayList<>();
      while (keyColumns.next()) {
        foundKeyColumns.add(keyColumns.getString("COLUMN_NAME"));
      }
      return foundKeyColumns.equals(REQUIRED_CHAT_RATE_LIMIT_KEY_COLUMNS);
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
