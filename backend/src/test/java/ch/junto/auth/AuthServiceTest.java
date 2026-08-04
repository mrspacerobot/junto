package ch.junto.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.junto.auth.entity.AuthToken;
import ch.junto.core.exception.ApiException;
import ch.junto.email.EmailService;
import ch.junto.fixtures.AuthTokenFixture;
import ch.junto.fixtures.UserFixture;
import ch.junto.invite.InviteService;
import ch.junto.user.UserService;
import ch.junto.user.entity.User;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock AuthTokenRepository authTokenRepository;
  @Mock UserService userService;
  @Mock InviteService inviteService;
  @Mock EmailService emailService;
  @Mock JdbcTemplate jdbcTemplate;
  @InjectMocks AuthService authService;
  @Captor ArgumentCaptor<AuthToken> tokenCaptor;

  @Test
  void unknownEmailWithoutValidInviteReturnsGenericSuccessWithoutIssuingToken() {
    when(userService.findByEmail("new@junto.app")).thenReturn(Optional.empty());

    authService.requestCode("new@junto.app", null);

    verify(authTokenRepository, never()).saveAndFlush(any());
    verify(emailService, never()).sendAuthCode(anyString(), anyString(), anyString());
  }

  @Test
  void validInviteIssuesTokenForNormalizedEmail() {
    when(userService.findByEmail("new@junto.app")).thenReturn(Optional.empty());
    when(inviteService.isValidCode("JUNTO-ABC123")).thenReturn(true);
    when(authTokenRepository.saveAndFlush(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    authService.requestCode("  New@Junto.App ", "JUNTO-ABC123");

    verify(authTokenRepository).deleteUnusedByEmail("new@junto.app");
    verify(authTokenRepository).saveAndFlush(tokenCaptor.capture());
    assertThat(tokenCaptor.getValue().getInviteCode()).isEqualTo("JUNTO-ABC123");
    verify(emailService).sendAuthCode(eq("new@junto.app"), anyString(), anyString());
  }

  @Test
  void inactiveAccountUsesSameGenericNoTokenPath() {
    User suspended = UserFixture.suspended();
    when(userService.findByEmail("suspended@junto.app")).thenReturn(Optional.of(suspended));

    authService.requestCode("suspended@junto.app", null);

    verify(authTokenRepository, never()).saveAndFlush(any());
    verify(emailService, never()).sendAuthCode(anyString(), anyString(), anyString());
  }

  @Test
  void validCodeIsConsumedAtomically() {
    User user = UserFixture.userWithEmail("user@junto.app");
    AuthToken token = AuthTokenFixture.valid("user@junto.app");
    when(authTokenRepository.consumeByEmailAndCode(
            eq("user@junto.app"), eq("123456"), any(OffsetDateTime.class), eq(5)))
        .thenReturn(1);
    when(authTokenRepository.findByEmailAndCode("user@junto.app", "123456"))
        .thenReturn(Optional.of(token));
    when(userService.findByEmail("user@junto.app")).thenReturn(Optional.of(user));

    assertThat(authService.verifyCode("user@junto.app", "123456")).isSameAs(user);
  }

  @Test
  void everyWrongCodeIncrementsCurrentTokenIndependentOfGuess() {
    when(authTokenRepository.consumeByEmailAndCode(
            eq("user@junto.app"), eq("000000"), any(OffsetDateTime.class), eq(5)))
        .thenReturn(0);
    when(authTokenRepository.incrementWrongAttempt(
            eq("user@junto.app"), eq("000000"), any(OffsetDateTime.class), eq(5)))
        .thenReturn(1);

    assertThatThrownBy(() -> authService.verifyCode("user@junto.app", "000000"))
        .isInstanceOf(ApiException.class)
        .extracting(exception -> ((ApiException) exception).getStatus())
        .isEqualTo(400);
  }

  @Test
  void exhaustedTokenIsRateLimited() {
    AuthToken token = AuthTokenFixture.valid("user@junto.app");
    token.setAttemptCount(5);
    when(authTokenRepository.consumeByEmailAndCode(
            eq("user@junto.app"), eq("123456"), any(OffsetDateTime.class), eq(5)))
        .thenReturn(0);
    when(authTokenRepository.incrementWrongAttempt(
            eq("user@junto.app"), eq("123456"), any(OffsetDateTime.class), eq(5)))
        .thenReturn(0);
    when(authTokenRepository.findFirstByEmailAndUsedAtIsNullOrderByCreatedAtDesc("user@junto.app"))
        .thenReturn(Optional.of(token));

    assertThatThrownBy(() -> authService.verifyCode("user@junto.app", "123456"))
        .isInstanceOf(ApiException.class)
        .extracting(exception -> ((ApiException) exception).getStatus())
        .isEqualTo(429);
  }

  @Test
  void firstVerificationConsumesInviteInUserCreationTransaction() {
    User user = UserFixture.userWithEmail("new@junto.app");
    AuthToken token = AuthTokenFixture.withInviteCode("new@junto.app", "JUNTO-ABC123");
    when(authTokenRepository.consumeByEmailAndCode(
            eq("new@junto.app"), eq("123456"), any(OffsetDateTime.class), eq(5)))
        .thenReturn(1);
    when(authTokenRepository.findByEmailAndCode("new@junto.app", "123456"))
        .thenReturn(Optional.of(token));
    when(userService.findByEmail("new@junto.app")).thenReturn(Optional.empty());
    when(userService.createFromEmail("new@junto.app")).thenReturn(user);

    assertThat(authService.verifyCode("new@junto.app", "123456")).isSameAs(user);
    verify(inviteService).consumeCode("JUNTO-ABC123", user);
  }

  @Test
  void malformedMagicTokenIsRejectedWithoutRepositoryAccess() {
    assertThatThrownBy(() -> authService.verifyToken("not-a-uuid"))
        .isInstanceOf(ApiException.class);
    verify(authTokenRepository, never()).consumeByToken(any(), any());
  }
}
