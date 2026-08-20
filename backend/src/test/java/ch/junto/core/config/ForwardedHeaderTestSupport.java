package ch.junto.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.junto.auth.AuthTokenRepository;
import ch.junto.base.TestContainerConfig;
import ch.junto.email.EmailService;
import ch.junto.fixtures.UserFixture;
import ch.junto.user.UserRepository;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Shared plumbing for the tests that drive a real servlet container over loopback while pretending
 * to be a provider TLS proxy. Forwarded-header handling is a Tomcat engine valve, so it is only
 * exercised by a real container and never by {@code MockMvc}.
 */
abstract class ForwardedHeaderTestSupport extends TestContainerConfig {

  static final String SESSION_COOKIE = "SESSION";
  static final String LOOPBACK_ADDRESS = "127.0.0.1";

  @LocalServerPort int port;
  @Autowired UserRepository userRepository;
  @Autowired AuthTokenRepository authTokenRepository;
  @Autowired JdbcTemplate jdbcTemplate;
  @MockitoBean EmailService emailService;

  @BeforeEach
  void clearRateLimitWindows() {
    jdbcTemplate.update("DELETE FROM auth_rate_limits");
  }

  static HttpClient newClient() {
    return HttpClient.newBuilder()
        .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
        .build();
  }

  static String uniqueEmail(String prefix) {
    return prefix + "-" + UUID.randomUUID() + "@junto.app";
  }

  static List<String> setCookies(HttpResponse<?> response, String name) {
    return response.headers().allValues("set-cookie").stream()
        .filter(value -> value.startsWith(name + "="))
        .toList();
  }

  static Optional<String> setCookie(HttpResponse<?> response, String name) {
    return setCookies(response, name).stream().findFirst();
  }

  HttpResponse<String> post(HttpClient client, String path, String body, String... headers)
      throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://" + LOOPBACK_ADDRESS + ":" + port + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
    for (int i = 0; i < headers.length; i += 2) {
      request.header(headers[i], headers[i + 1]);
    }
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  HttpResponse<String> requestCode(HttpClient client, String email, String... headers)
      throws Exception {
    return post(client, "/api/auth/request-code", "{\"email\":\"" + email + "\"}", headers);
  }

  /** Runs a full sign-in and returns the {@code Set-Cookie} header of the Spring Session cookie. */
  String signInAndCaptureSessionCookie(HttpClient client, String... headers) throws Exception {
    String email = uniqueEmail("proxy");
    userRepository.save(UserFixture.newMember(email));

    assertThat(requestCode(client, email, headers).statusCode()).isEqualTo(200);
    String code =
        authTokenRepository
            .findFirstByEmailAndUsedAtIsNullOrderByCreatedAtDesc(email)
            .orElseThrow()
            .getCode();

    HttpResponse<String> verify =
        post(
            client,
            "/api/auth/verify",
            "{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}",
            headers);
    assertThat(verify.statusCode()).isEqualTo(200);
    return setCookie(verify, SESSION_COOKIE).orElseThrow();
  }
}
