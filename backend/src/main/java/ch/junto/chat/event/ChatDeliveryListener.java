package ch.junto.chat.event;

import ch.junto.websocket.WebSocketOutboundAuthInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Component
public class ChatDeliveryListener {

    private static final Logger log = LoggerFactory.getLogger(ChatDeliveryListener.class);
    private final SimpMessagingTemplate messagingTemplate;

    public ChatDeliveryListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(
        phase = TransactionPhase.AFTER_COMMIT,
        fallbackExecution = true
    )
    public void deliver(ChatMessageCommittedEvent event) {
        UUID activityId = event.message().activityId();
        String destination = "/topic/activity/" + activityId;

        log.debug("Delivering committed chat message activity={} destination={}", activityId, destination);
        SimpMessageHeaderAccessor headers =
            SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setHeader(
            WebSocketOutboundAuthInterceptor.ACTIVITY_ID_HEADER,
            activityId.toString());
        headers.setLeaveMutable(true);
        messagingTemplate.convertAndSend(
            destination, event.message(), headers.getMessageHeaders());
    }
}
