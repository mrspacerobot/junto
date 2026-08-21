package ch.junto.bootstrap;

import static ch.junto.bootstrap.BootstrapAdminCommand.ACCESS_CODE_VARIABLE;
import static ch.junto.bootstrap.BootstrapAdminCommand.EMAIL_VARIABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.junto.auth.AuthTokenRepository;
import ch.junto.auth.entity.AuthToken;
import ch.junto.email.EmailService;
import ch.junto.helpers.MockMvcTestHelper;
import ch.junto.invite.InviteCodeRepository;
import ch.junto.user.UserRepository;
import ch.junto.user.entity.User;
import ch.junto.user.enums.UserRole;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Bootstrapped administrator journey")
class BootstrapAdminJourneyIntegrationTest {

  private static final String ADMIN_EMAIL = "Captain@Junto.Example";
  private static final String NORMALIZED_ADMIN_EMAIL = "captain@junto.example";
  private static final String ACCESS_CODE = "junto-pilot-2026";

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
              DockerImageName.parse("postgis/postgis:16-3.4-alpine")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("bootstrap_journey_test")
          .withUsername("test")
          .withPassword("test");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private AuthTokenRepository authTokenRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private InviteCodeRepository inviteCodeRepository;
  @MockitoBean private EmailService emailService;

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Test
  @DisplayName("signs in normally and issues a personal invite")
  void signsInAndIssuesAnInvite() throws Exception {
    Map<String, String> environment =
        Map.of(EMAIL_VARIABLE, ADMIN_EMAIL, ACCESS_CODE_VARIABLE, ACCESS_CODE);
    assertThat(
            BootstrapAdminCommand.run(
                new String[] {BootstrapAdminCommand.COMMAND},
                environment::get,
                commandDatasourceProperties()))
        .isEqualTo(BootstrapAdminCommand.SUCCESS);

    mockMvc
        .perform(postJson("/api/auth/request-code", new SignInBody(ADMIN_EMAIL)))
        .andExpect(status().isOk());
    verify(emailService).sendAuthCode(eq(NORMALIZED_ADMIN_EMAIL), anyString(), anyString());

    AuthToken token =
        authTokenRepository
            .findFirstByEmailAndUsedAtIsNullOrderByCreatedAtDesc(NORMALIZED_ADMIN_EMAIL)
            .orElseThrow();
    MvcResult login =
        mockMvc
            .perform(postJson("/api/auth/verify", new VerifyBody(ADMIN_EMAIL, token.getCode())))
            .andExpect(status().isOk())
            .andReturn();
    Cookie sessionCookie = login.getResponse().getCookie("SESSION");
    assertThat(sessionCookie).isNotNull();

    mockMvc.perform(post("/api/invites").cookie(sessionCookie)).andExpect(status().isOk());

    User administrator = userRepository.findByEmail(NORMALIZED_ADMIN_EMAIL).orElseThrow();
    assertThat(administrator.getRole()).isEqualTo(UserRole.ADMIN);
    assertThat(inviteCodeRepository.findAllByCreatedById(administrator.getId())).hasSize(1);
    assertThat(administrator.getInviteCodesRemaining()).isEqualTo(4);
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postJson(
      String path, Object body) {
    return post(path)
        .with(
            request -> {
              request.setRemoteAddr(UUID.randomUUID().toString());
              return request;
            })
        .contentType(MediaType.APPLICATION_JSON)
        .content(MockMvcTestHelper.json(objectMapper, body));
  }

  private static String[] commandDatasourceProperties() {
    return new String[] {
      "--spring.datasource.url=" + postgres.getJdbcUrl(),
      "--spring.datasource.username=" + postgres.getUsername(),
      "--spring.datasource.password=" + postgres.getPassword()
    };
  }

  private record SignInBody(String email) {}

  private record VerifyBody(String email, String code) {}
}
