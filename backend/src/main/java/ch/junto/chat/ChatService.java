package ch.junto.chat;

import ch.junto.activity.ActivityRepository;
import ch.junto.chat.dto.ChatMessageResponse;
import ch.junto.chat.entity.ChatMessage;
import ch.junto.chat.enums.MessageType;
import ch.junto.core.exception.ForbiddenException;
import ch.junto.core.exception.NotFoundException;
import ch.junto.core.exception.RateLimitException;
import ch.junto.participant.ParticipantRepository;
import ch.junto.participant.enums.ParticipantStatus;
import ch.junto.activity.entity.Activity;
import ch.junto.user.entity.User;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Transactional
public class ChatService {

    private static final int MAX_CONTENT_LENGTH = 2000;
    private static final int RATE_LIMIT_MAX = 10;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofSeconds(10);

    private final ChatMessageRepository chatMessageRepository;
    private final ActivityRepository activityRepository;
    private final ParticipantRepository participantRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private final Cache<String, CopyOnWriteArrayList<Long>> rateLimitCache = Caffeine.newBuilder()
        .expireAfterWrite(RATE_LIMIT_WINDOW)
        .build();

    public ChatService(
        ChatMessageRepository chatMessageRepository,
        ActivityRepository activityRepository,
        ParticipantRepository participantRepository,
        SimpMessagingTemplate messagingTemplate
    ) {
        this.chatMessageRepository = chatMessageRepository;
        this.activityRepository = activityRepository;
        this.participantRepository = participantRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public ChatMessageResponse sendMessage(UUID activityId, String content, User user) {
        Activity activity = activityRepository.findById(activityId)
            .orElseThrow(() -> new NotFoundException("Activity not found"));

        requireApprovedParticipant(activityId, user.getId());

        String trimmed = content.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Content must not be blank");
        }
        if (trimmed.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("Content must not exceed " + MAX_CONTENT_LENGTH + " characters");
        }

        checkRateLimit(activityId, user.getId());

        ChatMessage message = new ChatMessage();
        message.setActivity(activity);
        message.setUser(user);
        message.setContent(trimmed);
        message.setType(MessageType.USER);
        message = chatMessageRepository.save(message);

        ChatMessageResponse response = mapToResponse(message);
        messagingTemplate.convertAndSend("/topic/activity/" + activityId, response);
        return response;
    }

    public void sendSystemMessage(UUID activityId, String content) {
        Activity activity = activityRepository.findById(activityId)
            .orElseThrow(() -> new NotFoundException("Activity not found"));

        ChatMessage message = new ChatMessage();
        message.setActivity(activity);
        message.setUser(null);
        message.setContent(content);
        message.setType(MessageType.SYSTEM);
        message = chatMessageRepository.save(message);

        ChatMessageResponse response = mapToResponse(message);
        messagingTemplate.convertAndSend("/topic/activity/" + activityId, response);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(UUID activityId, User user, int limit, UUID beforeId) {
        requireApprovedParticipant(activityId, user.getId());

        int clampedLimit = Math.max(1, Math.min(limit, 100));

        List<ChatMessage> messages;
        if (beforeId != null) {
            ChatMessage cursor = chatMessageRepository.findById(beforeId)
                .orElseThrow(() -> new NotFoundException("Cursor message not found"));
            messages = chatMessageRepository.findByActivityIdBeforeCursor(
                activityId, cursor.getCreatedAt(), cursor.getId(), clampedLimit);
        } else {
            messages = chatMessageRepository.findLatestByActivityId(activityId, clampedLimit);
        }

        // Results come DESC from DB — reverse to ASC for the client
        Collections.reverse(messages);
        return messages.stream().map(this::mapToResponse).toList();
    }

    private void requireApprovedParticipant(UUID activityId, UUID userId) {
        participantRepository.findByActivityIdAndUserId(activityId, userId)
            .filter(p -> p.getStatus() == ParticipantStatus.APPROVED)
            .orElseThrow(() -> new ForbiddenException("Only approved participants can access chat"));
    }

    private void checkRateLimit(UUID activityId, UUID userId) {
        String key = userId + ":" + activityId;
        long now = System.currentTimeMillis();
        long windowStart = now - RATE_LIMIT_WINDOW.toMillis();

        CopyOnWriteArrayList<Long> timestamps = rateLimitCache.get(key, k -> new CopyOnWriteArrayList<>());
        timestamps.removeIf(ts -> ts < windowStart);

        if (timestamps.size() >= RATE_LIMIT_MAX) {
            throw new RateLimitException("Too many messages. Try again shortly.");
        }
        timestamps.add(now);
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
            m.getCreatedAt()
        );
    }
}
