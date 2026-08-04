package ch.junto.activity.dto;

import ch.junto.activity.entity.CustomFields;
import ch.junto.activity.enums.JoinPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record ActivityCreateRequest(
    @NotBlank String title,
    String description,
    @NotBlank @Schema(example = "cycling") String typeSlug,
    String city,
    @DecimalMin("-90.0") @DecimalMax("90.0") Double locationLat,
    @DecimalMin("-180.0") @DecimalMax("180.0") Double locationLng,
    @NotNull OffsetDateTime startsAt,
    OffsetDateTime signupDeadline,
    @NotNull @Min(1) Integer maxParticipants,
    @NotNull JoinPolicy joinPolicy,
    CustomFields customFields) {}
