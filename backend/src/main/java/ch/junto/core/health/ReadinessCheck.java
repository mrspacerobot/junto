package ch.junto.core.health;

import ch.junto.migration.SchemaCurrency;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.ValidateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.datasource.DelegatingDataSource;
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
  private static final int NETWORK_TIMEOUT_MILLISECONDS =
      Math.toIntExact(TimeUnit.SECONDS.toMillis(QUERY_TIMEOUT_SECONDS));
  private static final String CHAT_RATE_LIMIT_TABLE = "chat_rate_limits";
  private static final String AUTH_TOKEN_TABLE = "auth_tokens";
  private static final String INVITE_CODE_COLUMN = "invite_code";
  private static final int REQUIRED_INVITE_CODE_LENGTH = 64;

  /** Read-only probes for the capabilities the newest shipped migrations must have delivered. */
  private static final List<String> SCHEMA_CAPABILITY_PROBES =
      List.of(
          "SELECT activity_id, user_id, window_started_at, request_count FROM chat_rate_limits LIMIT 0",
          "SELECT id, code, label, created_by, revoked_at, created_at FROM access_codes LIMIT 0",
          "SELECT id, access_code_id, user_id, redeemed_at FROM access_code_redemptions LIMIT 0");

  private static final String CHAT_RATE_LIMIT_KEY_CAPABILITY_PROBE =
      """
      WITH candidate_indexes AS (
          SELECT index_definition.indimmediate
          FROM pg_catalog.pg_index AS index_definition
          JOIN pg_catalog.pg_class AS table_definition
            ON table_definition.oid = index_definition.indrelid
          JOIN pg_catalog.pg_namespace AS table_schema
            ON table_schema.oid = table_definition.relnamespace
          WHERE table_schema.nspname = current_schema()
            AND table_definition.relname = 'chat_rate_limits'
            AND index_definition.indisunique
            AND index_definition.indisvalid
            AND index_definition.indpred IS NULL
            AND index_definition.indexprs IS NULL
            AND index_definition.indnkeyatts = 2
            AND (
                SELECT array_agg(attribute_definition.attname::text
                                 ORDER BY attribute_definition.attname::text)
                FROM unnest(index_definition.indkey::smallint[])
                     WITH ORDINALITY AS key_definition(attnum, key_position)
                JOIN pg_catalog.pg_attribute AS attribute_definition
                  ON attribute_definition.attrelid = index_definition.indrelid
                 AND attribute_definition.attnum = key_definition.attnum
                WHERE key_definition.key_position <= index_definition.indnkeyatts
            ) = ARRAY['activity_id', 'user_id']
      )
      SELECT EXISTS (SELECT FROM candidate_indexes)
         AND NOT EXISTS (
             SELECT FROM candidate_indexes WHERE NOT indimmediate
         ) AS compatible
      """;
  private static final ExecutorService READINESS_EXECUTOR =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread thread = new Thread(r, "readiness-check");
            thread.setDaemon(true);
            return thread;
          });
  private static final Executor NETWORK_TIMEOUT_EXECUTOR =
      task -> {
        Thread thread = new Thread(task, "readiness-network-timeout");
        thread.setDaemon(true);
        thread.start();
      };

  private final DataSource dataSource;
  private final ObjectProvider<Flyway> flyway;
  private volatile CachedResult cachedResult;
  private volatile Flyway currentBuildMigrations;

  public ReadinessCheck(DataSource dataSource, ObjectProvider<Flyway> flyway) {
    this.dataSource = new NetworkTimeoutDataSource(dataSource);
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

  /**
   * A probe that outlives its deadline is cancelled rather than abandoned. The executor runs one
   * task at a time, so leaving a stalled probe queued would make every later probe wait behind the
   * whole outage instead of recovering with the database.
   */
  private boolean evaluateReadinessWithTimeout() {
    Future<Boolean> probe =
        READINESS_EXECUTOR.submit(this::databaseProvidesRequiredSchemaAndMatchesBuild);
    try {
      return probe.get(OPERATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      probe.cancel(true);
      log.warn("Readiness failed: the database readiness operation timed out", e);
      return false;
    } catch (InterruptedException e) {
      probe.cancel(true);
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
      return probeShippedSchemaCapabilities(connection)
          && requiredInviteCodeMetadataExists(connection);
    } catch (Exception e) {
      log.warn("Readiness failed: the database or required schema did not answer", e);
      return false;
    }
  }

  private boolean probeShippedSchemaCapabilities(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
      for (String probe : SCHEMA_CAPABILITY_PROBES) {
        try (ResultSet ignored = statement.executeQuery(probe)) {
          // probe only
        }
      }
      return requiredChatRateLimitKeyExists(statement);
    }
  }

  private boolean requiredInviteCodeMetadataExists(Connection connection) throws SQLException {
    DatabaseMetaData metaData = connection.getMetaData();
    String schema = connection.getSchema();
    return requiredInviteCodeWidthExists(metaData, schema);
  }

  private boolean requiredChatRateLimitKeyExists(Statement statement) throws SQLException {
    try (ResultSet capability = statement.executeQuery(CHAT_RATE_LIMIT_KEY_CAPABILITY_PROBE)) {
      if (capability.next() && capability.getBoolean("compatible")) {
        return true;
      }
      log.warn("Readiness failed: {} key is not ON CONFLICT compatible", CHAT_RATE_LIMIT_TABLE);
      return false;
    }
  }

  private boolean requiredInviteCodeWidthExists(DatabaseMetaData metaData, String schema)
      throws SQLException {
    try (ResultSet columns =
        metaData.getColumns(null, schema, AUTH_TOKEN_TABLE, INVITE_CODE_COLUMN)) {
      while (columns.next()) {
        if (Objects.equals(schema, columns.getString("TABLE_SCHEM"))
            && AUTH_TOKEN_TABLE.equals(columns.getString("TABLE_NAME"))
            && INVITE_CODE_COLUMN.equals(columns.getString("COLUMN_NAME"))
            && columns.getInt("COLUMN_SIZE") >= REQUIRED_INVITE_CODE_LENGTH) {
          return true;
        }
      }
      log.warn(
          "Readiness failed: {}.{} does not accept at least {} characters",
          AUTH_TOKEN_TABLE,
          INVITE_CODE_COLUMN,
          REQUIRED_INVITE_CODE_LENGTH);
      return false;
    }
  }

  private boolean schemaMatchesThisBuild() {
    try {
      Flyway currentBuild = currentBuildMigrations();
      if (currentBuild == null) {
        log.warn("Readiness failed: without Flyway the applied schema cannot be verified");
        return false;
      }
      ValidateResult validation = currentBuild.validateWithResult();
      if (!validation.validationSuccessful) {
        log.warn(
            "Readiness failed: schema validation reported {}", validation.getAllErrorMessages());
        return false;
      }
      if (!SchemaCurrency.hasOnlyCurrentMigrations(currentBuild)) {
        log.warn("Readiness failed: the database schema is not current for this build");
        return false;
      }
      return true;
    } catch (Exception e) {
      log.warn("Readiness failed: schema validation could not run", e);
      return false;
    }
  }

  /**
   * Resolving the shipped migrations rescans and rechecksums the whole migration set, so it happens
   * on first use and is then reused: the set cannot change without a new build.
   */
  private Flyway currentBuildMigrations() {
    Flyway resolved = currentBuildMigrations;
    if (resolved != null) {
      return resolved;
    }
    Flyway configured = flyway.getIfAvailable();
    if (configured == null) {
      return null;
    }
    resolved =
        Flyway.configure()
            .configuration(configured.getConfiguration())
            .dataSource(dataSource)
            .target(MigrationVersion.LATEST)
            .load();
    currentBuildMigrations = resolved;
    return resolved;
  }

  private record CachedResult(boolean ready, long expiresAt) {
    private boolean isCurrentAt(long now) {
      return now < expiresAt;
    }
  }

  private static final class NetworkTimeoutDataSource extends DelegatingDataSource {

    private NetworkTimeoutDataSource(DataSource targetDataSource) {
      super(targetDataSource);
    }

    @Override
    public Connection getConnection() throws SQLException {
      return applyNetworkTimeout(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
      return applyNetworkTimeout(super.getConnection(username, password));
    }

    private Connection applyNetworkTimeout(Connection connection) throws SQLException {
      try {
        connection.setNetworkTimeout(NETWORK_TIMEOUT_EXECUTOR, NETWORK_TIMEOUT_MILLISECONDS);
        return connection;
      } catch (SQLException | RuntimeException e) {
        try {
          connection.close();
        } catch (SQLException closeFailure) {
          e.addSuppressed(closeFailure);
        }
        throw e;
      }
    }
  }
}
