package ch.junto.auth;

import ch.junto.base.TestContainerConfig;
import ch.junto.fixtures.InviteCodeFixture;
import ch.junto.fixtures.UserFixture;
import ch.junto.helpers.MockMvcTestHelper;
import ch.junto.invite.InviteCodeRepository;
import ch.junto.invite.entity.InviteCode;
import ch.junto.user.UserRepository;
import ch.junto.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthEmailFailureIntegrationTest extends TestContainerConfig {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthTokenRepository authTokenRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private InviteCodeRepository inviteCodeRepository;
    @MockitoBean private JavaMailSender mailSender;

    @Test
    void smtpFailureReturnsActionableErrorAndRollsBackUnsentToken() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "smtp-failure-" + suffix + "@junto.app";
        String inviteCode = "JUNTO-" + suffix.toUpperCase().substring(0, 6);

        User inviter = userRepository.save(UserFixture.newAdmin("smtp-inviter-" + suffix + "@junto.app"));
        InviteCode invite = InviteCodeFixture.valid(inviter);
        invite.setId(null);
        invite.setCode(inviteCode);
        inviteCodeRepository.save(invite);

        doThrow(new MailSendException("smtp unavailable"))
            .when(mailSender).send(any(SimpleMailMessage.class));

        AuthController.RequestSignupCodeRequest body =
            new AuthController.RequestSignupCodeRequest(email, inviteCode);
        mockMvc.perform(post("/api/auth/signup/request-code")
                .with(request -> {
                    request.setRemoteAddr("smtp-failure-" + suffix);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(MockMvcTestHelper.json(objectMapper, body)))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error.code").value("EMAIL_DELIVERY_FAILED"))
            .andExpect(jsonPath("$.error.message")
                .value("We couldn't send your code. Please try again."));

        assertThat(authTokenRepository
            .findFirstByEmailAndUsedAtIsNullOrderByCreatedAtDesc(email)).isEmpty();
        assertThat(inviteCodeRepository.findByCode(inviteCode).orElseThrow().isUsed()).isFalse();
    }
}
