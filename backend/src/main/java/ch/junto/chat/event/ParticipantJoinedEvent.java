package ch.junto.chat.event;

import java.util.UUID;

public record ParticipantJoinedEvent(UUID activityId, String displayName) {}
