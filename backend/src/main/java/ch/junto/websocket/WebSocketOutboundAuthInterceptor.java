package ch.junto.websocket;

import ch.junto.participant.ParticipantRepository;
import ch.junto.participant.enums.ParticipantStatus;
import ch.junto.user.enums.UserStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Final delivery-time authorization closes leave, suspension, and logout races. */
@Component
public class WebSocketOutboundAuthInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketOutboundAuthInterceptor.class);
    public static final String ACTIVITY_ID_HEADER = "juntoActivityId";

    private final WebSocketSessionRegistry sessionRegistry;
    private final ParticipantRepository participantRepository;

    public WebSocketOutboundAuthInterceptor(
        WebSocketSessionRegistry sessionRegistry,
        ParticipantRepository participantRepository
    ) {
        this.sessionRegistry = sessionRegistry;
        this.participantRepository = participantRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        Object rawActivityId = message.getHeaders().get(ACTIVITY_ID_HEADER);
        if (!(rawActivityId instanceof String activityIdValue)) {
            return message;
        }

        String webSocketSessionId = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
        log.debug("Authorizing activity delivery activity={} websocketSession={}",
            activityIdValue, webSocketSessionId);
        if (webSocketSessionId == null || !sessionRegistry.hasLiveHttpSession(webSocketSessionId)) {
            return null;
        }

        UUID activityId = UUID.fromString(activityIdValue);
        UUID userId = sessionRegistry.userId(webSocketSessionId).orElse(null);
        if (userId == null) {
            return null;
        }

        boolean authorized = participantRepository
            .existsByActivityIdAndUserIdAndStatusAndUserStatus(
                activityId, userId, ParticipantStatus.APPROVED, UserStatus.ACTIVE);
        log.debug("Activity delivery authorization user={} activity={} authorized={}",
            userId, activityId, authorized);
        return authorized ? message : null;
    }
}
