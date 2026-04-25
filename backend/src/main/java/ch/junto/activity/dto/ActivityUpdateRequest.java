package ch.junto.activity.dto;

import ch.junto.activity.entity.CustomFields;
import ch.junto.activity.enums.JoinPolicy;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;

public record ActivityUpdateRequest(
    @NotBlank String title,
    String description,
    String city,
    Double locationLat,
    Double locationLng,
    OffsetDateTime startsAt,
    OffsetDateTime signupDeadline,
    @Min(1) Integer maxParticipants,
    JoinPolicy joinPolicy,
    CustomFields customFields
) {}
