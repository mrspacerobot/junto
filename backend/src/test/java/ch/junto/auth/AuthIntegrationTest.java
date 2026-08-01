package ch.junto.auth;

import ch.junto.base.BaseIntegrationTest;
import ch.junto.email.EmailService;
import ch.junto.auth.entity.AuthToken;
import ch.junto.fixtures.InviteCodeFixture;
import ch.junto.fixtures.UserFixture;
import ch.junto.helpers.MockMvcTestHelper;
import ch.junto.invite.entity.InviteCode;
import ch.junto.user.entity.User;
import ch.junto.user.enums.UserStatus;
import ch.junto.user.UserRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import jakarta.servlet.http.Cookie;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Auth Integration")
class AuthIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmailService emailService;

    @Autowired
    private AuthTokenRepository authTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ch.junto.invite.InviteCodeRepository inviteCodeRepository;

    private static final String INVITE_CODE = "JUNTO-TEST1";

    @BeforeEach
    void seedInviteCode() {
        User inviter = userRepository.save(UserFixture.newAdmin("inviter@junto.app"));

        InviteCode code = InviteCodeFixture.valid(inviter);
        code.setId(null);
        code.setCode(INVITE_CODE);
        inviteCodeRepository.save(code);
    }

    private String json(Object dto) {
        return MockMvcTestHelper.json(objectMapper, dto);
    }

    private AuthToken grabToken(String email) {
        return authTokenRepository.findFirstByEmailAndUsedAtIsNullOrderByCreatedAtDesc(email)
            .orElseThrow();
    }

    private User seedUser(String email) {
        return userRepository.save(UserFixture.newMember(email));
    }

    @Nested
    @DisplayName("Signup")
    class Signup {

        @Test
        @DisplayName("should signup new user with invite code")
        void shouldSignupWithInviteCode() throws Exception {
            mockMvc.perform(post("/api/auth/request-code")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new AuthController.RequestCodeRequest("new@junto.app", INVITE_CODE))))
                .andExpect(status().isOk());

            AuthToken token = grabToken("new@junto.app");

            MvcResult result = mockMvc.perform(post("/api/auth/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new AuthController.VerifyCodeRequest("new@junto.app", token.getCode()))))
                .andExpect(status().isOk())
                .andReturn();

            User user = userRepository.findByEmail("new@junto.app").orElseThrow();
            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(user.isOnboarded()).isFalse();

            InviteCode used = inviteCodeRepository.findByCode(INVITE_CODE).orElseThrow();
            assertThat(used.isUsed()).isTrue();
            assertThat(used.getUsedBy().getId()).isEqualTo(user.getId());

            mockMvc.perform(get("/api/auth/me")
                    .cookie(sessionCookie(result)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@junto.app"))
                .andExpect(jsonPath("$.onboarded").value(false));
        }

        @Test
        @DisplayName("should return generic success without issuing a token when invite is missing")
        void shouldNotIssueTokenWithoutInvite() throws Exception {
            mockMvc.perform(post("/api/auth/request-code")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new AuthController.RequestCodeRequest("noinvite@junto.app", null))))
                .andExpect(status().isOk());
            assertThat(authTokenRepository
                .findFirstByEmailAndUsedAtIsNullOrderByCreatedAtDesc("noinvite@junto.app"))
                .isEmpty();
        }

        @Test
        @DisplayName("should return generic success without issuing a token for an invalid invite")
        void shouldNotIssueTokenWithBadInvite() throws Exception {
            mockMvc.perform(post("/api/auth/request-code")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new AuthController.RequestCodeRequest("badinvite@junto.app", "FAKE-CODE"))))
                .andExpect(status().isOk());
            assertThat(authTokenRepository
                .findFirstByEmailAndUsedAtIsNullOrderByCreatedAtDesc("badinvite@junto.app"))
                .isEmpty();
        }
    }

    @Nested
    @DisplayName("Login")
    class Login {

        @Test
        @DisplayName("should login existing user with OTP")
        void shouldLoginWithOtp() throws Exception {
            seedUser("existing@junto.app");

            mockMvc.perform(post("/api/auth/request-code")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new AuthController.RequestCodeRequest("existing@junto.app", null))))
                .andExpect(status().isOk());

            AuthToken token = grabToken("existing@junto.app");

            MvcResult result = mockMvc.perform(post("/api/auth/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new AuthController.VerifyCodeRequest("existing@junto.app", token.getCode()))))
                .andExpect(status().isOk())
                .andReturn();

            mockMvc.perform(get("/api/auth/me")
                    .cookie(sessionCookie(result)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("existing@junto.app"));
        }

        @Test
        @DisplayName("should login existing user via magic link")
        void shouldLoginViaMagicLink() throws Exception {
            seedUser("magic@junto.app");

            mockMvc.perform(post("/api/auth/request-code")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new AuthController.RequestCodeRequest("magic@junto.app", null))))
                .andExpect(status().isOk());

            AuthToken token = grabToken("magic@junto.app");

            MvcResult result = mockMvc.perform(post("/api/auth/verify-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new AuthController.VerifyTokenRequest(token.getToken().toString()))))
                .andExpect(status().isOk())
                .andReturn();

            mockMvc.perform(get("/api/auth/me")
                    .cookie(sessionCookie(result)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("magic@junto.app"));
        }
    }

    @Nested
    @DisplayName("Security")
    class Security {

        @Test
        @DisplayName("should return 401 for unauthenticated request")
        void shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should change session ID on login")
        void shouldChangeSessionIdOnLogin() throws Exception {
            seedUser("fixation@junto.app");

            mockMvc.perform(post("/api/auth/request-code")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new AuthController.RequestCodeRequest("fixation@junto.app", null))));

            MockHttpSession preAuthSession = new MockHttpSession();
            String preAuthId = preAuthSession.getId();

            AuthToken token = grabToken("fixation@junto.app");

            MvcResult result = mockMvc.perform(post("/api/auth/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .session(preAuthSession)
                    .content(json(new AuthController.VerifyCodeRequest("fixation@junto.app", token.getCode()))))
                .andExpect(status().isOk())
                .andReturn();

            assertThat(sessionCookie(result).getValue()).isNotEqualTo(preAuthId);
        }

        @Test
        @DisplayName("should reject code reuse")
        void shouldRejectCodeReuse() throws Exception {
            seedUser("reuse@junto.app");

            mockMvc.perform(post("/api/auth/request-code")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new AuthController.RequestCodeRequest("reuse@junto.app", null))));

            String code = grabToken("reuse@junto.app").getCode();

            mockMvc.perform(post("/api/auth/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new AuthController.VerifyCodeRequest("reuse@junto.app", code))))
                .andExpect(status().isOk());

            mockMvc.perform(post("/api/auth/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new AuthController.VerifyCodeRequest("reuse@junto.app", code))))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should invalidate previous code when requesting new one")
        void shouldInvalidatePreviousCode() throws Exception {
            seedUser("supersede@junto.app");

            mockMvc.perform(post("/api/auth/request-code")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new AuthController.RequestCodeRequest("supersede@junto.app", null))));

            String firstCode = grabToken("supersede@junto.app").getCode();

            mockMvc.perform(post("/api/auth/request-code")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new AuthController.RequestCodeRequest("supersede@junto.app", null))));

            mockMvc.perform(post("/api/auth/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new AuthController.VerifyCodeRequest("supersede@junto.app", firstCode))))
                .andExpect(status().isBadRequest());
        }
    }

    private Cookie sessionCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("SESSION");
        assertThat(cookie).as("JDBC session cookie").isNotNull();
        return cookie;
    }
}
