package ch.junto.activity.validation;

import ch.junto.activitytype.definition.CustomFieldDefinition;
import ch.junto.activitytype.model.FieldType;
import ch.junto.activitytype.model.Unit;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
public class SkiingFieldValidator implements ActivityFieldValidator {
    private static final List<String> LEVELS = List.of("Beginner", "Intermediate", "Advanced", "Expert");
    @Override public String getActivityTypeSlug() { return "skiing"; }
    @Override
    public List<CustomFieldDefinition> getFieldDefinitions() {
        return List.of(
            new CustomFieldDefinition("level", "Level", FieldType.SELECT, true, LEVELS, null),
            new CustomFieldDefinition("elevationGain", "Elevation Gain", FieldType.NUMBER_WITH_UNIT, false, null, Unit.METERS)
        );
    }
    @Override public void validate(Map<String, Object> customFields) { validateFields(customFields, getFieldDefinitions()); }
}
