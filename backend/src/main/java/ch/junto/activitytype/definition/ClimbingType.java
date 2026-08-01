package ch.junto.activitytype.definition;

import ch.junto.activitytype.enums.FieldType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ClimbingType implements ActivityTypeDefinition {

    private static final List<String> STYLES = List.of("Bouldering", "Sport", "Trad", "Top Rope", "Ice", "Alpine");
    private static final List<String> ENVIRONMENTS = List.of("Indoor", "Outdoor");
    private static final List<String> EXPERIENCE_LEVELS = List.of("Beginner", "Intermediate", "Advanced");

    @Override public String getSlug() { return "climbing"; }
    @Override public String getName() { return "Climbing"; }
    @Override public String getIconName() { return "mountain"; }
    @Override public String getDefaultImageUrl() { return "https://images.unsplash.com/photo-1522163182402-834f871fd851?auto=format&fit=crop&w=800&q=80"; }

    @Override
    public List<CustomFieldDefinition> getFieldDefinitions() {
        return List.of(
            new CustomFieldDefinition("style", "Style", FieldType.SELECT, true, STYLES, null),
            new CustomFieldDefinition("grade", "Difficulty Grade", FieldType.TEXT, false, null, null),
            new CustomFieldDefinition("environment", "Environment", FieldType.SELECT, true, ENVIRONMENTS, null),
            new CustomFieldDefinition("experienceLevel", "Recommended Experience", FieldType.SELECT, true, EXPERIENCE_LEVELS, null),
            new CustomFieldDefinition("needsEquipment", "Requires Gear", FieldType.BOOLEAN, false, null, null)
        );
    }

    @Override
    public List<String> getSummaryFields() { return List.of("style", "grade"); }
}
