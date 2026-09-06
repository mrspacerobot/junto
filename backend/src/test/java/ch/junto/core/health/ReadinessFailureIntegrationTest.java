package ch.junto.core.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Proves the probe reports non-success for the two deployment failures Render must catch: a schema
 * that does not match the migrations this build ships, and a database that stopped answering.
 *
 * <p>The class owns a private container because both cases damage or destroy the database, and it
 * is ordered so the recoverable schema case runs before the container is stopped for good.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.hikari.connection-timeout=2000",
      "spring.flyway.target=18",
      "spring.jpa.hibernate.ddl-auto=none"
    })
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReadinessFailureIntegrationTest {

  private static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
              DockerImageName.parse("postgis/postgis:16-3.4-alpine")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("junto_readiness")
          .withUsername("test")
          .withPassword("test");

  static {
    postgres.start();
  }

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @LocalServerPort private int port;
  @Autowired private Flyway flyway;
  @Autowired private DataSource dataSource;

  @Test
  @Order(1)
  void shippedMigrationAboveConfiguredTargetIsNotReady() throws Exception {
    expectReadiness(false);

    Flyway currentBuild =
        Flyway.configure()
            .configuration(flyway.getConfiguration())
            .target(MigrationVersion.LATEST)
            .load();
    assertThat(currentBuild.migrate().migrationsExecuted).isPositive();

    expectReadiness(true);
  }

  @Test
  @Order(2)
  void schemaAheadOfThisBuildIsNotReady() throws Exception {
    execute(
        """
        INSERT INTO flyway_schema_history (
            installed_rank, version, description, type, script, checksum,
            installed_by, execution_time, success)
        SELECT max(installed_rank) + 1, '999', 'future migration', 'SQL',
            'V999__future_migration.sql', 0, 'test', 0, true
        FROM flyway_schema_history
        """);
    try {
      expectReadiness(false);
    } finally {
      execute("DELETE FROM flyway_schema_history WHERE version = '999'");
    }

    expectReadiness(true);
  }

  @Test
  @Order(3)
  void schemaBaselinedAheadOfThisBuildIsNotReady() throws Exception {
    execute("TRUNCATE flyway_schema_history");
    execute(
        """
        INSERT INTO flyway_schema_history (
            installed_rank, version, description, type, script, checksum,
            installed_by, execution_time, success)
        VALUES (
            1, '999', 'future baseline', 'BASELINE', '<< Flyway Baseline >>', NULL,
            'test', 0, true)
        """);
    try {
      expectReadiness(false);
    } finally {
      Flyway currentBuild = currentBuildFlyway();
      currentBuild.clean();
      currentBuild.migrate();
    }

    expectReadiness(true);
  }

  @Test
  @Order(4)
  void newestMigrationCapabilityDriftIsNotReady() throws Exception {
    execute("ALTER TABLE access_codes DROP COLUMN created_at");
    try {
      expectReadiness(false);
    } finally {
      execute("ALTER TABLE access_codes ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now()");
    }
    expectReadiness(true);

    execute("ALTER TABLE auth_tokens ALTER COLUMN invite_code TYPE VARCHAR(12)");
    try {
      expectReadiness(false);
    } finally {
      execute("ALTER TABLE auth_tokens ALTER COLUMN invite_code TYPE VARCHAR(64)");
    }
    expectReadiness(true);

    execute("ALTER TABLE auth_tokens RENAME COLUMN invite_code TO invitexcode");
    try {
      expectReadiness(false);
    } finally {
      execute("ALTER TABLE auth_tokens RENAME COLUMN invitexcode TO invite_code");
    }
    expectReadiness(true);
  }

  @Test
  @Order(5)
  void deferrableChatRateLimitKeyIsNotReady() throws Exception {
    execute(
        """
        ALTER TABLE chat_rate_limits
          DROP CONSTRAINT chat_rate_limits_pkey,
          ADD CONSTRAINT chat_rate_limits_pkey
            PRIMARY KEY (activity_id, user_id) DEFERRABLE
        """);
    try {
      expectReadiness(false);
    } finally {
      execute(
          """
          ALTER TABLE chat_rate_limits
            DROP CONSTRAINT chat_rate_limits_pkey,
            ADD CONSTRAINT chat_rate_limits_pkey
              PRIMARY KEY (activity_id, user_id) NOT DEFERRABLE
          """);
    }
    expectReadiness(true);
  }

  @Test
  @Order(6)
  void databaseOutageIsNotReady() throws Exception {
    expectReadiness(true);

    postgres.stop();

    expectReadiness(false);
  }

  private void expectReadiness(boolean ready) throws Exception {
    int expectedStatus = ready ? 200 : 503;
    long deadline = System.nanoTime() + 3_000_000_000L;
    HttpResponse<String> response;

    do {
      response =
          httpClient.send(
              HttpRequest.newBuilder(uri("/healthz")).GET().build(),
              HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == expectedStatus) {
        return;
      }
      Thread.sleep(50);
    } while (System.nanoTime() < deadline);

    assertThat(response.statusCode()).isEqualTo(expectedStatus);
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + port + path);
  }

  private void execute(String sql) throws Exception {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private Flyway currentBuildFlyway() {
    return Flyway.configure()
        .configuration(flyway.getConfiguration())
        .cleanDisabled(false)
        .target(MigrationVersion.LATEST)
        .load();
  }
}
