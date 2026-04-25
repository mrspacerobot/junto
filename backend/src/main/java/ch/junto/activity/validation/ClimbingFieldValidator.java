package ch.junto.activity.validation;

import ch.junto.activity.entity.Activity;
import ch.junto.activitytype.definition.CustomFieldDefinition;
import ch.junto.activitytype.model.FieldType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ClimbingFieldValidator implements ActivityFieldValidator {

    private static final List<String> STYLES = List.of("Bouldering", "Sport", "Trad", "Top Rope", "Ice", "Alpine");
    private static final List<String> ENVIRONMENTS = List.of("Indoor", "Outdoor");
    private static final List<String> EXPERIENCE_LEVELS = List.of("Beginner", "Intermediate", "Advanced");

    @Override
    public String getActivityTypeSlug() { return "climbing"; }

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
    public void validate(Map<String, Object> customFields) {
        validateFields(customFields, getFieldDefinitions());
    }

    @Override
    public void normalize(Activity activity) {
        Map<String, Object> fields = activity.getCustomFields();
        if (fields.containsKey("style")) {
            activity.setSubtype(fields.get("style").toString());
        }
        if (fields.containsKey("grade")) {
            activity.setDifficultyLabel(fields.get("grade").toString());
        }
    }
}
