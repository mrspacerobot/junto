package ch.junto.core.config;

/**
 * The browser security headers Junto serves on every response.
 *
 * <p>These are declared here rather than inline in the security filter chain because each allowance
 * exists for a specific resource the application actually loads. Widening one silently is the
 * failure mode worth guarding, so the policy is a single reviewed constant covered by {@code
 * BrowserSecurityHeadersIntegrationTest} and by an end-to-end check that the map and remote images
 * still render.
 */
public final class BrowserSecurityHeaders {

  /** One year, the minimum age browsers require before a host is eligible for HSTS preloading. */
  public static final long HSTS_MAX_AGE_SECONDS = 31_536_000L;

  /**
   * Content Security Policy for the packaged single-page application.
   *
   * <p>Allowances, each tied to a resource the app loads:
   *
   * <ul>
   *   <li>{@code script-src 'self'} - the Vite build emits only external module scripts, so no
   *       inline or {@code eval} allowance is needed.
   *   <li>{@code style-src-attr} keeps {@code 'unsafe-inline'} because Leaflet markers and popups
   *       are built from HTML carrying {@code style} attributes, which CSP treats as inline styles.
   *       Inline style elements remain disallowed. The Google Fonts stylesheet host is allowed for
   *       the two web fonts in {@code index.html}.
   *   <li>{@code img-src} covers CARTO's basemap tile subdomains ({@code
   *       a-d.basemaps.cartocdn.com}) and the Unsplash images used for activity and avatar artwork.
   *       {@code data:} covers inline SVG icon data.
   *   <li>{@code connect-src 'self'} covers the REST API and the same-origin STOMP WebSocket; CSP
   *       Level 3 resolves {@code 'self'} to the page's {@code ws}/{@code wss} origin.
   *   <li>{@code frame-ancestors 'none'} is the modern equivalent of {@code X-Frame-Options: DENY},
   *       which is also sent for older browsers.
   * </ul>
   */
  public static final String CONTENT_SECURITY_POLICY =
      String.join(
          "; ",
          "default-src 'self'",
          "base-uri 'none'",
          "object-src 'none'",
          "frame-ancestors 'none'",
          "form-action 'self'",
          "script-src 'self'",
          "style-src 'self' https://fonts.googleapis.com",
          "style-src-attr 'unsafe-inline'",
          "font-src 'self' https://fonts.gstatic.com",
          "img-src 'self' data: https://images.unsplash.com"
              + " https://a.basemaps.cartocdn.com"
              + " https://b.basemaps.cartocdn.com"
              + " https://c.basemaps.cartocdn.com"
              + " https://d.basemaps.cartocdn.com",
          "connect-src 'self'");

  private BrowserSecurityHeaders() {}
}
