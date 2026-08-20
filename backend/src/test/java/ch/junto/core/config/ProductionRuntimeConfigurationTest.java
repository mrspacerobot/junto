package ch.junto.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * The container runtime contract is declarative, so it is asserted against the shipped property
 * files rather than against a running context that a test could configure into passing.
 */
@DisplayName("Production runtime configuration")
class ProductionRuntimeConfigurationTest {

  private static final PropertySource<?> DEFAULTS = load("application.yml");
  private static final PropertySource<?> PRODUCTION = load("application-prod.yml");

  @Test
  @DisplayName("the listener uses the injected port on every interface with a local default")
  void bindsToTheInjectedPortOnAllInterfaces() {
    assertThat(DEFAULTS.getProperty("server.port")).isEqualTo("${PORT:8080}");
    assertThat(DEFAULTS.getProperty("server.address")).isEqualTo("0.0.0.0");
  }

  @Test
  @DisplayName("shutdown is graceful and finishes inside the provider's SIGTERM window")
  void shutsDownGracefullyWithinTheProviderWindow() {
    assertThat(PRODUCTION.getProperty("server.shutdown")).isEqualTo("graceful");
    assertThat(PRODUCTION.getProperty("spring.lifecycle.timeout-per-shutdown-phase"))
        .isEqualTo("20s");
  }

  @Test
  @DisplayName("forwarding headers are trusted only in production, using the native valve")
  void trustsForwardingHeadersOnlyInProduction() {
    assertThat(PRODUCTION.getProperty("server.forward-headers-strategy")).isEqualTo("native");
    assertThat(DEFAULTS.getProperty("server.forward-headers-strategy")).isNull();
  }

  @Test
  @DisplayName("session cookie flags are explicit, and Secure is unconditional in production")
  void declaresExplicitSessionCookieFlags() {
    assertThat(DEFAULTS.getProperty("server.servlet.session.cookie.name")).isEqualTo("SESSION");
    assertThat(DEFAULTS.getProperty("server.servlet.session.cookie.http-only")).isEqualTo(true);
    assertThat(DEFAULTS.getProperty("server.servlet.session.cookie.same-site")).isEqualTo("lax");
    assertThat(PRODUCTION.getProperty("server.servlet.session.cookie.secure")).isEqualTo(true);
  }

  private static PropertySource<?> load(String resource) {
    try {
      List<PropertySource<?>> sources =
          new YamlPropertySourceLoader().load(resource, new ClassPathResource(resource));
      assertThat(sources).hasSize(1);
      return sources.get(0);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read " + resource, e);
    }
  }
}
