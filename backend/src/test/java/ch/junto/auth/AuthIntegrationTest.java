package ch.junto.auth;

import ch.junto.base.BaseIntegrationTest;
import ch.junto.email.EmailService;
import ch.junto.shared.entity.AuthToken;
import ch.junto.shared.entity.InviteCode;
import ch.junto.shared.entity.User;
import ch.junto.shared.enums.UserRole;
import ch.junto.shared.enums.UserStatus;
import ch.junto.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
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

    @MockBean
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
        User inviter = new User();
        inviter.setEmail("inviter@junto.app");
        inviter.setDisplayName("inviter");
        inviter.setRole(UserRole.ADMIN);
        inviter.setStatus(UserStatus.ACTIVE);
        inviter.setOnboarded(true);
        inviter.setInviteCodesRemaining(99);
        inviter = userRepository.save(inviter);

        InviteCode code = new InviteCode();
        code.setCode(INVITE_CODE);
        code.setCreatedBy(inviter);
        inviteCodeRepository.save(code);
    }

    private String json(Object dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private AuthToken grabToken() {
        return authTokenRepository.findAll().get(0);
    }

    private User seedUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName(email.split("@")[0]);
        user.setRole(UserRole.MEMBER);
        user.setStatus(UserStatus.ACTIVE);
        user.setOnboarded(true);
        user.setInviteCodesRemaining(5);
        return userRepository.save(user);
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

            AuthToken token = grabToken();

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
                    .session((MockHttpSession) result.getRequest().getSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@junto.app"))
                .andExpect(jsonPath("$.onboarded").value(false));
        }

        @Test
        @DisplayName("should reject signup without invite code")
        void shouldRejectSignupWithoutInvite() throws Exception {
            mockMvc.perform(post("/api/auth/request-code")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new AuthController.RequestCodeRequest("noinvite@junto.app", null))))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should reject signup with invalid invite code")
        void shouldRejectSignupWithBadInvite() throws Exception {
            mockMvc.perform(post("/api/auth/request-code")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new AuthController.RequestCodeRequest("badinvite@junto.app", "FAKE-CODE"))))
                .andExpect(status().isBadRequest());
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

            AuthToken token = grabToken();

            MvcResult result = mockMvc.perform(post("/api/auth/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new AuthController.VerifyCodeRequest("existing@junto.app", token.getCode()))))
                .andExpect(status().isOk())
                .andReturn();

            mockMvc.perform(get("/api/auth/me")
                    .session((MockHttpSession) result.getRequest().getSession()))
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

            AuthToken token = grabToken();

            MvcResult result = mockMvc.perform(post("/api/auth/verify-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new AuthController.VerifyTokenRequest(token.getToken().toString()))))
                .andExpect(status().isOk())
                .andReturn();

            mockMvc.perform(get("/api/auth/me")
                    .session((MockHttpSession) result.getRequest().getSession()))
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

            AuthToken token = grabToken();

            MvcResult result = mockMvc.perform(post("/api/auth/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .session(preAuthSession)
                    .content(json(new AuthController.VerifyCodeRequest("fixation@junto.app", token.getCode()))))
                .andExpect(status().isOk())
                .andReturn();

            assertThat(result.getRequest().getSession().getId()).isNotEqualTo(preAuthId);
        }

        @Test
        @DisplayName("should reject code reuse")
        void shouldRejectCodeReuse() throws Exception {
            seedUser("reuse@junto.app");

            mockMvc.perform(post("/api/auth/request-code")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new AuthController.RequestCodeRequest("reuse@junto.app", null))));

            String code = grabToken().getCode();

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

            String firstCode = grabToken().getCode();

            mockMvc.perform(post("/api/auth/request-code")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new AuthController.RequestCodeRequest("supersede@junto.app", null))));

            mockMvc.perform(post("/api/auth/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new AuthController.VerifyCodeRequest("supersede@junto.app", firstCode))))
                .andExpect(status().isBadRequest());
        }
    }
}
