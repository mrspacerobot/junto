package ch.junto.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import ch.junto.auth.AuthRateLimiter;
import java.net.http.HttpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Without an explicit provider boundary the application must ignore forwarding headers entirely,
 * because any caller can set them.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Forwarded headers without proxy trust")
class UntrustedForwardedHeaderIntegrationTest extends ForwardedHeaderTestSupport {

  @MockitoSpyBean AuthRateLimiter authRateLimiter;

  @Test
  @DisplayName("a spoofed X-Forwarded-For cannot replace the source used for rate limiting")
  void spoofedForwardedForDoesNotChangeTheRateLimitSource() throws Exception {
    String email = uniqueEmail("spoofed-source");

    assertThat(requestCode(newClient(), email, "X-Forwarded-For", "203.0.113.9").statusCode())
        .isEqualTo(200);

    ArgumentCaptor<String> source = ArgumentCaptor.forClass(String.class);
    verify(authRateLimiter).checkCodeRequest(eq(email), source.capture());
    assertThat(source.getValue()).isEqualTo(LOOPBACK_ADDRESS);
  }

  @Test
  @DisplayName("a spoofed X-Forwarded-Proto cannot make the session cookie Secure")
  void spoofedForwardedProtoDoesNotMarkTheSessionCookieSecure() throws Exception {
    HttpClient client = newClient();

    String sessionCookie = signInAndCaptureSessionCookie(client, "X-Forwarded-Proto", "https");

    assertThat(sessionCookie).doesNotContain("Secure").contains("HttpOnly", "SameSite=Lax");
  }
}
