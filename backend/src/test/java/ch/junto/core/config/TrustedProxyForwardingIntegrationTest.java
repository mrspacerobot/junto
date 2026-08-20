package ch.junto.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import ch.junto.auth.AuthRateLimiter;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Mirrors the production forwarding configuration: the loopback peer stands in for Render's TLS
 * proxy, which is inside Tomcat's default internal-proxy ranges.
 *
 * <p>{@link ProductionRuntimeConfigurationTest} asserts that {@code application-prod.yml} really
 * declares the strategy exercised here.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "server.forward-headers-strategy=native")
@ActiveProfiles("test")
@DisplayName("Forwarded headers at the provider boundary")
class TrustedProxyForwardingIntegrationTest extends ForwardedHeaderTestSupport {

  private static final String PROXY_CLIENT_ADDRESS = "198.51.100.9";

  @MockitoSpyBean AuthRateLimiter authRateLimiter;

  @Test
  @DisplayName("the address the proxy reports becomes the source used for rate limiting")
  void forwardedForFromTheProxyBecomesTheRateLimitSource() throws Exception {
    String email = uniqueEmail("trusted-source");

    assertThat(
            requestCode(newClient(), email, "X-Forwarded-For", PROXY_CLIENT_ADDRESS).statusCode())
        .isEqualTo(200);

    assertThat(capturedSource(email)).isEqualTo(PROXY_CLIENT_ADDRESS);
  }

  @Test
  @DisplayName("a client-supplied X-Forwarded-For prefix cannot spoof the source")
  void clientSuppliedForwardedForPrefixCannotSpoofTheSource() throws Exception {
    String email = uniqueEmail("prefix-spoof");

    // The provider appends the real peer, so the attacker's value is only ever a left-hand prefix.
    assertThat(
            requestCode(newClient(), email, "X-Forwarded-For", "1.2.3.4, " + PROXY_CLIENT_ADDRESS)
                .statusCode())
        .isEqualTo(200);

    assertThat(capturedSource(email)).isEqualTo(PROXY_CLIENT_ADDRESS).isNotEqualTo("1.2.3.4");
  }

  @Test
  @DisplayName("an HTTPS request through the proxy yields a Secure, HttpOnly, SameSite=Lax cookie")
  void httpsProxyProducesSecureSessionCookie() throws Exception {
    String sessionCookie =
        signInAndCaptureSessionCookie(
            newClient(), "X-Forwarded-Proto", "https", "X-Forwarded-For", PROXY_CLIENT_ADDRESS);

    assertThat(sessionCookie).contains("Secure", "HttpOnly", "SameSite=Lax");
  }

  @Test
  @DisplayName("logout deletes the Spring Session cookie and no other session cookie")
  void logoutDeletesTheSpringSessionCookie() throws Exception {
    HttpClient client = newClient();
    signInAndCaptureSessionCookie(client, "X-Forwarded-For", PROXY_CLIENT_ADDRESS);

    HttpResponse<String> logout = post(client, "/api/auth/logout", "{}");

    assertThat(logout.statusCode()).isEqualTo(200);
    assertThat(setCookies(logout, SESSION_COOKIE))
        .isNotEmpty()
        .allSatisfy(cookie -> assertThat(cookie).startsWith(SESSION_COOKIE + "=;"))
        .anySatisfy(
            cookie -> assertThat(cookie).containsAnyOf("Max-Age=0", "Expires=Thu, 01 Jan 1970"));
    assertThat(setCookies(logout, "JSESSIONID")).isEmpty();
  }

  private String capturedSource(String email) {
    ArgumentCaptor<String> source = ArgumentCaptor.forClass(String.class);
    verify(authRateLimiter).checkCodeRequest(eq(email), source.capture());
    return source.getValue();
  }
}
