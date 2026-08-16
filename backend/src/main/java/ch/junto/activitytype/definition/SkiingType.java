package ch.junto.activitytype.definition;

import ch.junto.activitytype.enums.FieldType;
import ch.junto.activitytype.enums.Unit;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SkiingType implements ActivityTypeDefinition {

  private static final List<String> LEVELS =
      List.of("Beginner", "Intermediate", "Advanced", "Expert");

  @Override
  public String getSlug() {
    return "skiing";
  }

  @Override
  public String getName() {
    return "Skiing";
  }

  @Override
  public String getIconName() {
    return "skiing";
  }

  @Override
  public String getDefaultImageUrl() {
    return "https://images.unsplash.com/photo-1551698618-1fed5d975506?auto=format&fit=crop&w=800&q=80";
  }

  @Override
  public List<CustomFieldDefinition> getFieldDefinitions() {
    return List.of(
        new CustomFieldDefinition("level", "Level", FieldType.SELECT, true, LEVELS, null),
        new CustomFieldDefinition(
            "elevationGain",
            "Elevation Gain",
            FieldType.NUMBER_WITH_UNIT,
            false,
            null,
            Unit.METERS));
  }

  @Override
  public List<String> getSummaryFields() {
    return List.of("level");
  }
}
