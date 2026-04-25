package ch.junto.activitytype.dto;

import ch.junto.activitytype.definition.CustomFieldDefinition;
import java.util.List;
import java.util.UUID;

public record ActivityTypeResponse(
    UUID id,
    String slug,
    String name,
    String iconName,
    List<CustomFieldDefinition> customFields
) {}
