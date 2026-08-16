package ch.junto.activitytype.definition;

import ch.junto.activitytype.enums.FieldType;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class YogaType implements ActivityTypeDefinition {

  private static final List<String> STYLES =
      List.of("Vinyasa", "Hatha", "Ashtanga", "Yin", "Bikram", "Restorative");
  private static final List<String> LEVELS = List.of("Beginner", "Intermediate", "Advanced");

  @Override
  public String getSlug() {
    return "yoga";
  }

  @Override
  public String getName() {
    return "Yoga";
  }

  @Override
  public String getIconName() {
    return "yoga";
  }

  @Override
  public String getDefaultImageUrl() {
    return "https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?auto=format&fit=crop&w=800&q=80";
  }

  @Override
  public List<CustomFieldDefinition> getFieldDefinitions() {
    return List.of(
        new CustomFieldDefinition("style", "Style", FieldType.SELECT, true, STYLES, null),
        new CustomFieldDefinition("level", "Level", FieldType.SELECT, true, LEVELS, null),
        new CustomFieldDefinition(
            "bringMat", "Bring your own mat", FieldType.BOOLEAN, false, null, null));
  }

  @Override
  public List<String> getSummaryFields() {
    return List.of("style");
  }
}
