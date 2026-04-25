package ch.junto.activity.validation;

import ch.junto.activitytype.definition.CustomFieldDefinition;
import ch.junto.activitytype.model.FieldType;
import ch.junto.activitytype.model.Unit;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
public class CyclingFieldValidator implements ActivityFieldValidator {
    private static final List<String> DIFFICULTIES = List.of("Beginner", "Intermediate", "Advanced");
    @Override public String getActivityTypeSlug() { return "cycling"; }
    @Override
    public List<CustomFieldDefinition> getFieldDefinitions() {
        return List.of(
            new CustomFieldDefinition("distance", "Distance", FieldType.NUMBER_WITH_UNIT, true, null, Unit.KM),
            new CustomFieldDefinition("duration", "Duration", FieldType.NUMBER_WITH_UNIT, false, null, Unit.HOURS),
            new CustomFieldDefinition("difficulty", "Difficulty", FieldType.SELECT, true, DIFFICULTIES, null)
        );
    }
    @Override public void validate(Map<String, Object> customFields) { validateFields(customFields, getFieldDefinitions()); }
}
