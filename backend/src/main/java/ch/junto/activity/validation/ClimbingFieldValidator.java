package ch.junto.activity.validation;

import ch.junto.activity.entity.Activity;
import ch.junto.activitytype.dto.CustomFieldDefinition;
import ch.junto.activitytype.model.FieldType;
import ch.junto.activitytype.model.ValidatorConfig;
import ch.junto.activity.model.Option;
import org.springframework.stereotype.Component;
import ch.junto.core.exception.ApiException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ClimbingFieldValidator implements ActivityFieldValidator {

    private static final List<String> STYLES = List.of("Bouldering", "Sport", "Trad", "Top Rope", "Ice", "Alpine");
    private static final List<String> ENVIRONIRONMENTS = List.of("Indoor", "Outdoor");
    private static final List<String> EXPERIENCE_LEVELS = List.of("Beginner", "Intermediate", "Advanced");

    @Override
    public String getActivityTypeSlug() {
        return "climbing";
    }

    @Override
    public List<CustomFieldDefinition> getFieldDefinitions() {
        return List.of(
            new CustomFieldDefinition("style", "Style", FieldType.SELECT, true, STYLES, null),
            new CustomFieldDefinition("grade", "Difficulty Grade", FieldType.TEXT, false, null, null),
            new CustomFieldDefinition("environment", "Environment", FieldType.SELECT, true, ENVIRONIRONMENTS, null),
            new CustomFieldDefinition("experienceLevel", "Recommended Experience", FieldType.SELECT, true, EXPERIENCE_LEVELS, null),
            new CustomFieldDefinition("needsEquipment", "Requires Gear", FieldType.BOOLEAN, false, null, null)
        );
    }

    @Override
    public void validate(Map<String, Object> customFields) {
        if (customFields == null || customFields.isEmpty()) {
            throw new ApiException("VALIDATION_ERROR", "Custom fields are required for climbing", 400);
        }

        Object style = customFields.get("style");
        if (style == null || !STYLES.contains(style.toString())) {
            throw new ApiException("VALIDATION_ERROR", "Invalid or missing climbing style", 400);
        }

        Object environment = customFields.get("environment");
        if (environment == null || !ENVIRONIRONMENTS.contains(environment.toString())) {
            throw new ApiException("VALIDATION_ERROR", "Invalid or missing environment", 400);
        }

        Object expLevel = customFields.get("experienceLevel");
        if (expLevel == null || !EXPERIENCE_LEVELS.contains(expLevel.toString())) {
            throw new ApiException("VALIDATION_ERROR", "Invalid or missing experience level", 400);
        }
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