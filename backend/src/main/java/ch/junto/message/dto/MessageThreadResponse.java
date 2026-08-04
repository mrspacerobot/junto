package ch.junto.message.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record MessageThreadResponse(
    UUID activityId,
    String activityTitle,
    String activityTypeSlug,
    String activityTypeName,
    String imageUrl,
    int participantCount,
    List<String> participantAvatars,
    String lastMessageContent,
    String lastMessageSender,
    OffsetDateTime lastMessageAt) {}
