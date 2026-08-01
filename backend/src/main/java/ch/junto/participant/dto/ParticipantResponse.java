package ch.junto.participant.dto;

import ch.junto.participant.enums.ParticipantRole;
import ch.junto.participant.enums.ParticipantStatus;
import java.util.UUID;

public record ParticipantResponse(
    UUID id,
    UUID userId,
    String displayName,
    String avatarUrl,
    ParticipantRole role,
    ParticipantStatus status
) {}
