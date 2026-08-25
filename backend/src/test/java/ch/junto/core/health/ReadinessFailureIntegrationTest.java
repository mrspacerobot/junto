package ch.junto.core.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
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
    properties = {"spring.datasource.hikari.connection-timeout=2000", "spring.flyway.target=18"})
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
  void databaseOutageIsNotReady() throws Exception {
    expectReadiness(true);

    postgres.stop();

    expectReadiness(false);
  }

  private void expectReadiness(boolean ready) throws Exception {
    int expectedStatus = ready ? 200 : 503;
    String expectedBody = ready ? "{\"status\":\"ok\"}" : "{\"status\":\"unavailable\"}";
    long deadline = System.nanoTime() + 3_000_000_000L;
    HttpResponse<String> response;

    do {
      response =
          httpClient.send(
              HttpRequest.newBuilder(uri("/healthz")).GET().build(),
              HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == expectedStatus) {
        assertThat(response.headers().firstValue("content-type"))
            .hasValueSatisfying(
                value -> assertThat(value).startsWith(MediaType.APPLICATION_JSON_VALUE));
        assertThat(response.body()).isEqualTo(expectedBody);
        return;
      }
      Thread.sleep(50);
    } while (System.nanoTime() < deadline);

    assertThat(response.statusCode()).isEqualTo(expectedStatus);
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + port + path);
  }
}
