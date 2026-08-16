package ch.junto.activitytype.definition;

import java.util.List;

/**
 * Defines an activity type's metadata and field schema.
 *
 * <p>Slugs and field names are persisted in the activity table and must remain stable once
 * released. Changing a slug requires a data migration.
 */
public interface ActivityTypeDefinition {
  /** Stable identifier. Must match ^[a-z0-9-]+$ and never be renamed. */
  String getSlug();

  String getName();

  String getIconName();

  /** Required. Fallback image when activity has no custom imageUrl. */
  String getDefaultImageUrl();

  /** Field names must be unique within this list. */
  List<CustomFieldDefinition> getFieldDefinitions();

  /** Ordered list referencing field names from getFieldDefinitions(). */
  List<String> getSummaryFields();
}
