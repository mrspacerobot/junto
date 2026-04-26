package ch.junto.websocket;

import ch.junto.participant.ParticipantRepository;
import ch.junto.participant.enums.ParticipantStatus;
import ch.junto.user.entity.User;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Pattern ACTIVITY_TOPIC = Pattern.compile("^/topic/activity/([0-9a-f\\-]+)$");

    private final ParticipantRepository participantRepository;

    public WebSocketAuthInterceptor(ParticipantRepository participantRepository) {
        this.participantRepository = participantRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }

        String destination = accessor.getDestination();
        if (destination == null) {
            return message;
        }

        Matcher matcher = ACTIVITY_TOPIC.matcher(destination);
        if (!matcher.matches()) {
            return message;
        }

        UUID activityId = UUID.fromString(matcher.group(1));
        Object principal = accessor.getSessionAttributes().get("AUTHENTICATED_USER");
        if (!(principal instanceof User user)) {
            throw new IllegalStateException("Not authenticated");
        }

        boolean isApproved = participantRepository.findByActivityIdAndUserId(activityId, user.getId())
            .filter(p -> p.getStatus() == ParticipantStatus.APPROVED)
            .isPresent();

        if (!isApproved) {
            throw new IllegalStateException("Not an approved participant of this activity");
        }

        return message;
    }
}
