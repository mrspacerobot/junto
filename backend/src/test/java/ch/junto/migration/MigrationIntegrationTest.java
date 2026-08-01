package ch.junto.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class MigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        DockerImageName.parse("postgis/postgis:16-3.4-alpine")
            .asCompatibleSubstituteFor("postgres"))
        .withDatabaseName("migration_test")
        .withUsername("test")
        .withPassword("test");

    @BeforeEach
    void cleanDatabase() {
        flyway("classpath:db/migration").clean();
    }

    @Test
    void emptyInstallUsesBaselineAndReachesCurrentSchema() throws Exception {
        Flyway flyway = flyway("classpath:db/migration");
        flyway.migrate();
        flyway.validate();

        try (Connection connection = connection()) {
            assertThat(tableExists(connection, "users")).isTrue();
            assertThat(tableExists(connection, "activities")).isTrue();
            assertThat(tableExists(connection, "spring_session")).isTrue();
            assertThat(tableExists(connection, "auth_rate_limits")).isTrue();
            assertThat(tableExists(connection, "chat_rate_limits")).isTrue();
            assertThat(queryLong(connection,
                "SELECT count(*) FROM flyway_schema_history WHERE CAST(version AS integer) = 16 AND type LIKE '%BASELINE%'"))
                .isEqualTo(1);
            assertThat(queryString(connection,
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1"))
                .isIn("19", "019");
        }
    }

    @Test
    void representativeOndaUpgradePreservesDomainDataAndExpiresLegacySessions() throws Exception {
        Flyway ondaBeforeDomain = Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations("classpath:db/onda-upgrade")
            .target("14")
            .load();
        ondaBeforeDomain.migrate();
        executeSqlResource("db/onda-upgrade/onda_domain_schema.sql");
        flyway("classpath:db/onda-upgrade").migrate();

        UUID userId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();

        try (Connection connection = connection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO users(id, email, display_name) VALUES (?, 'legacy@onda.test', 'Legacy User')
                """)) {
                statement.setObject(1, userId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO activities(id, creator_id, type_slug, title, starts_at, custom_fields)
                VALUES (?, ?, 'cycling', 'Preserve me', now() + interval '1 day', '{"distance":42}')
                """)) {
                statement.setObject(1, activityId);
                statement.setObject(2, userId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO spring_session(
                    primary_id, session_id, creation_time, last_access_time,
                    max_inactive_interval, expiry_time, principal_name)
                VALUES ('legacy-primary', 'legacy-session', 1, 1, 1800, 9999999999999, 'legacy@onda.test')
                """)) {
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO spring_session_attributes(session_primary_id, attribute_name, attribute_bytes)
                VALUES ('legacy-primary', 'SPRING_SECURITY_CONTEXT', ?)
                """)) {
                statement.setBytes(1, new byte[] {1, 2, 3});
                statement.executeUpdate();
            }
        }

        Flyway junto = flyway("classpath:db/migration");
        junto.migrate();
        junto.validate();

        try (Connection connection = connection()) {
            assertThat(queryString(connection,
                "SELECT email FROM users WHERE id = '" + userId + "'"))
                .isEqualTo("legacy@onda.test");
            assertThat(queryString(connection,
                "SELECT title FROM activities WHERE id = '" + activityId + "'"))
                .isEqualTo("Preserve me");
            assertThat(queryLong(connection,
                "SELECT version FROM activities WHERE id = '" + activityId + "'"))
                .isZero();
            assertThat(queryLong(connection, "SELECT count(*) FROM spring_session")).isZero();
            assertThat(columnExists(connection, "auth_tokens", "invite_code")).isTrue();
        }
    }

    @Test
    void releasedMigrationFixturesRemainByteIdentical() throws Exception {
        List<String> immutableMigrations = List.of(
            "V001__enable_postgis.sql",
            "V002__create_users.sql",
            "V003__create_auth_tokens.sql",
            "V004__create_invite_codes.sql",
            "V005__create_spring_session_tables.sql",
            "V014__auth_token_attempt_count.sql",
            "V016__add_gin_index_custom_fields.sql"
        );
        for (String migration : immutableMigrations) {
            byte[] production = new ClassPathResource("db/migration/" + migration)
                .getInputStream().readAllBytes();
            byte[] fixture = new ClassPathResource("db/onda-upgrade/" + migration)
                .getInputStream().readAllBytes();
            assertThat(fixture).as(migration).isEqualTo(production);
        }
    }

    private Flyway flyway(String location) {
        return Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations(location)
            .cleanDisabled(false)
            .load();
    }

    private void executeSqlResource(String resource) throws Exception {
        String sql = new String(new ClassPathResource(resource).getInputStream().readAllBytes(),
            java.nio.charset.StandardCharsets.UTF_8);
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private boolean tableExists(Connection connection, String table) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT EXISTS (
                SELECT 1 FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = ?)
            """)) {
            statement.setString(1, table);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }

    private boolean columnExists(Connection connection, String table, String column) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT EXISTS (
                SELECT 1 FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND column_name = ?)
            """)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }

    private long queryLong(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private String queryString(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }
}
