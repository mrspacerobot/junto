package ch.junto.bootstrap;

import static ch.junto.bootstrap.BootstrapAdminCommand.ACCESS_CODE_VARIABLE;
import static ch.junto.bootstrap.BootstrapAdminCommand.EMAIL_VARIABLE;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.servlet.ServletWebServerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@DisplayName("bootstrap-admin command")
class BootstrapAdminCommandIntegrationTest {

  private static final String ADMIN_EMAIL = "Captain@Junto.Example";
  private static final String NORMALIZED_ADMIN_EMAIL = "captain@junto.example";
  private static final String ACCESS_CODE = "junto-pilot-2026";
  private static final String NORMALIZED_ACCESS_CODE = "JUNTO-PILOT-2026";

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
              DockerImageName.parse("postgis/postgis:16-3.4-alpine")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("bootstrap_test")
          .withUsername("test")
          .withPassword("test");

  @BeforeEach
  void emptyDatabase() {
    flyway().clean();
  }

  @Test
  @DisplayName("refuses to write anything into a schema that is not current")
  void refusesWhenTheSchemaIsNotCurrent() throws Exception {
    int status = bootstrap(ADMIN_EMAIL, ACCESS_CODE);

    assertThat(status).isEqualTo(BootstrapAdminCommand.FAILED);
    try (Connection connection = connection()) {
      assertThat(tableExists(connection, "users")).isFalse();
      assertThat(tableExists(connection, "access_codes")).isFalse();
    }
  }

  @Test
  @DisplayName("refuses when the schema is behind this build")
  void refusesWhenTheSchemaIsBehind() throws Exception {
    migrateTo("19");

    int status = bootstrap(ADMIN_EMAIL, ACCESS_CODE);

    assertThat(status).isEqualTo(BootstrapAdminCommand.FAILED);
    try (Connection connection = connection()) {
      assertThat(count(connection, "SELECT count(*) FROM users")).isZero();
    }
  }

