package ch.junto.fixtures;

import ch.junto.activity.entity.Activity;
import ch.junto.chat.entity.ChatMessage;
import ch.junto.chat.enums.MessageType;
import ch.junto.user.entity.User;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class ChatMessageFixture {

  private ChatMessageFixture() {}

  public static ChatMessage userMessage(Activity activity, User user, String content) {
    return message(activity, user, MessageType.USER, content, null);
  }

  public static ChatMessage systemMessage(Activity activity, String content) {
    return message(activity, null, MessageType.SYSTEM, content, null);
  }

  public static ChatMessage cursorMessage(Activity activity, OffsetDateTime createdAt) {
    ChatMessage message = message(activity, null, MessageType.USER, "Cursor", createdAt);
    message.setId(UUID.randomUUID());
    return message;
  }

  public static ChatMessage message(
      Activity activity, User user, MessageType type, String content, OffsetDateTime createdAt) {
    ChatMessage message = new ChatMessage();
    message.setActivity(activity);
    message.setUser(user);
    message.setType(type);
    message.setContent(content);
    if (createdAt != null) {
      message.setCreatedAt(createdAt);
      message.setUpdatedAt(createdAt);
    }
    return message;
  }
}
