package ch.junto.activity.dto;

import ch.junto.activity.enums.ActivityStatus;
import ch.junto.activity.entity.CustomFields;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ActivityListItemResponse(
    UUID id,
    String title,
    String activityTypeName,
    String activityTypeSlug,
    String activityTypeIconName,
    String city,
    Double locationLat,
    Double locationLng,
    OffsetDateTime startsAt,
    int maxParticipants,
    int currentParticipants,
    ActivityStatus status,
    String creatorDisplayName,
    String creatorAvatarUrl,
    CustomFields customFields,
    String imageUrl,
    List<String> participantAvatars,
    List<ActivitySummaryField> summary,
    Double distance
) {}
