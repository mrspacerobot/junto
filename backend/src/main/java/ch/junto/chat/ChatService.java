package ch.junto.chat;

import ch.junto.activity.ActivityRepository;
import ch.junto.activity.entity.Activity;
import ch.junto.chat.dto.ChatMessageResponse;
import ch.junto.chat.entity.ChatMessage;
import ch.junto.chat.enums.MessageType;
import ch.junto.chat.event.ChatMessageCommittedEvent;
import ch.junto.core.exception.ForbiddenException;
import ch.junto.core.exception.NotFoundException;
import ch.junto.participant.ParticipantRepository;
import ch.junto.participant.enums.ParticipantStatus;
import ch.junto.user.entity.User;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ChatService {

  private static final int MAX_CONTENT_LENGTH = 2000;

  private final ChatMessageRepository chatMessageRepository;
  private final ActivityRepository activityRepository;
  private final ParticipantRepository participantRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final ChatRateLimiter rateLimiter;

  public ChatService(
      ChatMessageRepository chatMessageRepository,
      ActivityRepository activityRepository,
      ParticipantRepository participantRepository,
      ApplicationEventPublisher eventPublisher,
      ChatRateLimiter rateLimiter) {
    this.chatMessageRepository = chatMessageRepository;
    this.activityRepository = activityRepository;
    this.participantRepository = participantRepository;
    this.eventPublisher = eventPublisher;
    this.rateLimiter = rateLimiter;
  }

  public ChatMessageResponse sendMessage(UUID activityId, String content, User user) {
    Activity activity =
        activityRepository
            .findById(activityId)
            .orElseThrow(() -> new NotFoundException("Activity not found"));

    requireApprovedParticipant(activityId, user.getId());

    String trimmed = content.strip();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("Content must not be blank");
    }
    if (trimmed.length() > MAX_CONTENT_LENGTH) {
      throw new IllegalArgumentException(
          "Content must not exceed " + MAX_CONTENT_LENGTH + " characters");
    }

    rateLimiter.check(activityId, user.getId());

    ChatMessage message = new ChatMessage();
    message.setActivity(activity);
    message.setUser(user);
    message.setContent(trimmed);
    message.setType(MessageType.USER);
    message = chatMessageRepository.save(message);

    ChatMessageResponse response = mapToResponse(message);
    eventPublisher.publishEvent(new ChatMessageCommittedEvent(response));
    return response;
  }

  public void sendSystemMessage(UUID activityId, String content) {
    Activity activity =
        activityRepository
            .findById(activityId)
            .orElseThrow(() -> new NotFoundException("Activity not found"));

    ChatMessage message = new ChatMessage();
    message.setActivity(activity);
    message.setUser(null);
    message.setContent(content);
    message.setType(MessageType.SYSTEM);
    message = chatMessageRepository.save(message);

    ChatMessageResponse response = mapToResponse(message);
    eventPublisher.publishEvent(new ChatMessageCommittedEvent(response));
  }

  @Transactional(readOnly = true)
  public List<ChatMessageResponse> getMessages(
      UUID activityId, User user, int limit, UUID beforeId) {
    requireApprovedParticipant(activityId, user.getId());

    int clampedLimit = Math.max(1, Math.min(limit, 100));

    List<ChatMessage> messages;
    if (beforeId != null) {
      ChatMessage cursor =
          chatMessageRepository
              .findById(beforeId)
              .orElseThrow(() -> new NotFoundException("Cursor message not found"));
      if (!cursor.getActivity().getId().equals(activityId)) {
        throw new NotFoundException("Cursor message not found");
      }
      messages =
          chatMessageRepository.findByActivityIdBeforeCursor(
              activityId, cursor.getCreatedAt(), cursor.getId(), clampedLimit);
    } else {
      messages = chatMessageRepository.findLatestByActivityId(activityId, clampedLimit);
    }

    // Results come DESC from DB — reverse to ASC for the client
    Collections.reverse(messages);
    return messages.stream().map(this::mapToResponse).toList();
  }

  private void requireApprovedParticipant(UUID activityId, UUID userId) {
    participantRepository
        .findByActivityIdAndUserId(activityId, userId)
        .filter(p -> p.getStatus() == ParticipantStatus.APPROVED)
        .orElseThrow(() -> new ForbiddenException("Only approved participants can access chat"));
  }

  private ChatMessageResponse mapToResponse(ChatMessage m) {
    return new ChatMessageResponse(
        m.getId(),
        m.getActivity().getId(),
        m.getUser() != null ? m.getUser().getId() : null,
        m.getUser() != null ? m.getUser().getDisplayName() : "System",
        m.getUser() != null ? m.getUser().getAvatarUrl() : null,
        m.getContent(),
        m.getType(),
        m.getCreatedAt());
  }
}
