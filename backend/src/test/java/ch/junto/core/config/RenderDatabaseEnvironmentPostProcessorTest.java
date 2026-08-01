package ch.junto.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

class RenderDatabaseEnvironmentPostProcessorTest {

    private final RenderDatabaseEnvironmentPostProcessor processor =
        new RenderDatabaseEnvironmentPostProcessor();

    @Test
    void productionProfileReceivesConvertedDatasourcePropertiesAtHighestPrecedence() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.getPropertySources().addFirst(new MapPropertySource("provider", Map.of(
            "DATABASE_URL",
            "postgresql://encoded%40user:p%3Ass@192.0.2.10:5432/app%2Fblue?sslmode=require",
            "spring.datasource.url", "unsafe-provider-value")));

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.datasource.url"))
            .isEqualTo("jdbc:postgresql://192.0.2.10:5432/app%2Fblue");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("encoded@user");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("p:ss");
        assertThat(environment.getProperty(
            "spring.datasource.hikari.data-source-properties.sslmode")).isEqualTo("require");
        assertThat(environment.getPropertySources().iterator().next().getName())
            .isEqualTo(RenderDatabaseEnvironmentPostProcessor.PROPERTY_SOURCE_NAME);
    }

    @Test
    void nonProductionProfilesAreNotAffectedByProviderEnvironment() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("DATABASE_URL", "not-a-provider-url")
            .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/test");
        environment.setActiveProfiles("test");

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.datasource.url"))
            .isEqualTo("jdbc:postgresql://localhost:5432/test");
        assertThat(environment.getPropertySources()
            .contains(RenderDatabaseEnvironmentPostProcessor.PROPERTY_SOURCE_NAME)).isFalse();
    }

    @Test
    void productionProfileRejectsMissingOrInvalidProviderUrl() {
        MockEnvironment missing = new MockEnvironment();
        missing.setActiveProfiles("prod");
        assertThatThrownBy(() -> processor.postProcessEnvironment(missing, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(RenderPostgresqlUrl.INVALID_URL_MESSAGE);

        MockEnvironment invalid = new MockEnvironment()
            .withProperty("DATABASE_URL",
                "postgresql://user:credential-secret@host:5432?token=query-secret");
        invalid.setActiveProfiles("prod");
        assertThatThrownBy(() -> processor.postProcessEnvironment(invalid, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(RenderPostgresqlUrl.INVALID_URL_MESSAGE)
            .hasMessageNotContaining("credential-secret")
            .hasMessageNotContaining("query-secret");
    }
}
