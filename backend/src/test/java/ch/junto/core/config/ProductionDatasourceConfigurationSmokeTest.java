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

import ch.junto.JuntoApplication;
import ch.junto.base.TestContainerConfig;

@ExtendWith(OutputCaptureExtension.class)
class ProductionDatasourceConfigurationSmokeTest extends TestContainerConfig {

    @Test
    void productionProfileUsesSeparateDatasourcePropertiesWithoutLoggingCredentials(
            CapturedOutput output) throws Exception {
        String expectedJdbcUrl = "jdbc:postgresql://" + postgres.getHost() + ':'
            + postgres.getMappedPort(5432) + '/' + postgres.getDatabaseName();
        String credentialBearingAuthority = postgres.getUsername() + ':'
            + postgres.getPassword() + '@';

        // Launch directly instead of through Spring's TestContext so the
        // fixture's test-profile @DynamicPropertySource does not override the
        // production DATABASE_* contract under test.
        try (ConfigurableApplicationContext context = application().run(
                "--spring.profiles.active=prod",
                "--DATABASE_HOST=" + postgres.getHost(),
                "--DATABASE_PORT=" + postgres.getMappedPort(5432),
                "--DATABASE_NAME=" + postgres.getDatabaseName(),
                "--DATABASE_USERNAME=" + postgres.getUsername(),
                "--DATABASE_PASSWORD=" + postgres.getPassword(),
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
                    .isEqualTo(expectedJdbcUrl)
                    .doesNotContain(credentialBearingAuthority);
                assertThat(connection.getMetaData().getUserName())
                    .isEqualTo(postgres.getUsername());
            }
        }

        assertThat(output).doesNotContain(credentialBearingAuthority);
    }

    private static SpringApplicationBuilder application() {
        return new SpringApplicationBuilder(JuntoApplication.class)
            .web(WebApplicationType.SERVLET);
    }
}
