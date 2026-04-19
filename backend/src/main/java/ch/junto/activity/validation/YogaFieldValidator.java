package ch.junto.activity.validation;

import ch.junto.activitytype.dto.CustomFieldDefinition;
import ch.junto.activitytype.model.FieldType;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
public class YogaFieldValidator implements ActivityFieldValidator {
    private static final List<String> STYLES = List.of("Vinyasa", "Hatha", "Ashtanga", "Yin", "Bikram", "Restorative");
    private static final List<String> LEVELS = List.of("Beginner", "Intermediate", "Advanced");
    @Override public String getActivityTypeSlug() { return "yoga"; }
    @Override
    public List<CustomFieldDefinition> getFieldDefinitions() {
        return List.of(
            new CustomFieldDefinition("style", "Style", FieldType.SELECT, true, STYLES, null),
            new CustomFieldDefinition("level", "Level", FieldType.SELECT, true, LEVELS, null),
            new CustomFieldDefinition("bringMat", "Bring your own mat", FieldType.BOOLEAN, false, null, null)
        );
    }
    @Override public void validate(Map<String, Object> customFields) { validateFields(customFields, getFieldDefinitions()); }
}
