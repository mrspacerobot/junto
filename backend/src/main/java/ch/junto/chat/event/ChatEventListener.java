package ch.junto.chat.event;

import ch.junto.chat.ChatService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ChatEventListener {

  private final ChatService chatService;

  public ChatEventListener(ChatService chatService) {
    this.chatService = chatService;
  }

  @EventListener
  public void onParticipantJoined(ParticipantJoinedEvent event) {
    chatService.sendSystemMessage(event.activityId(), event.displayName() + " joined the activity");
  }

  @EventListener
  public void onParticipantLeft(ParticipantLeftEvent event) {
    chatService.sendSystemMessage(event.activityId(), event.displayName() + " left the activity");
  }
}
