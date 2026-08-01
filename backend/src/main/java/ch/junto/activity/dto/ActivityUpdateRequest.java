package ch.junto.activity.dto;

import ch.junto.activity.entity.CustomFields;
import ch.junto.activity.enums.JoinPolicy;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record ActivityUpdateRequest(
    @NotBlank String title,
    String description,
    String city,
    @DecimalMin("-90.0") @DecimalMax("90.0") Double locationLat,
    @DecimalMin("-180.0") @DecimalMax("180.0") Double locationLng,
    @NotNull OffsetDateTime startsAt,
    OffsetDateTime signupDeadline,
    @Min(1) Integer maxParticipants,
    JoinPolicy joinPolicy,
    CustomFields customFields
) {}
