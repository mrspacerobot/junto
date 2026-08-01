package ch.junto.activitytype.definition;

import ch.junto.activitytype.enums.FieldType;
import ch.junto.activitytype.enums.Unit;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RunningType implements ActivityTypeDefinition {

    private static final List<String> SURFACES = List.of("Road", "Trail", "Track", "Treadmill");
    private static final List<String> DIFFICULTIES = List.of("Beginner", "Intermediate", "Advanced");

    @Override public String getSlug() { return "running"; }
    @Override public String getName() { return "Running"; }
    @Override public String getIconName() { return "running"; }
    @Override public String getDefaultImageUrl() { return "https://images.unsplash.com/photo-1476480862126-209bfaa8edc8?auto=format&fit=crop&w=800&q=80"; }

    @Override
    public List<CustomFieldDefinition> getFieldDefinitions() {
        return List.of(
            new CustomFieldDefinition("distance", "Distance", FieldType.NUMBER_WITH_UNIT, true, null, Unit.KM),
            new CustomFieldDefinition("surface", "Surface", FieldType.SELECT, true, SURFACES, null),
            new CustomFieldDefinition("difficulty", "Difficulty", FieldType.SELECT, true, DIFFICULTIES, null)
        );
    }

    @Override
    public List<String> getSummaryFields() { return List.of("distance"); }
}