  @Test
  @DisplayName("refuses when the schema is ahead of this build")
  void refusesWhenTheSchemaIsAhead() throws Exception {
    migrate();
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          INSERT INTO flyway_schema_history (
              installed_rank, version, description, type, script, checksum,
              installed_by, execution_time, success)
          SELECT max(installed_rank) + 1, '999', 'future migration', 'SQL',
              'V999__future_migration.sql', 0, 'test', 0, true
          FROM flyway_schema_history
          """);
    }

    int status = bootstrap(ADMIN_EMAIL, ACCESS_CODE);

    assertThat(status).isEqualTo(BootstrapAdminCommand.FAILED);
    try (Connection connection = connection()) {
      assertThat(count(connection, "SELECT count(*) FROM users")).isZero();
      assertThat(count(connection, "SELECT count(*) FROM access_codes")).isZero();
    }
  }

  @Test
  @DisplayName("refuses when schema history is baselined above this build")
  void refusesWhenTheSchemaIsBaselinedAboveThisBuild() throws Exception {
    migrate();
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          INSERT INTO flyway_schema_history (
              installed_rank, version, description, type, script, checksum,
              installed_by, execution_time, success)
          SELECT max(installed_rank) + 1, '999', 'operator baseline', 'BASELINE',
              'operator baseline', NULL, 'test', 0, true
          FROM flyway_schema_history
          """);
    }

    int status = bootstrap(ADMIN_EMAIL, ACCESS_CODE);

    assertThat(status).isEqualTo(BootstrapAdminCommand.FAILED);
    try (Connection connection = connection()) {
      assertThat(count(connection, "SELECT count(*) FROM users")).isZero();
      assertThat(count(connection, "SELECT count(*) FROM access_codes")).isZero();
    }
  }

  @Test
  @DisplayName("refuses when schema history contains a deleted migration")
  void refusesWhenSchemaHistoryContainsADeletedMigration() throws Exception {
    migrate();
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          INSERT INTO flyway_schema_history (
              installed_rank, version, description, type, script, checksum,
              installed_by, execution_time, success)
          SELECT max(installed_rank) + 1, '15', 'removed migration', 'SQL',
              'V015__removed_migration.sql', 0, 'test', 0, true
          FROM flyway_schema_history
          """);
      statement.executeUpdate(
          """
          INSERT INTO flyway_schema_history (
              installed_rank, version, description, type, script, checksum,
              installed_by, execution_time, success)
          SELECT max(installed_rank) + 1, '15', 'removed migration', 'DELETE',
              'V015__removed_migration.sql', 0, 'test', 0, true
          FROM flyway_schema_history
          """);
    }

    assertThat(flyway().validateWithResult().validationSuccessful).isTrue();
    assertThat(bootstrap(ADMIN_EMAIL, ACCESS_CODE)).isEqualTo(BootstrapAdminCommand.FAILED);

    try (Connection connection = connection()) {
      assertThat(count(connection, "SELECT count(*) FROM users")).isZero();
      assertThat(count(connection, "SELECT count(*) FROM access_codes")).isZero();
    }
  }

  @Test
  @DisplayName(
      "creates the administrator and the initial access code, then changes nothing on a rerun")
  void createsTheAdministratorAndAccessCodeIdempotently() throws Exception {
    migrate();

    assertThat(bootstrap(ADMIN_EMAIL, ACCESS_CODE)).isEqualTo(BootstrapAdminCommand.SUCCESS);

    try (Connection connection = connection()) {
      assertThat(count(connection, "SELECT count(*) FROM users")).isEqualTo(1);
      assertThat(value(connection, "SELECT email FROM users")).isEqualTo(NORMALIZED_ADMIN_EMAIL);
      assertThat(value(connection, "SELECT role FROM users")).isEqualTo("ADMIN");
      assertThat(value(connection, "SELECT status FROM users")).isEqualTo("ACTIVE");
      assertThat(value(connection, "SELECT onboarded::text FROM users")).isEqualTo("false");
      assertThat(count(connection, "SELECT invite_codes_remaining FROM users")).isPositive();
      assertThat(value(connection, "SELECT code FROM access_codes"))
          .isEqualTo(NORMALIZED_ACCESS_CODE);
      assertThat(value(connection, "SELECT label FROM access_codes")).isEqualTo("bootstrap");
      assertThat(value(connection, "SELECT revoked_at FROM access_codes")).isNull();
    }

    assertThat(bootstrap(" CAPTAIN@junto.example ", " Junto-Pilot-2026 "))
        .isEqualTo(BootstrapAdminCommand.SUCCESS);

    try (Connection connection = connection()) {
      assertThat(count(connection, "SELECT count(*) FROM users")).isEqualTo(1);
      assertThat(count(connection, "SELECT count(*) FROM access_codes")).isEqualTo(1);
    }
  }

  @Test
  @DisplayName("serializes concurrent invocations for one normalized administrator identity")
  void serializesConcurrentInvocations() throws Exception {
    migrate();
    delayUserInserts();
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    try {
      Future<Integer> first =
          executor.submit(
              () -> {
                ready.countDown();
                start.await();
                return bootstrap(ADMIN_EMAIL, ACCESS_CODE);
              });
      Future<Integer> second =
          executor.submit(
              () -> {
                ready.countDown();
                start.await();
                return bootstrap(" CAPTAIN@junto.example ", " Junto-Pilot-2026 ");
              });

      assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(first.get(1, TimeUnit.MINUTES)).isEqualTo(BootstrapAdminCommand.SUCCESS);
      assertThat(second.get(1, TimeUnit.MINUTES)).isEqualTo(BootstrapAdminCommand.SUCCESS);
    } finally {
      executor.shutdownNow();
    }

    try (Connection connection = connection()) {
      assertThat(count(connection, "SELECT count(*) FROM users")).isEqualTo(1);
      assertThat(count(connection, "SELECT count(*) FROM access_codes")).isEqualTo(1);
    }
  }

  @Test
  @DisplayName("does not override later account administration on a rerun")
  void doesNotOverrideLaterAccountAdministration() throws Exception {
    migrate();
    assertThat(bootstrap(ADMIN_EMAIL, ACCESS_CODE)).isEqualTo(BootstrapAdminCommand.SUCCESS);

    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          "UPDATE users SET role = 'MEMBER', status = 'SUSPENDED', invite_codes_remaining = 0");
    }

    String output =
        captureOutput(
            () ->
                assertThat(bootstrap(ADMIN_EMAIL, ACCESS_CODE))
                    .isEqualTo(BootstrapAdminCommand.SUCCESS));

    try (Connection connection = connection()) {
      assertThat(value(connection, "SELECT role FROM users")).isEqualTo("MEMBER");
      assertThat(value(connection, "SELECT status FROM users")).isEqualTo("SUSPENDED");
      assertThat(count(connection, "SELECT invite_codes_remaining FROM users")).isZero();
    }
    assertThat(output)
        .contains("administrator ALREADY_BOOTSTRAPPED (role=MEMBER, status=SUSPENDED)");
  }

  @Test
  @DisplayName("promotes an existing account instead of creating a second one")
  void promotesAnExistingAccount() throws Exception {
    migrate();
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          "INSERT INTO users(email, display_name, role, status, invite_codes_remaining) VALUES ('"
              + NORMALIZED_ADMIN_EMAIL
              + "', 'Existing', 'MEMBER', 'SUSPENDED', 0)");
    }

    assertThat(bootstrap(ADMIN_EMAIL, ACCESS_CODE)).isEqualTo(BootstrapAdminCommand.SUCCESS);

    try (Connection connection = connection()) {
      assertThat(count(connection, "SELECT count(*) FROM users")).isEqualTo(1);
      assertThat(value(connection, "SELECT role FROM users")).isEqualTo("ADMIN");
      assertThat(value(connection, "SELECT status FROM users")).isEqualTo("ACTIVE");
      assertThat(value(connection, "SELECT display_name FROM users")).isEqualTo("Existing");
      assertThat(count(connection, "SELECT invite_codes_remaining FROM users")).isEqualTo(1);
    }
  }

  @Test
  @DisplayName("refuses a second, different administrator identity")
  void refusesASecondAdministrator() throws Exception {
    migrate();
    bootstrap(ADMIN_EMAIL, ACCESS_CODE);

    assertThat(bootstrap("someone.else@junto.example", ACCESS_CODE))
        .isEqualTo(BootstrapAdminCommand.FAILED);

    try (Connection connection = connection()) {
      assertThat(count(connection, "SELECT count(*) FROM users")).isEqualTo(1);
      assertThat(value(connection, "SELECT email FROM users")).isEqualTo(NORMALIZED_ADMIN_EMAIL);
    }
  }

  @Test
  @DisplayName("refuses a second, different initial access code")
  void refusesASecondInitialAccessCode() throws Exception {
    migrate();
    bootstrap(ADMIN_EMAIL, ACCESS_CODE);

    assertThat(bootstrap(ADMIN_EMAIL, "junto-something-else"))
        .isEqualTo(BootstrapAdminCommand.FAILED);

    try (Connection connection = connection()) {
      assertThat(count(connection, "SELECT count(*) FROM access_codes")).isEqualTo(1);
      assertThat(value(connection, "SELECT code FROM access_codes"))
          .isEqualTo(NORMALIZED_ACCESS_CODE);
    }
  }

  @Test
  @DisplayName("refuses a code another shared access code already holds, without echoing it")
  void refusesACodeAnotherAccessCodeAlreadyHolds() throws Exception {
    migrate();
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          "INSERT INTO users(email, display_name, role, status)"
              + " VALUES ('quartermaster@junto.example', 'Quartermaster', 'MEMBER', 'ACTIVE')");
      statement.executeUpdate(
          "INSERT INTO access_codes(code, label, created_by) SELECT '"
              + NORMALIZED_ACCESS_CODE
              + "', 'pilot', id FROM users WHERE email = 'quartermaster@junto.example'");
    }

    String output =
        captureOutput(
            () ->
                assertThat(bootstrap(ADMIN_EMAIL, ACCESS_CODE))
                    .isEqualTo(BootstrapAdminCommand.FAILED));

    try (Connection connection = connection()) {
      assertThat(count(connection, "SELECT count(*) FROM access_codes")).isEqualTo(1);
      assertThat(value(connection, "SELECT label FROM access_codes")).isEqualTo("pilot");
      assertThat(count(connection, "SELECT count(*) FROM users")).isEqualTo(1);
    }
    assertThat(output)
        .doesNotContainIgnoringCase(ACCESS_CODE)
        .doesNotContainIgnoringCase(NORMALIZED_ACCESS_CODE)
        .doesNotContainIgnoringCase(ADMIN_EMAIL)
        .doesNotContainIgnoringCase(NORMALIZED_ADMIN_EMAIL);
  }

  @Test
  @DisplayName("refuses access codes and administrator identities that cannot be used")
  void refusesUnusableInputs() throws Exception {
    migrate();

    assertThat(bootstrap(ADMIN_EMAIL, "short")).isEqualTo(BootstrapAdminCommand.FAILED);
    assertThat(bootstrap(ADMIN_EMAIL, "junto pilot code")).isEqualTo(BootstrapAdminCommand.FAILED);
    assertThat(bootstrap("not-an-email", ACCESS_CODE)).isEqualTo(BootstrapAdminCommand.FAILED);
    assertThat(bootstrap("a".repeat(65) + "@junto.example", ACCESS_CODE))
        .isEqualTo(BootstrapAdminCommand.FAILED);
    assertThat(bootstrap("\"captain one\"@junto.example", ACCESS_CODE))
        .isEqualTo(BootstrapAdminCommand.FAILED);

    try (Connection connection = connection()) {
      assertThat(count(connection, "SELECT count(*) FROM access_codes")).isZero();
      assertThat(count(connection, "SELECT count(*) FROM users")).isZero();
    }
  }

  @Test
  @DisplayName("reserves the generated personal invite namespace")
  void reservesThePersonalInviteNamespace() throws Exception {
    migrate();

    assertThat(bootstrap(ADMIN_EMAIL, "JUNTO-ABC123")).isEqualTo(BootstrapAdminCommand.FAILED);

    try (Connection connection = connection()) {
      assertThat(count(connection, "SELECT count(*) FROM access_codes")).isZero();
      assertThat(count(connection, "SELECT count(*) FROM users")).isZero();
    }
  }

  @Test
  @DisplayName("takes its inputs only from the environment, never from the command line")
  void takesItsInputsOnlyFromTheEnvironment() {
    assertThat(BootstrapAdminCommand.matches(new String[] {})).isFalse();
    assertThat(BootstrapAdminCommand.matches(new String[] {"--spring.profiles.active=prod"}))
        .isFalse();
    assertThat(BootstrapAdminCommand.matches(new String[] {"bootstrap-admin"})).isTrue();

    assertThat(
            BootstrapAdminCommand.run(
                new String[] {"bootstrap-admin", "--email=captain@junto.example"},
                environment(ADMIN_EMAIL, ACCESS_CODE)))
        .isEqualTo(BootstrapAdminCommand.USAGE);
    assertThat(BootstrapAdminCommand.run(new String[] {"bootstrap-admin"}, name -> null))
        .isEqualTo(BootstrapAdminCommand.USAGE);
  }

  @Test
  @DisplayName("runs in a context with no web server, no security filter chain and no scheduler")
  void startsNoWebServerOrSchedulerAndExposesNoHandler() {
    migrate();

    try (ConfigurableApplicationContext context =
        new SpringApplicationBuilder(BootstrapContext.class)
            .web(WebApplicationType.NONE)
            .bannerMode(Banner.Mode.OFF)
            .run(datasourceProperties())) {

      assertThat(context).isNotInstanceOf(WebApplicationContext.class);
      assertThat(context.getBeanNamesForType(ServletWebServerFactory.class)).isEmpty();
      assertThat(context.getBeanNamesForType(SecurityFilterChain.class)).isEmpty();
      assertThat(context.getBeanNamesForType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
      assertThat(context.getBeanNamesForType(TaskScheduler.class)).isEmpty();
      assertThat(context.getBeanNamesForAnnotation(Controller.class)).isEmpty();
      assertThat(context.getBeanNamesForAnnotation(RestController.class)).isEmpty();
      assertThat(context.getBeanNamesForType(BootstrapAdminService.class)).hasSize(1);
    }
  }

  @Test
  @DisplayName("keeps the administrator address and the access code out of its audit trail")
  void printsNoIdentityOrSecret() throws Exception {
    migrate();

    String output = captureOutput(() -> bootstrap(ADMIN_EMAIL, ACCESS_CODE));

    assertThat(output).doesNotContainIgnoringCase(ADMIN_EMAIL);
    assertThat(output).doesNotContainIgnoringCase(NORMALIZED_ADMIN_EMAIL);
    assertThat(output).doesNotContainIgnoringCase("captain");
    assertThat(output).doesNotContainIgnoringCase(ACCESS_CODE);
    assertThat(output).doesNotContainIgnoringCase(NORMALIZED_ACCESS_CODE);
    assertThat(output).doesNotContain("sha256:");
    assertThat(output).contains("reusable=true", "expiry=none");
    assertThat(output).contains(EMAIL_VARIABLE, ACCESS_CODE_VARIABLE);
  }

  @Test
  @DisplayName("reports creation only after the bootstrap transaction commits")
  void reportsCreationOnlyAfterCommit() throws Exception {
    migrate();
    rejectAccessCodeInserts();

    String output =
        captureOutput(
            () ->
                assertThat(bootstrap(ADMIN_EMAIL, ACCESS_CODE))
                    .isEqualTo(BootstrapAdminCommand.FAILED));

    try (Connection connection = connection()) {
      assertThat(count(connection, "SELECT count(*) FROM users")).isZero();
      assertThat(count(connection, "SELECT count(*) FROM access_codes")).isZero();
    }
    assertThat(output)
        .doesNotContain(
            "administrator CREATED", "Shared access code created", "initial access code CREATED")
        .doesNotContainIgnoringCase(ADMIN_EMAIL)
        .doesNotContainIgnoringCase(NORMALIZED_ADMIN_EMAIL)
        .doesNotContainIgnoringCase(ACCESS_CODE)
        .doesNotContainIgnoringCase(NORMALIZED_ACCESS_CODE);
  }

  private int bootstrap(String email, String accessCode) {
    return BootstrapAdminCommand.run(
        new String[] {BootstrapAdminCommand.COMMAND},
        environment(email, accessCode),
        datasourceProperties());
  }

  private static UnaryOperator<String> environment(String email, String accessCode) {
    Map<String, String> variables = new HashMap<>();
    variables.put(EMAIL_VARIABLE, email);
    variables.put(ACCESS_CODE_VARIABLE, accessCode);
    return variables::get;
  }

  private static String[] datasourceProperties() {
    return new String[] {
      "--spring.datasource.url=" + postgres.getJdbcUrl(),
      "--spring.datasource.username=" + postgres.getUsername(),
      "--spring.datasource.password=" + postgres.getPassword()
    };
  }

  private static String captureOutput(Runnable action) {
    PrintStream original = System.out;
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
    try {
      action.run();
    } finally {
      System.setOut(original);
    }
    return captured.toString(StandardCharsets.UTF_8);
  }

  private static void migrate() {
    flyway().migrate();
  }

  private static void migrateTo(String version) {
    Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .locations("classpath:db/migration")
        .cleanDisabled(false)
        .target(version)
        .load()
        .migrate();
  }

  private static void delayUserInserts() throws Exception {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          CREATE FUNCTION delay_user_insert()
          RETURNS trigger
          LANGUAGE plpgsql
          AS $$
          BEGIN
              PERFORM pg_sleep(2);
              RETURN NEW;
          END;
          $$
          """);
      statement.execute(
          """
          CREATE TRIGGER delay_user_insert
          BEFORE INSERT ON users
          FOR EACH ROW EXECUTE FUNCTION delay_user_insert()
          """);
    }
  }

  private static void rejectAccessCodeInserts() throws Exception {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          CREATE FUNCTION reject_access_code_insert()
          RETURNS trigger
          LANGUAGE plpgsql
          AS $$
          BEGIN
              RAISE EXCEPTION 'access code insert rejected'
                  USING DETAIL = 'Key (code)=(' || NEW.code || ') rejected';
              RETURN NEW;
          END;
          $$
          """);
      statement.execute(
          """
          CREATE TRIGGER reject_access_code_insert
          BEFORE INSERT ON access_codes
          FOR EACH ROW EXECUTE FUNCTION reject_access_code_insert()
          """);
    }
  }

  private static Flyway flyway() {
    return Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .locations("classpath:db/migration")
        .cleanDisabled(false)
        .load();
  }

  private static Connection connection() throws Exception {
    return DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }

  private static boolean tableExists(Connection connection, String table) throws Exception {
    return Boolean.parseBoolean(
        value(
            connection,
            "SELECT (EXISTS (SELECT 1 FROM information_schema.tables"
                + " WHERE table_schema = 'public' AND table_name = '"
                + table
                + "'))::text"));
  }

  private static long count(Connection connection, String sql) throws Exception {
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      result.next();
      return result.getLong(1);
    }
  }

  private static String value(Connection connection, String sql) throws Exception {
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      result.next();
      return result.getString(1);
    }
  }
}
