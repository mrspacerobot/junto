package ch.junto.websocket;

import ch.junto.auth.SessionPrincipal;
import ch.junto.participant.ParticipantRepository;
import ch.junto.participant.enums.ParticipantStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Pattern ACTIVITY_TOPIC =
        Pattern.compile("^/topic/activity/([0-9a-f\\-]+)$");

    private final ParticipantRepository participantRepository;
    private final WebSocketSessionRegistry sessionRegistry;

    public WebSocketAuthInterceptor(
        ParticipantRepository participantRepository,
        WebSocketSessionRegistry sessionRegistry
    ) {
        this.participantRepository = participantRepository;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (accessor.getCommand() == StompCommand.DISCONNECT) {
            sessionRegistry.removeWebSocketSession(accessor.getSessionId());
            return message;
        }

        if (!(accessor.getUser() instanceof org.springframework.security.core.Authentication authentication)
            || !(authentication.getPrincipal() instanceof SessionPrincipal principal)) {
            throw new IllegalStateException("Not authenticated");
        }

        if (accessor.getCommand() == StompCommand.CONNECT) {
            Map<String, Object> attributes = accessor.getSessionAttributes();
            String httpSessionId = attributes == null
                ? null : (String) attributes.get("HTTP_SESSION_ID");
            sessionRegistry.register(accessor.getSessionId(), httpSessionId, principal.userId());
            return message;
        }

        if (!sessionRegistry.hasLiveHttpSession(accessor.getSessionId())) {
            throw new IllegalStateException("Authentication session has expired");
        }

        if (accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }

        String destination = accessor.getDestination();
        Matcher matcher = destination == null ? null : ACTIVITY_TOPIC.matcher(destination);
        if (matcher == null || !matcher.matches()) {
            throw new IllegalStateException("Subscription destination is not allowed");
        }

        UUID activityId = UUID.fromString(matcher.group(1));
        boolean isApproved = participantRepository
            .existsByActivityIdAndUserIdAndStatusAndUserStatus(
                activityId,
                principal.userId(),
                ParticipantStatus.APPROVED,
                ch.junto.user.enums.UserStatus.ACTIVE);

        if (!isApproved) {
            throw new IllegalStateException("Not an approved participant of this activity");
        }

        return message;
    }
}
