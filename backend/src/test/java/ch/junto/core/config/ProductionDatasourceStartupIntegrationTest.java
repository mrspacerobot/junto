package ch.junto.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import ch.junto.JuntoApplication;

@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class ProductionDatasourceStartupIntegrationTest {

    private static final String DATABASE_NAME = "release_profile_test";
    private static final String DATABASE_USERNAME = "release_profile_user";
    private static final String DATABASE_PASSWORD = "release-password-secret-marker";

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        DockerImageName.parse("postgis/postgis:16-3.4-alpine")
            .asCompatibleSubstituteFor("postgres"))
        .withDatabaseName(DATABASE_NAME)
        .withUsername(DATABASE_USERNAME)
        .withPassword(DATABASE_PASSWORD);

    @Test
    void productionProfileStartsAgainstSeparatePostgisConnectionPropertiesWithoutLoggingCredentials(
            CapturedOutput output) throws Exception {
        try (ConfigurableApplicationContext context = application().run(
                "--spring.profiles.active=prod",
                "--DATABASE_HOST=" + postgres.getHost(),
                "--DATABASE_PORT=" + postgres.getMappedPort(5432),
                "--DATABASE_NAME=" + DATABASE_NAME,
                "--DATABASE_USERNAME=" + DATABASE_USERNAME,
                "--DATABASE_PASSWORD=" + DATABASE_PASSWORD,
                "--APP_BASE_URL=https://junto.example",
                "--RESEND_API_KEY=unused-in-startup",
                "--server.port=0",
                "--app.scheduling.enabled=false")) {
            DataSource dataSource = context.getBean(DataSource.class);
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT PostGIS_Version()")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isNotBlank();
                assertThat(connection.getMetaData().getURL())
                    .startsWith("jdbc:postgresql://")
                    .contains("/" + DATABASE_NAME)
                    .doesNotContain(DATABASE_USERNAME, DATABASE_PASSWORD);
                assertThat(connection.getMetaData().getUserName()).isEqualTo(DATABASE_USERNAME);
            }
        }

        assertThat(output).doesNotContain(DATABASE_USERNAME, DATABASE_PASSWORD);
    }

    private static SpringApplicationBuilder application() {
        return new SpringApplicationBuilder(JuntoApplication.class)
            .web(WebApplicationType.SERVLET);
    }
}
