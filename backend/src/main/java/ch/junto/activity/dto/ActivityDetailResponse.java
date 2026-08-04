package ch.junto.activity.dto;

import ch.junto.activity.entity.CustomFields;
import ch.junto.activity.enums.ActivityStatus;
import ch.junto.activity.enums.JoinPolicy;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ActivityDetailResponse(
    UUID id,
    String title,
    String description,
    String activityTypeName,
    String activityTypeSlug,
    String city,
    Double locationLat,
    Double locationLng,
    OffsetDateTime startsAt,
    OffsetDateTime signupDeadline,
    int maxParticipants,
    int currentParticipants,
    JoinPolicy joinPolicy,
    ActivityStatus status,
    CustomFields customFields,
    String imageUrl,
    UUID creatorId,
    String creatorDisplayName,
    String creatorAvatarUrl,
    boolean isJoinable,
    OffsetDateTime effectiveDeadline) {}
