package ch.junto.chat.dto;

import ch.junto.chat.enums.MessageType;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ChatMessageResponse(
    UUID id,
    UUID activityId,
    UUID userId,
    String displayName,
    String avatarUrl,
    String content,
    MessageType type,
    OffsetDateTime createdAt) {}
