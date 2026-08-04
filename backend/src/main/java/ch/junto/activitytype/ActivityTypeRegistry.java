package ch.junto.activitytype;

import ch.junto.activitytype.definition.ActivityTypeDefinition;
import ch.junto.activitytype.definition.CustomFieldDefinition;
import ch.junto.core.exception.NotFoundException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ActivityTypeRegistry {

  private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9-]+$");

  private final Map<String, ActivityTypeDefinition> types;
  private final List<ActivityTypeDefinition> sortedTypes;

  public ActivityTypeRegistry(List<ActivityTypeDefinition> definitions) {
    validate(definitions);
    this.types =
        definitions.stream().collect(Collectors.toMap(ActivityTypeDefinition::getSlug, d -> d));
    this.sortedTypes =
        definitions.stream().sorted(Comparator.comparing(ActivityTypeDefinition::getSlug)).toList();
  }

  public ActivityTypeDefinition get(String slug) {
    ActivityTypeDefinition type = types.get(slug);
    if (type == null) {
      throw new NotFoundException("Unknown activity type: " + slug);
    }
    return type;
  }

  public List<ActivityTypeDefinition> listAll() {
    return sortedTypes;
  }

  public boolean exists(String slug) {
    return types.containsKey(slug);
  }

  private void validate(List<ActivityTypeDefinition> definitions) {
    Set<String> seenSlugs = new HashSet<>();

    for (ActivityTypeDefinition def : definitions) {
      String slug = def.getSlug();

      requireNonBlank(slug, "slug");
      if (!SLUG_PATTERN.matcher(slug).matches()) {
        throw new IllegalStateException(
            "Activity type slug '" + slug + "' must match ^[a-z0-9-]+$");
      }
      if (!seenSlugs.add(slug)) {
        throw new IllegalStateException("Duplicate activity type slug: " + slug);
      }

      requireNonBlank(def.getName(), "name for type '" + slug + "'");
      requireNonBlank(def.getIconName(), "iconName for type '" + slug + "'");
      requireNonBlank(def.getDefaultImageUrl(), "defaultImageUrl for type '" + slug + "'");

      List<CustomFieldDefinition> fields = def.getFieldDefinitions();
      if (fields == null || fields.isEmpty()) {
        throw new IllegalStateException(
            "Activity type '" + slug + "' must define at least one field");
      }

      Set<String> fieldNames = new HashSet<>();
      for (CustomFieldDefinition field : fields) {
        if (!fieldNames.add(field.name())) {
          throw new IllegalStateException(
              "Duplicate field name '" + field.name() + "' in type '" + slug + "'");
        }
      }

      List<String> summaryFields = def.getSummaryFields();
      if (summaryFields != null) {
        Set<String> seenSummary = new HashSet<>();
        for (String sf : summaryFields) {
          if (!fieldNames.contains(sf)) {
            throw new IllegalStateException(
                "Summary field '"
                    + sf
                    + "' in type '"
                    + slug
                    + "' does not reference a defined field");
          }
          if (!seenSummary.add(sf)) {
            throw new IllegalStateException(
                "Duplicate summary field '" + sf + "' in type '" + slug + "'");
          }
        }
      }
    }
  }

  private void requireNonBlank(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Activity type " + fieldName + " must not be blank");
    }
  }
}
