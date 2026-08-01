package ch.junto.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

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
class RenderDatabaseProductionStartupIntegrationTest {

    private static final String DATABASE_USERNAME = "release_profile_user";
    private static final String DATABASE_PASSWORD = "release-password-secret-marker";
    private static final String QUERY_VALUE = "query-secret-marker";

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        DockerImageName.parse("postgis/postgis:16-3.4-alpine")
            .asCompatibleSubstituteFor("postgres"))
        .withDatabaseName("release_profile_test")
        .withUsername(DATABASE_USERNAME)
        .withPassword(DATABASE_PASSWORD);

    @Test
    void productionProfileStartsAgainstProviderStylePostgisUrlWithoutLoggingSecrets(
            CapturedOutput output) throws Exception {
        String providerUrl = "postgresql://" + DATABASE_USERNAME + ':' + DATABASE_PASSWORD
            + '@' + postgres.getHost() + ':' + postgres.getMappedPort(5432)
            + "/release_profile_test?sslmode=disable&ApplicationName=" + QUERY_VALUE;

        try (ConfigurableApplicationContext context = application().run(
                "--spring.profiles.active=prod",
                "--DATABASE_URL=" + providerUrl,
                "--APP_BASE_URL=https://junto.example",
                "--RESEND_API_KEY=unused-in-startup",
                "--server.port=0",
                "--app.scheduling.enabled=false")) {
            DataSource dataSource = context.getBean(DataSource.class);
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(
                     "SELECT PostGIS_Version(), current_setting('application_name')")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isNotBlank();
                assertThat(result.getString(2)).isEqualTo(QUERY_VALUE);
                assertThat(connection.getMetaData().getURL())
                    .startsWith("jdbc:postgresql://")
                    .contains("/release_profile_test")
                    .doesNotContain("?", QUERY_VALUE);
                assertThat(connection.getMetaData().getUserName()).isEqualTo(DATABASE_USERNAME);
            }
        }

        assertThat(output).doesNotContain(DATABASE_USERNAME, DATABASE_PASSWORD, QUERY_VALUE);
    }

    @Test
    void malformedProductionUrlStopsStartupWithOnlySafeDiagnostics(CapturedOutput output) {
        String credentialSecret = "invalid-credential-secret-marker";
        String querySecret = "invalid-query-secret-marker";
        String invalidUrl = "postgresql://user:" + credentialSecret
            + "@localhost:5432?token=" + querySecret;

        Throwable failure = catchThrowable(() -> application().run(
            "--spring.profiles.active=prod",
            "--DATABASE_URL=" + invalidUrl,
            "--APP_BASE_URL=https://junto.example",
            "--RESEND_API_KEY=unused-in-startup",
            "--server.port=0"));

        assertThat(failure).isNotNull();
        assertThat(rootCause(failure))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(RenderPostgresqlUrl.INVALID_URL_MESSAGE);
        assertThat(output).doesNotContain(credentialSecret, querySecret, invalidUrl);
    }

    private static SpringApplicationBuilder application() {
        return new SpringApplicationBuilder(JuntoApplication.class)
            .web(WebApplicationType.SERVLET);
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root;
    }
}
