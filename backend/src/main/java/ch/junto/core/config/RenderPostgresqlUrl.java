package ch.junto.core.config;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Converts the external PostgreSQL URL supplied by Render into JDBC connection
 * details. The input value is deliberately absent from errors and string
 * representations because it contains credentials and may contain secret query
 * parameters.
 */
final class RenderPostgresqlUrl {

    private static final Pattern QUERY_PARAMETER_NAME =
        Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    static final String INVALID_URL_MESSAGE =
        "DATABASE_URL must be a complete postgresql:// URL with credentials, host, port, and database";

    private RenderPostgresqlUrl() {
    }

    static JdbcConnectionDetails parse(String value) {
        if (value == null || value.isBlank()) {
            throw invalidUrl();
        }

        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException exception) {
            throw invalidUrl();
        }

        if (!"postgresql".equalsIgnoreCase(uri.getScheme())
            || uri.isOpaque()
            || uri.getFragment() != null) {
            throw invalidUrl();
        }

        String rawUserInfo = uri.getRawUserInfo();
        int credentialSeparator = rawUserInfo == null ? -1 : rawUserInfo.indexOf(':');
        if (credentialSeparator <= 0 || credentialSeparator == rawUserInfo.length() - 1) {
            throw invalidUrl();
        }

        String username = decodeCredential(rawUserInfo.substring(0, credentialSeparator));
        String password = decodeCredential(rawUserInfo.substring(credentialSeparator + 1));
        if (username.isEmpty() || password.isEmpty()
            || username.indexOf('\0') >= 0 || password.indexOf('\0') >= 0) {
            throw invalidUrl();
        }

        String host = uri.getHost();
        int port = uri.getPort();
        String rawPath = uri.getRawPath();
        if (host == null || host.isBlank()
            || port < 1 || port > 65535
            || rawPath == null || rawPath.length() <= 1
            || rawPath.charAt(0) != '/'
            || rawPath.indexOf('/', 1) >= 0) {
            throw invalidUrl();
        }

        // Decode only to validate UTF-8 and NUL. The raw form must remain in the
        // JDBC URL so reserved characters in valid database names stay escaped.
        String database = decodePercentEncoded(rawPath.substring(1));
        if (database.isEmpty() || database.indexOf('\0') >= 0) {
            throw invalidUrl();
        }

        String jdbcHost = host;
        if (host.indexOf(':') >= 0 && !(host.startsWith("[") && host.endsWith("]"))) {
            jdbcHost = '[' + host + ']';
        }

        String jdbcUrl = new StringBuilder("jdbc:postgresql://")
            .append(jdbcHost)
            .append(':')
            .append(port)
            .append(rawPath)
            .toString();

        return new JdbcConnectionDetails(
            jdbcUrl, username, password, parseQueryParameters(uri.getRawQuery()));
    }

    private static Map<String, String> parseQueryParameters(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return Map.of();
        }

        Map<String, String> parameters = new LinkedHashMap<>();
        for (String pair : rawQuery.split("&", -1)) {
            int separator = pair.indexOf('=');
            if (separator <= 0) {
                throw invalidUrl();
            }

            String name = decodePercentEncoded(pair.substring(0, separator));
            String value = decodePercentEncoded(pair.substring(separator + 1));
            if (!QUERY_PARAMETER_NAME.matcher(name).matches()
                || "user".equalsIgnoreCase(name)
                || "password".equalsIgnoreCase(name)
                || containsUnsafeControl(value)
                || parameters.putIfAbsent(name, value) != null) {
                throw invalidUrl();
            }
        }
        return Collections.unmodifiableMap(parameters);
    }

    private static String decodeCredential(String rawValue) {
        String decoded = decodePercentEncoded(rawValue);
        if (containsUnsafeControl(decoded)) {
            throw invalidUrl();
        }
        return decoded;
    }

    private static String decodePercentEncoded(String rawValue) {
        StringBuilder decoded = new StringBuilder(rawValue.length());
        int index = 0;
        while (index < rawValue.length()) {
            if (rawValue.charAt(index) != '%') {
                decoded.append(rawValue.charAt(index));
                index++;
                continue;
            }

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            while (index < rawValue.length() && rawValue.charAt(index) == '%') {
                if (index + 2 >= rawValue.length()) {
                    throw invalidUrl();
                }
                int high = Character.digit(rawValue.charAt(index + 1), 16);
                int low = Character.digit(rawValue.charAt(index + 2), 16);
                if (high < 0 || low < 0) {
                    throw invalidUrl();
                }
                bytes.write((high << 4) + low);
                index += 3;
            }

            try {
                decoded.append(StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray())));
            } catch (CharacterCodingException exception) {
                throw invalidUrl();
            }
        }
        return decoded.toString();
    }

    private static boolean containsUnsafeControl(String value) {
        return value.indexOf('\0') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }

    private static IllegalArgumentException invalidUrl() {
        return new IllegalArgumentException(INVALID_URL_MESSAGE);
    }

    static final class JdbcConnectionDetails {
        private final String jdbcUrl;
        private final String username;
        private final String password;
        private final Map<String, String> queryParameters;

        private JdbcConnectionDetails(String jdbcUrl, String username, String password,
                                      Map<String, String> queryParameters) {
            this.jdbcUrl = jdbcUrl;
            this.username = username;
            this.password = password;
            this.queryParameters = queryParameters;
        }

        String jdbcUrl() {
            return jdbcUrl;
        }

        String username() {
            return username;
        }

        String password() {
            return password;
        }

        Map<String, String> queryParameters() {
            return queryParameters;
        }

        @Override
        public String toString() {
            return "JdbcConnectionDetails[jdbcUrl=<redacted>, username=<redacted>, "
                + "password=<redacted>, queryParameters=<redacted>]";
        }
    }
}
