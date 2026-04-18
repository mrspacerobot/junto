package ch.junto.auth;

import ch.junto.email.EmailService;
import ch.junto.fixtures.AuthTokenFixture;
import ch.junto.fixtures.UserFixture;
import ch.junto.invite.InviteService;
import ch.junto.shared.entity.AuthToken;
import ch.junto.shared.entity.User;
import ch.junto.shared.exception.ApiException;
import ch.junto.user.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock
    private AuthTokenRepository authTokenRepository;

    @Mock
    private UserService userService;

    @Mock
    private InviteService inviteService;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuthService authService;

    @Captor
    private ArgumentCaptor<AuthToken> tokenCaptor;

    @Nested
    @DisplayName("requestCode")
    class RequestCode {

        @Test
        @DisplayName("should require invite code for new email")
        void shouldRequireInviteCodeForNewEmail() {
            when(userService.findByEmail("new@junto.app")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.requestCode("new@junto.app", null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo("INVITE_CODE_REQUIRED");
                });
        }

        @Test
        @DisplayName("should require invite code for new email when blank")
        void shouldRequireInviteCodeForNewEmailWhenBlank() {
            when(userService.findByEmail("new@junto.app")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.requestCode("new@junto.app", "   "))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo("INVITE_CODE_REQUIRED");
                });
        }

        @Test
        @DisplayName("should validate invite code for new email")
        void shouldValidateInviteCodeForNewEmail() {
            when(userService.findByEmail("new@junto.app")).thenReturn(Optional.empty());
            doThrow(new ApiException("INVITE_CODE_INVALID", "Invalid invite code", 400))
                .when(inviteService).validateCode("BAD-CODE");

            assertThatThrownBy(() -> authService.requestCode("new@junto.app", "BAD-CODE"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo("INVITE_CODE_INVALID");
                });
        }

        @Test
        @DisplayName("should send code for new email with valid invite")
        void shouldSendCodeForNewEmailWithValidInvite() {
            when(userService.findByEmail("new@junto.app")).thenReturn(Optional.empty());
            when(authTokenRepository.save(any(AuthToken.class))).thenAnswer(inv -> inv.getArgument(0));

            authService.requestCode("new@junto.app", "JUNTO-ABC123");

            verify(inviteService).validateCode("JUNTO-ABC123");
            verify(authTokenRepository).deleteUnusedByEmail("new@junto.app");
            verify(authTokenRepository).save(any(AuthToken.class));
            verify(emailService).sendAuthCode(eq("new@junto.app"), anyString(), anyString());
        }

        @Test
        @DisplayName("should store invite code on token for new user")
        void shouldStoreInviteCodeOnTokenForNewUser() {
            when(userService.findByEmail("new@junto.app")).thenReturn(Optional.empty());
            when(authTokenRepository.save(any(AuthToken.class))).thenAnswer(inv -> inv.getArgument(0));

            authService.requestCode("new@junto.app", "JUNTO-XYZ789");

            verify(authTokenRepository).save(tokenCaptor.capture());
            assertThat(tokenCaptor.getValue().getInviteCode()).isEqualTo("JUNTO-XYZ789");
        }

        @Test
        @DisplayName("should ignore invite code for existing email")
        void shouldIgnoreInviteCodeForExistingEmail() {
            User existingUser = UserFixture.defaultUser();
            when(userService.findByEmail("user@junto.app")).thenReturn(Optional.of(existingUser));

            authService.requestCode("user@junto.app", "JUNTO-IGNORE");

            verify(inviteService, never()).validateCode(anyString());
            verify(authTokenRepository).save(any(AuthToken.class));
            verify(emailService).sendAuthCode(eq("user@junto.app"), anyString(), anyString());
        }

        @Test
        @DisplayName("should reject suspended user")
        void shouldRejectSuspendedUser() {
            User suspendedUser = UserFixture.suspended();
            when(userService.findByEmail("suspended@junto.app")).thenReturn(Optional.of(suspendedUser));

            assertThatThrownBy(() -> authService.requestCode("suspended@junto.app", null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo("ACCOUNT_SUSPENDED");
                });

            verify(authTokenRepository, never()).save(any());
            verify(emailService, never()).sendAuthCode(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("should invalidate previous unused tokens before issuing new one")
        void shouldInvalidatePreviousUnusedTokens() {
            User existingUser = UserFixture.defaultUser();
            when(userService.findByEmail("user@junto.app")).thenReturn(Optional.of(existingUser));

            authService.requestCode("user@junto.app", null);

            verify(authTokenRepository).deleteUnusedByEmail("user@junto.app");
        }

        @Test
        @DisplayName("should not set invite code on token for existing user")
        void shouldNotSetInviteCodeForExistingUser() {
            User existingUser = UserFixture.defaultUser();
            when(userService.findByEmail("user@junto.app")).thenReturn(Optional.of(existingUser));
            when(authTokenRepository.save(any(AuthToken.class))).thenAnswer(inv -> inv.getArgument(0));

            authService.requestCode("user@junto.app", null);

            verify(authTokenRepository).save(tokenCaptor.capture());
            assertThat(tokenCaptor.getValue().getInviteCode()).isNull();
        }

        @Test
        @DisplayName("should normalize email to lowercase and trim")
        void shouldNormalizeEmail() {
            User existingUser = UserFixture.withEmail("user@junto.app");
            when(userService.findByEmail("user@junto.app")).thenReturn(Optional.of(existingUser));

            authService.requestCode("  User@Junto.APP  ", null);

            verify(userService).findByEmail("user@junto.app");
            verify(authTokenRepository).deleteUnusedByEmail("user@junto.app");
            verify(emailService).sendAuthCode(eq("user@junto.app"), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("verifyCode")
    class VerifyCode {

        @Test
        @DisplayName("should verify valid OTP with atomic consume")
        void shouldVerifyValidOtp() {
            User existingUser = UserFixture.withEmail("user@junto.app");
            AuthToken authToken = AuthTokenFixture.valid("user@junto.app");

            when(authTokenRepository.findByEmailAndCodeAndUsedAtIsNull("user@junto.app", "123456"))
                .thenReturn(Optional.of(authToken));
            when(authTokenRepository.consumeByEmailAndCode(eq("user@junto.app"), eq("123456"), any()))
                .thenReturn(1);
            when(authTokenRepository.findByEmailAndCode("user@junto.app", "123456"))
                .thenReturn(Optional.of(authToken));
            when(userService.findByEmail("user@junto.app")).thenReturn(Optional.of(existingUser));

            User result = authService.verifyCode("user@junto.app", "123456");

            assertThat(result).isEqualTo(existingUser);
        }

        @Test
        @DisplayName("should reject invalid code")
        void shouldRejectInvalidCode() {
            when(authTokenRepository.findByEmailAndCodeAndUsedAtIsNull("user@junto.app", "000000"))
                .thenReturn(Optional.empty());
            when(authTokenRepository.consumeByEmailAndCode(eq("user@junto.app"), eq("000000"), any()))
                .thenReturn(0);
            when(authTokenRepository.incrementAttemptCount("user@junto.app", "000000"))
                .thenReturn(0);

            assertThatThrownBy(() -> authService.verifyCode("user@junto.app", "000000"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo("AUTH_CODE_INVALID");
                });
        }

        @Test
        @DisplayName("should reject expired code")
        void shouldRejectExpiredCode() {
            AuthToken authToken = AuthTokenFixture.expired("user@junto.app");

            when(authTokenRepository.findByEmailAndCodeAndUsedAtIsNull("user@junto.app", "654321"))
                .thenReturn(Optional.of(authToken));
            when(authTokenRepository.consumeByEmailAndCode(eq("user@junto.app"), eq("654321"), any()))
                .thenReturn(1);
            when(authTokenRepository.findByEmailAndCode("user@junto.app", "654321"))
                .thenReturn(Optional.of(authToken));

            assertThatThrownBy(() -> authService.verifyCode("user@junto.app", "654321"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo("AUTH_CODE_EXPIRED");
                });
        }

        @Test
        @DisplayName("should create user on first verification")
        void shouldCreateUserOnFirstVerification() {
            User newUser = UserFixture.withEmail("new@junto.app");
            AuthToken authToken = AuthTokenFixture.valid("new@junto.app");

            when(authTokenRepository.findByEmailAndCodeAndUsedAtIsNull("new@junto.app", "123456"))
                .thenReturn(Optional.of(authToken));
            when(authTokenRepository.consumeByEmailAndCode(eq("new@junto.app"), eq("123456"), any()))
                .thenReturn(1);
            when(authTokenRepository.findByEmailAndCode("new@junto.app", "123456"))
                .thenReturn(Optional.of(authToken));
            when(userService.findByEmail("new@junto.app")).thenReturn(Optional.empty());
            when(userService.createFromEmail("new@junto.app")).thenReturn(newUser);

            User result = authService.verifyCode("new@junto.app", "123456");

            assertThat(result).isEqualTo(newUser);
            verify(userService).createFromEmail("new@junto.app");
        }

        @Test
        @DisplayName("should pass exact invite code to consumeCode")
        void shouldPassExactInviteCodeToConsumeCode() {
            User newUser = UserFixture.withEmail("new@junto.app");
            AuthToken authToken = AuthTokenFixture.withInviteCode("new@junto.app", "JUNTO-ABC123");

            when(authTokenRepository.findByEmailAndCodeAndUsedAtIsNull("new@junto.app", "123456"))
                .thenReturn(Optional.of(authToken));
            when(authTokenRepository.consumeByEmailAndCode(eq("new@junto.app"), eq("123456"), any()))
                .thenReturn(1);
            when(authTokenRepository.findByEmailAndCode("new@junto.app", "123456"))
                .thenReturn(Optional.of(authToken));
            when(userService.findByEmail("new@junto.app")).thenReturn(Optional.empty());
            when(userService.createFromEmail("new@junto.app")).thenReturn(newUser);

            authService.verifyCode("new@junto.app", "123456");

            verify(inviteService).consumeCode("JUNTO-ABC123", newUser);
        }

        @Test
        @DisplayName("should not consume invite code for existing user")
        void shouldNotConsumeInviteCodeForExistingUser() {
            User existingUser = UserFixture.withEmail("user@junto.app");
            AuthToken authToken = AuthTokenFixture.valid("user@junto.app");

            when(authTokenRepository.findByEmailAndCodeAndUsedAtIsNull("user@junto.app", "123456"))
                .thenReturn(Optional.of(authToken));
            when(authTokenRepository.consumeByEmailAndCode(eq("user@junto.app"), eq("123456"), any()))
                .thenReturn(1);
            when(authTokenRepository.findByEmailAndCode("user@junto.app", "123456"))
                .thenReturn(Optional.of(authToken));
            when(userService.findByEmail("user@junto.app")).thenReturn(Optional.of(existingUser));

            authService.verifyCode("user@junto.app", "123456");

            verify(inviteService, never()).consumeCode(anyString(), any());
        }

        @Test
        @DisplayName("should reject suspended user at verification time")
        void shouldRejectSuspendedUserAtVerification() {
            User suspendedUser = UserFixture.suspended();
            AuthToken authToken = AuthTokenFixture.valid("suspended@junto.app");

            when(authTokenRepository.findByEmailAndCodeAndUsedAtIsNull("suspended@junto.app", "123456"))
                .thenReturn(Optional.of(authToken));
            when(authTokenRepository.consumeByEmailAndCode(eq("suspended@junto.app"), eq("123456"), any()))
                .thenReturn(1);
            when(authTokenRepository.findByEmailAndCode("suspended@junto.app", "123456"))
                .thenReturn(Optional.of(authToken));
            when(userService.findByEmail("suspended@junto.app")).thenReturn(Optional.of(suspendedUser));

            assertThatThrownBy(() -> authService.verifyCode("suspended@junto.app", "123456"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo("ACCOUNT_SUSPENDED");
                });
        }

        @Test
        @DisplayName("should reject after max attempts exceeded")
        void shouldRejectAfterMaxAttemptsExceeded() {
            AuthToken authToken = AuthTokenFixture.valid("user@junto.app");
            authToken.setAttemptCount(5);

            when(authTokenRepository.findByEmailAndCodeAndUsedAtIsNull("user@junto.app", "123456"))
                .thenReturn(Optional.of(authToken));

            assertThatThrownBy(() -> authService.verifyCode("user@junto.app", "123456"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo("AUTH_CODE_INVALID");
                    assertThat(api.getStatus()).isEqualTo(429);
                });

            verify(authTokenRepository, never()).consumeByEmailAndCode(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("should normalize email for verification")
        void shouldNormalizeEmailForVerification() {
            User existingUser = UserFixture.withEmail("user@junto.app");
            AuthToken authToken = AuthTokenFixture.valid("user@junto.app");

            when(authTokenRepository.findByEmailAndCodeAndUsedAtIsNull("user@junto.app", "123456"))
                .thenReturn(Optional.of(authToken));
            when(authTokenRepository.consumeByEmailAndCode(eq("user@junto.app"), eq("123456"), any()))
                .thenReturn(1);
            when(authTokenRepository.findByEmailAndCode("user@junto.app", "123456"))
                .thenReturn(Optional.of(authToken));
            when(userService.findByEmail("user@junto.app")).thenReturn(Optional.of(existingUser));

            User result = authService.verifyCode("  User@Junto.APP  ", "123456");

            assertThat(result).isEqualTo(existingUser);
        }
    }

    @Nested
    @DisplayName("verifyToken")
    class VerifyToken {

        @Test
        @DisplayName("should verify valid magic link token")
        void shouldVerifyValidToken() {
            User existingUser = UserFixture.withEmail("user@junto.app");
            AuthToken authToken = AuthTokenFixture.valid("user@junto.app");

            when(authTokenRepository.consumeByToken(eq(authToken.getToken()), any()))
                .thenReturn(1);
            when(authTokenRepository.findByToken(authToken.getToken()))
                .thenReturn(Optional.of(authToken));
            when(userService.findByEmail("user@junto.app")).thenReturn(Optional.of(existingUser));

            User result = authService.verifyToken(authToken.getToken().toString());

            assertThat(result).isEqualTo(existingUser);
        }

        @Test
        @DisplayName("should reject invalid token format")
        void shouldRejectInvalidTokenFormat() {
            assertThatThrownBy(() -> authService.verifyToken("not-a-uuid"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo("AUTH_CODE_INVALID");
                });
        }

        @Test
        @DisplayName("should reject already used token")
        void shouldRejectAlreadyUsedToken() {
            UUID token = UUID.randomUUID();
            when(authTokenRepository.consumeByToken(eq(token), any())).thenReturn(0);

            assertThatThrownBy(() -> authService.verifyToken(token.toString()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo("AUTH_CODE_INVALID");
                });
        }

        @Test
        @DisplayName("should reject expired token")
        void shouldRejectExpiredToken() {
            AuthToken authToken = AuthTokenFixture.expired("user@junto.app");
            UUID token = authToken.getToken();

            when(authTokenRepository.consumeByToken(eq(token), any())).thenReturn(1);
            when(authTokenRepository.findByToken(token)).thenReturn(Optional.of(authToken));

            assertThatThrownBy(() -> authService.verifyToken(token.toString()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo("AUTH_CODE_EXPIRED");
                });
        }

        @Test
        @DisplayName("should reject suspended user at token verification")
        void shouldRejectSuspendedUserAtTokenVerification() {
            User suspendedUser = UserFixture.suspended();
            AuthToken authToken = AuthTokenFixture.valid("suspended@junto.app");
            UUID token = authToken.getToken();

            when(authTokenRepository.consumeByToken(eq(token), any())).thenReturn(1);
            when(authTokenRepository.findByToken(token)).thenReturn(Optional.of(authToken));
            when(userService.findByEmail("suspended@junto.app")).thenReturn(Optional.of(suspendedUser));

            assertThatThrownBy(() -> authService.verifyToken(token.toString()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo("ACCOUNT_SUSPENDED");
                });
        }
    }
}
