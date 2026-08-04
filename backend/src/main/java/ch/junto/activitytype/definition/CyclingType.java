package ch.junto.activitytype.definition;

import ch.junto.activitytype.enums.FieldType;
import ch.junto.activitytype.enums.Unit;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CyclingType implements ActivityTypeDefinition {

  private static final List<String> DIFFICULTIES = List.of("Beginner", "Intermediate", "Advanced");

  @Override
  public String getSlug() {
    return "cycling";
  }

  @Override
  public String getName() {
    return "Cycling";
  }

  @Override
  public String getIconName() {
    return "bike";
  }

  @Override
  public String getDefaultImageUrl() {
    return "https://images.unsplash.com/photo-1541625602330-2277a4c4b08d?auto=format&fit=crop&w=800&q=80";
  }

  @Override
  public List<CustomFieldDefinition> getFieldDefinitions() {
    return List.of(
        new CustomFieldDefinition(
            "distance", "Distance", FieldType.NUMBER_WITH_UNIT, true, null, Unit.KM),
        new CustomFieldDefinition(
            "duration", "Duration", FieldType.NUMBER_WITH_UNIT, false, null, Unit.HOURS),
        new CustomFieldDefinition(
            "difficulty", "Difficulty", FieldType.SELECT, true, DIFFICULTIES, null));
  }

  @Override
  public List<String> getSummaryFields() {
    return List.of("distance");
  }
}
