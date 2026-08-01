package ch.junto.activitytype.dto;

import ch.junto.activitytype.definition.CustomFieldDefinition;
import java.util.List;

public record ActivityTypeResponse(
    String slug,
    String name,
    String iconName,
    String defaultImageUrl,
    List<CustomFieldDefinition> customFields,
    List<String> summaryFields
) {}
