package ch.junto.auth;

import ch.junto.auth.entity.AuthToken;
import ch.junto.base.BaseIntegrationTest;
import ch.junto.email.EmailService;
import ch.junto.fixtures.InviteCodeFixture;
import ch.junto.fixtures.UserFixture;
import ch.junto.helpers.MockMvcTestHelper;
import ch.junto.invite.InviteCodeRepository;
import ch.junto.invite.entity.InviteCode;
import ch.junto.user.UserRepository;
import ch.junto.user.entity.User;
import ch.junto.user.enums.UserStatus;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Auth Integration")
class AuthIntegrationTest extends BaseIntegrationTest {

    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private EmailService emailService;
    @Autowired private AuthTokenRepository authTokenRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private InviteCodeRepository inviteCodeRepository;

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

    private MockHttpServletRequestBuilder postJson(String path, Object body, String source) {
        return post(path)
            .with(request -> {
                request.setRemoteAddr(source);
                return request;
            })
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(body));
    }

    private AuthToken grabToken(String email) {
        return authTokenRepository.findFirstByEmailAndUsedAtIsNullOrderByCreatedAtDesc(email)
            .orElseThrow();
    }

    private User seedUser(String email) {
        return userRepository.save(UserFixture.newMember(email));
    }

    private InviteCode invite(String code) {
        InviteCode invite = InviteCodeFixture.valid(
            userRepository.findByEmail("inviter@junto.app").orElseThrow());
        invite.setId(null);
        invite.setCode(code);
        return invite;
    }

    @Nested
    @DisplayName("Signup")
    class Signup {

        @Test
        @DisplayName("should signup new user with invite code")
        void shouldSignupWithInviteCode() throws Exception {
            mockMvc.perform(postJson("/api/auth/signup/request-code",
                    new AuthController.RequestSignupCodeRequest("new@junto.app", INVITE_CODE),
                    "signup-valid"))
                .andExpect(status().isOk());

            verify(emailService).sendAuthCode(eq("new@junto.app"), anyString(), anyString());
            AuthToken token = grabToken("new@junto.app");

            MvcResult result = mockMvc.perform(postJson("/api/auth/verify",
                    new AuthController.VerifyCodeRequest("new@junto.app", token.getCode()),
                    "signup-verify"))
                .andExpect(status().isOk())
                .andReturn();

            User user = userRepository.findByEmail("new@junto.app").orElseThrow();
            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(user.isOnboarded()).isFalse();

            InviteCode used = inviteCodeRepository.findByCode(INVITE_CODE).orElseThrow();
            assertThat(used.isUsed()).isTrue();
            assertThat(used.getUsedBy().getId()).isEqualTo(user.getId());

            mockMvc.perform(get("/api/auth/me").cookie(sessionCookie(result)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@junto.app"))
                .andExpect(jsonPath("$.onboarded").value(false));
        }

        @Test
        @DisplayName("should require invite for explicit signup regardless of account existence")
        void shouldRequireInviteBeforeAnyAccountDependentResponse() throws Exception {
            seedUser("existing-signup@junto.app");

            for (String email : new String[]{"missing@junto.app", "existing-signup@junto.app"}) {
                mockMvc.perform(postJson("/api/auth/signup/request-code",
                        new AuthController.RequestSignupCodeRequest(email, "  "),
                        "missing-" + email))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVITE_REQUIRED"))
                    .andExpect(jsonPath("$.error.message")
                        .value("An invite code is required to create an account."));
                assertThat(authTokenRepository
                    .findFirstByEmailAndUsedAtIsNullOrderByCreatedAtDesc(email)).isEmpty();
            }
            verify(emailService, never()).sendAuthCode(eq("missing@junto.app"), anyString(), anyString());
            verify(emailService, never()).sendAuthCode(eq("existing-signup@junto.app"), anyString(), anyString());
        }

        @Test
        @DisplayName("should return one actionable error for unknown, used, and revoked invites")
        void shouldRejectEveryUnavailableInviteGenerically() throws Exception {
            InviteCode used = invite("JUNTO-USED1");
            used.setUsedAt(OffsetDateTime.now());
            used.setUsedBy(seedUser("invite-used-by@junto.app"));
            inviteCodeRepository.save(used);

            InviteCode revoked = invite("JUNTO-REVOKE");
            revoked.setRevokedAt(OffsetDateTime.now());
            inviteCodeRepository.save(revoked);

            String[] codes = {"DOES-NOT-EXIST", "JUNTO-USED1", "JUNTO-REVOKE"};
            for (int index = 0; index < codes.length; index++) {
                String email = "bad-invite-" + index + "@junto.app";
                mockMvc.perform(postJson("/api/auth/signup/request-code",
                        new AuthController.RequestSignupCodeRequest(email, codes[index]),
                        "bad-invite-" + index))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVITE_INVALID"))
                    .andExpect(jsonPath("$.error.message")
                        .value("This invite is invalid or unavailable. Ask for a new invite."));
                assertThat(authTokenRepository
                    .findFirstByEmailAndUsedAtIsNullOrderByCreatedAtDesc(email)).isEmpty();
            }
        }

        @Test
        @DisplayName("should not let hidden account existence mask invalid signup intent")
        void invalidSignupIsIdenticalForExistingAndUnknownEmail() throws Exception {
            seedUser("known-invalid-invite@junto.app");

            for (String email : new String[]{"unknown-invalid-invite@junto.app", "known-invalid-invite@junto.app"}) {
                mockMvc.perform(postJson("/api/auth/signup/request-code",
                        new AuthController.RequestSignupCodeRequest(email, "NOT-VALID"),
                        "invalid-" + email))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVITE_INVALID"));
                assertThat(authTokenRepository
                    .findFirstByEmailAndUsedAtIsNullOrderByCreatedAtDesc(email)).isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("Login")
    class Login {

        @Test
        @DisplayName("should login existing user with OTP without invite")
        void shouldLoginWithOtp() throws Exception {
            seedUser("existing@junto.app");

            mockMvc.perform(postJson("/api/auth/request-code",
                    new AuthController.RequestCodeRequest("existing@junto.app"), "login-otp"))
                .andExpect(status().isOk());

            AuthToken token = grabToken("existing@junto.app");
            MvcResult result = mockMvc.perform(postJson("/api/auth/verify",
                    new AuthController.VerifyCodeRequest("existing@junto.app", token.getCode()),
                    "login-otp-verify"))
                .andExpect(status().isOk())
                .andReturn();

            mockMvc.perform(get("/api/auth/me").cookie(sessionCookie(result)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("existing@junto.app"));
        }

        @Test
        @DisplayName("should preserve generic success for unknown sign-in")
        void shouldNotEnumerateUnknownSignInEmail() throws Exception {
            mockMvc.perform(postJson("/api/auth/request-code",
                    new AuthController.RequestCodeRequest("unknown-signin@junto.app"),
                    "unknown-signin"))
                .andExpect(status().isOk());

            assertThat(authTokenRepository
                .findFirstByEmailAndUsedAtIsNullOrderByCreatedAtDesc("unknown-signin@junto.app"))
                .isEmpty();
            verify(emailService, never()).sendAuthCode(
                eq("unknown-signin@junto.app"), anyString(), anyString());
        }

        @Test
        @DisplayName("should login existing user via magic link")
        void shouldLoginViaMagicLink() throws Exception {
            seedUser("magic@junto.app");

            mockMvc.perform(postJson("/api/auth/request-code",
                    new AuthController.RequestCodeRequest("magic@junto.app"), "magic-request"))
                .andExpect(status().isOk());

            AuthToken token = grabToken("magic@junto.app");
            MvcResult result = mockMvc.perform(postJson("/api/auth/verify-token",
                    new AuthController.VerifyTokenRequest(token.getToken().toString()),
                    "magic-verify"))
                .andExpect(status().isOk())
                .andReturn();

            mockMvc.perform(get("/api/auth/me").cookie(sessionCookie(result)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("magic@junto.app"));
        }

        @Test
        @DisplayName("should return a visible rate-limit error")
        void shouldExposeRequestRateLimit() throws Exception {
            seedUser("rate-visible@junto.app");
            for (int attempt = 0; attempt < 3; attempt++) {
                mockMvc.perform(postJson("/api/auth/request-code",
                        new AuthController.RequestCodeRequest("rate-visible@junto.app"),
                        "rate-visible-source"))
                    .andExpect(status().isOk());
            }

            mockMvc.perform(postJson("/api/auth/request-code",
                    new AuthController.RequestCodeRequest("rate-visible@junto.app"),
                    "rate-visible-source"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.message")
                    .value("Too many authentication attempts. Try again later."));
        }
    }

    @Nested
    @DisplayName("Security")
    class Security {

        @Test
        void shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        }

        @Test
        void shouldChangeSessionIdOnLogin() throws Exception {
            seedUser("fixation@junto.app");
            mockMvc.perform(postJson("/api/auth/request-code",
                new AuthController.RequestCodeRequest("fixation@junto.app"), "fixation-request"));

            MockHttpSession preAuthSession = new MockHttpSession();
            String preAuthId = preAuthSession.getId();
            AuthToken token = grabToken("fixation@junto.app");

            MvcResult result = mockMvc.perform(postJson("/api/auth/verify",
                    new AuthController.VerifyCodeRequest("fixation@junto.app", token.getCode()),
                    "fixation-verify")
                    .session(preAuthSession))
                .andExpect(status().isOk())
                .andReturn();

            assertThat(sessionCookie(result).getValue()).isNotEqualTo(preAuthId);
        }

        @Test
        void shouldRejectCodeReuse() throws Exception {
            seedUser("reuse@junto.app");
            mockMvc.perform(postJson("/api/auth/request-code",
                new AuthController.RequestCodeRequest("reuse@junto.app"), "reuse-request"));
            String code = grabToken("reuse@junto.app").getCode();

            mockMvc.perform(postJson("/api/auth/verify",
                    new AuthController.VerifyCodeRequest("reuse@junto.app", code), "reuse-first"))
                .andExpect(status().isOk());
            mockMvc.perform(postJson("/api/auth/verify",
                    new AuthController.VerifyCodeRequest("reuse@junto.app", code), "reuse-second"))
                .andExpect(status().isBadRequest());
        }

        @Test
        void shouldInvalidatePreviousCodeWhenRequestingNewOne() throws Exception {
            seedUser("supersede@junto.app");
            mockMvc.perform(postJson("/api/auth/request-code",
                new AuthController.RequestCodeRequest("supersede@junto.app"), "supersede-source"));
            String firstCode = grabToken("supersede@junto.app").getCode();

            mockMvc.perform(postJson("/api/auth/request-code",
                new AuthController.RequestCodeRequest("supersede@junto.app"), "supersede-source"));
            mockMvc.perform(postJson("/api/auth/verify",
                    new AuthController.VerifyCodeRequest("supersede@junto.app", firstCode),
                    "supersede-verify"))
                .andExpect(status().isBadRequest());
        }
    }

    private Cookie sessionCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("SESSION");
        assertThat(cookie).as("JDBC session cookie").isNotNull();
        return cookie;
    }
}
