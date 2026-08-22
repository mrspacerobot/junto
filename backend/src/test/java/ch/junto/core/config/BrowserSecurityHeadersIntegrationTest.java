package ch.junto.core.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.junto.base.BaseIntegrationTest;
import org.junit.jupiter.api.Test;

class BrowserSecurityHeadersIntegrationTest extends BaseIntegrationTest {

  @Test
  void browserSecurityHeadersProtectTheSpaShell() throws Exception {
    mockMvc
        .perform(get("/auth/verify").secure(true))
        .andExpect(status().isOk())
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(header().string("X-Frame-Options", "DENY"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(
            header()
                .string("Content-Security-Policy", BrowserSecurityHeaders.CONTENT_SECURITY_POLICY))
        .andExpect(
            header()
                .string(
                    "Strict-Transport-Security",
                    containsString("max-age=" + BrowserSecurityHeaders.HSTS_MAX_AGE_SECONDS)))
        .andExpect(
            header().string("Strict-Transport-Security", containsString("includeSubDomains")));
  }

  @Test
  void hstsIsNotSentOverPlainHttp() throws Exception {
    mockMvc
        .perform(get("/auth/verify"))
        .andExpect(status().isOk())
        .andExpect(header().doesNotExist("Strict-Transport-Security"));
  }
}
