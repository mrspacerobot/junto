package ch.junto.chat.event;

import java.util.UUID;

public record ParticipantLeftEvent(UUID activityId, String displayName) {}
