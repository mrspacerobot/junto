package ch.junto.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The magic-link URL is the boundary that decides whether the one-time secret is loggable. A
 * browser puts the path and query on the request line and can repeat both in a {@code Referer}
 * header; it never does either with the fragment. These assertions are written against the parsed
 * URL rather than the string so a reordering or re-encoding that reintroduced the leak could not
 * pass.
 */
@DisplayName("Magic link URL")
class MagicLinkUrlTest {

  private static final String BASE_URL = "https://junto.example";
  private static final String TOKEN = UUID.randomUUID().toString();

  @Test
  @DisplayName("should carry the token only in the fragment")
  void shouldCarryTokenOnlyInFragment() {
    URI url = URI.create(EmailService.magicLinkUrl(BASE_URL, TOKEN));

    assertThat(url.getFragment()).isEqualTo("token=" + TOKEN);
    assertThat(url.getPath()).isEqualTo("/auth/verify");
    assertThat(url.getQuery()).isNull();
  }

  @Test
  @DisplayName("should keep the token out of everything a server can log")
  void shouldKeepTokenOutOfServerVisibleRequestLine() {
    URI url = URI.create(EmailService.magicLinkUrl(BASE_URL, TOKEN));

    // What a browser sends for GET https://junto.example/auth/verify#token=...
    String requestTarget =
        url.getRawPath() + (url.getRawQuery() == null ? "" : "?" + url.getRawQuery());

    assertThat(requestTarget).doesNotContain(TOKEN);
    assertThat(url.getHost()).doesNotContain(TOKEN);
    assertThat(url.getUserInfo()).isNull();
  }
}
