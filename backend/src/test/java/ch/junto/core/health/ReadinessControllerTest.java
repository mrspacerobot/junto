package ch.junto.core.health;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.junto.auth.config.SecurityConfig;
import ch.junto.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = ReadinessController.class,
    properties = "server.servlet.session.cookie.name=SESSION")
@Import(SecurityConfig.class)
class ReadinessControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private ReadinessCheck readinessCheck;
  @MockitoBean private UserService userService;

  @Test
  void publicEndpointReturnsOnlyTheFixedReadyBody() throws Exception {
    when(readinessCheck.isReady()).thenReturn(true);

    mockMvc
        .perform(get("/healthz"))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(content().json("{\"status\":\"ok\"}", JsonCompareMode.STRICT));
  }

  @Test
  void publicEndpointReturnsOnlyTheFixedUnavailableBody() throws Exception {
    when(readinessCheck.isReady()).thenReturn(false);

    mockMvc
        .perform(get("/healthz"))
        .andDo(print())
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(content().json("{\"status\":\"unavailable\"}", JsonCompareMode.STRICT));
  }
}
