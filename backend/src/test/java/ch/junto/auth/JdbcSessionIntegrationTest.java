package ch.junto.auth;

import static org.assertj.core.api.Assertions.assertThat;

import ch.junto.base.TestContainerConfig;
import ch.junto.email.EmailService;
import ch.junto.fixtures.UserFixture;
import ch.junto.user.UserRepository;
import ch.junto.user.entity.User;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class JdbcSessionIntegrationTest extends TestContainerConfig {

  @LocalServerPort int port;
  @Autowired UserRepository userRepository;
  @Autowired AuthTokenRepository authTokenRepository;
  @Autowired JdbcTemplate jdbcTemplate;
  @MockitoBean EmailService emailService;

  @Test
  void loginReloadAndLogoutRoundTripThroughJdbcSession() throws Exception {
    String email = "jdbc-session-" + UUID.randomUUID() + "@junto.app";
    User user = UserFixture.newMember(email);
    userRepository.save(user);

    CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();

    assertThat(post(client, "/api/auth/request-code", "{\"email\":\"" + email + "\"}").statusCode())
        .isEqualTo(200);
    String code =
        authTokenRepository
            .findFirstByEmailAndUsedAtIsNullOrderByCreatedAtDesc(email)
            .orElseThrow()
            .getCode();

    assertThat(
            post(
                    client,
                    "/api/auth/verify",
                    "{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}")
                .statusCode())
        .isEqualTo(200);

    HttpResponse<String> me =
        client.send(
            HttpRequest.newBuilder(uri("/api/auth/me")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(me.statusCode()).isEqualTo(200);
    assertThat(me.body()).contains(email);

    List<byte[]> attributes =
        jdbcTemplate.query(
            """
            SELECT a.attribute_bytes
            FROM spring_session_attributes a
            JOIN spring_session s ON s.primary_id = a.session_primary_id
            WHERE s.principal_name = ?
            """,
            (resultSet, row) -> resultSet.getBytes(1),
            user.getId().toString());
    assertThat(attributes).isNotEmpty();
    String serialized = new String(attributes.get(0), StandardCharsets.ISO_8859_1);
    assertThat(serialized).contains("ch.junto.auth.SessionPrincipal");
    assertThat(serialized).doesNotContain("ch.junto.user.entity.User");

    assertThat(post(client, "/api/auth/logout", "{}").statusCode()).isEqualTo(200);
    HttpResponse<String> afterLogout =
        client.send(
            HttpRequest.newBuilder(uri("/api/auth/me")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(afterLogout.statusCode()).isEqualTo(401);
  }

  private HttpResponse<String> post(HttpClient client, String path, String body) throws Exception {
    return client.send(
        HttpRequest.newBuilder(uri(path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + port + path);
  }
}
