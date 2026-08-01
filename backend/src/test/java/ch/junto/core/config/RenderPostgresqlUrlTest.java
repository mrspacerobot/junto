package ch.junto.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class RenderPostgresqlUrlTest {

    @ParameterizedTest
    @MethodSource("validUrls")
    void convertsRenderUrlsToJdbcConnectionDetails(String input, String jdbcUrl,
                                                     String username, String password,
                                                     Map<String, String> queryParameters) {
        RenderPostgresqlUrl.JdbcConnectionDetails details = RenderPostgresqlUrl.parse(input);

        assertThat(details.jdbcUrl()).isEqualTo(jdbcUrl);
        assertThat(details.username()).isEqualTo(username);
        assertThat(details.password()).isEqualTo(password);
        assertThat(details.queryParameters()).isEqualTo(queryParameters);
    }

    static Stream<Arguments> validUrls() {
        return Stream.of(
            Arguments.of(
                "postgresql://junto:password@db.example.com:5432/junto",
                "jdbc:postgresql://db.example.com:5432/junto",
                "junto",
                "password",
                Map.of()),
            Arguments.of(
                "postgresql://user%40tenant:p%3Ass%2Fword%2B%C3%A9@127.0.0.1:6432/app_db",
                "jdbc:postgresql://127.0.0.1:6432/app_db",
                "user@tenant",
                "p:ss/word+é",
                Map.of()),
            Arguments.of(
                "postgresql://plus+user:plus+password@[2001:db8::7]:5432/tenant%2Fblue"
                    + "?sslmode=require&options=-c%20statement_timeout%3D5000",
                "jdbc:postgresql://[2001:db8::7]:5432/tenant%2Fblue",
                "plus+user",
                "plus+password",
                Map.of(
                    "sslmode", "require",
                    "options", "-c statement_timeout=5000"))
        );
    }

    @Test
    void rejectsMissingUrlWithoutEchoingIt() {
        assertThatThrownBy(() -> RenderPostgresqlUrl.parse(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(RenderPostgresqlUrl.INVALID_URL_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "   ",
        "jdbc:postgresql://user:password@host:5432/database",
        "postgres://user:password@host:5432/database",
        "postgresql://host:5432/database",
        "postgresql://user@host:5432/database",
        "postgresql://:password@host:5432/database",
        "postgresql://user:@host:5432/database",
        "postgresql://user:password@:5432/database",
        "postgresql://user:password@host/database",
        "postgresql://user:password@host:0/database",
        "postgresql://user:password@host:65536/database",
        "postgresql://user:password@host:5432",
        "postgresql://user:password@host:5432/",
        "postgresql://user:password@host:5432/database/extra",
        "postgresql://user:password@host:5432/database#fragment",
        "postgresql://user:password@host:5432/database?missing-value",
        "postgresql://user:password@host:5432/database?user=other",
        "postgresql://user:password@host:5432/database?password=other",
        "postgresql://user:password@host:5432/database?sslmode=a&sslmode=b",
        "postgresql://user:password@host:5432/database?bad-name=value",
        "postgresql://user:password%ZZ@host:5432/database",
        "postgresql://user:%C3%28@host:5432/database",
        "postgresql://user:password@host:5432/data%00base",
        "postgresql://user:line%0Abreak@host:5432/database"
    })
    void rejectsMalformedOrUnsupportedUrlsWithOneSafeDiagnostic(String input) {
        assertThatThrownBy(() -> RenderPostgresqlUrl.parse(input))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(RenderPostgresqlUrl.INVALID_URL_MESSAGE);
    }

    @Test
    void stringRepresentationNeverContainsCredentialsOrQueryValues() {
        RenderPostgresqlUrl.JdbcConnectionDetails details = RenderPostgresqlUrl.parse(
            "postgresql://private-user:private-password@host:5432/database?token=query-secret");

        assertThat(details.toString())
            .doesNotContain("private-user", "private-password", "query-secret", "host", "database")
            .isEqualTo("JdbcConnectionDetails[jdbcUrl=<redacted>, username=<redacted>, "
                + "password=<redacted>, queryParameters=<redacted>]");
    }
}
