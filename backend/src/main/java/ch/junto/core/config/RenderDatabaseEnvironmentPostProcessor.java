package ch.junto.core.config;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Supplies Spring's datasource properties from Render's DATABASE_URL before the
 * production application context is created.
 */
public final class RenderDatabaseEnvironmentPostProcessor
        implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE_NAME = "renderPostgresqlDatabaseUrl";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                       SpringApplication application) {
        if (Arrays.stream(environment.getActiveProfiles()).noneMatch("prod"::equals)) {
            return;
        }

        RenderPostgresqlUrl.JdbcConnectionDetails details =
            RenderPostgresqlUrl.parse(environment.getProperty("DATABASE_URL"));

        Map<String, Object> datasourceProperties = new LinkedHashMap<>();
        datasourceProperties.put("spring.datasource.url", details.jdbcUrl());
        datasourceProperties.put("spring.datasource.username", details.username());
        datasourceProperties.put("spring.datasource.password", details.password());
        details.queryParameters().forEach((name, value) -> datasourceProperties.put(
            "spring.datasource.hikari.data-source-properties." + name, value));
        environment.getPropertySources().addFirst(
            new MapPropertySource(PROPERTY_SOURCE_NAME, datasourceProperties));
    }

    /**
     * Config data must load first so spring.profiles.active from normal Boot
     * configuration sources is available to the adapter.
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
