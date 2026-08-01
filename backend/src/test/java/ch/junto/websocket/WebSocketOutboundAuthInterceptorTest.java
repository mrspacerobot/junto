package ch.junto.websocket;

import ch.junto.participant.ParticipantRepository;
import ch.junto.participant.enums.ParticipantStatus;
import ch.junto.user.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketOutboundAuthInterceptorTest {

    @Mock WebSocketSessionRegistry sessionRegistry;
    @Mock ParticipantRepository participantRepository;
    private WebSocketOutboundAuthInterceptor interceptor;
    private UUID activityId;
    private UUID userId;
    private Message<byte[]> message;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketOutboundAuthInterceptor(sessionRegistry, participantRepository);
        activityId = UUID.randomUUID();
        userId = UUID.randomUUID();
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId("ws-1");
        headers.setHeader(WebSocketOutboundAuthInterceptor.ACTIVITY_ID_HEADER, activityId.toString());
        message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    @Test
    void logoutDropsDeliveryWhenBackingHttpSessionIsGone() {
        when(sessionRegistry.hasLiveHttpSession("ws-1")).thenReturn(false);
        assertThat(interceptor.preSend(message, null)).isNull();
    }

    @Test
    void suspensionDropsDeliveryEvenForPreviouslyApprovedSubscription() {
        when(sessionRegistry.hasLiveHttpSession("ws-1")).thenReturn(true);
        when(sessionRegistry.userId("ws-1")).thenReturn(Optional.of(userId));
        when(participantRepository.existsByActivityIdAndUserIdAndStatusAndUserStatus(
            activityId, userId, ParticipantStatus.APPROVED, UserStatus.ACTIVE))
            .thenReturn(false);

        assertThat(interceptor.preSend(message, null)).isNull();
    }
}
