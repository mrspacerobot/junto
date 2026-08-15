package ch.junto.auth;

import ch.junto.auth.entity.AuthToken;
import ch.junto.email.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class AuthCodeServiceTest {

    @Mock AuthTokenRepository authTokenRepository;
    @Mock PostgresAuthTokenIssuanceLock issuanceLock;
    @Mock EmailService emailService;
    @InjectMocks AuthCodeService authCodeService;

    @Test
    void locksAndFlushesReplacementBeforeDelivery() {
        String email = "user@junto.app";
        String inviteCode = "JUNTO-ABC123";
        ArgumentCaptor<AuthToken> tokenCaptor = ArgumentCaptor.forClass(AuthToken.class);

        authCodeService.issue(email, inviteCode);

        InOrder ordered = inOrder(issuanceLock, authTokenRepository, emailService);
        ordered.verify(issuanceLock).acquire(email);
        ordered.verify(authTokenRepository).deleteUnusedByEmail(email);
        ordered.verify(authTokenRepository).saveAndFlush(tokenCaptor.capture());

        AuthToken persistedToken = tokenCaptor.getValue();
        assertThat(persistedToken.getEmail()).isEqualTo(email);
        assertThat(persistedToken.getInviteCode()).isEqualTo(inviteCode);
        ordered.verify(emailService).sendAuthCode(
            email, persistedToken.getCode(), persistedToken.getToken().toString());
        ordered.verifyNoMoreInteractions();
    }
}
