package ch.junto.activity;

import ch.junto.activitytype.ActivityTypeRegistry;
import ch.junto.activitytype.definition.ActivityTypeDefinition;
import ch.junto.activitytype.definition.CustomFieldDefinition;
import ch.junto.core.exception.ApiException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ActivityFieldValidationService {

    private final ActivityTypeRegistry registry;

    public ActivityFieldValidationService(ActivityTypeRegistry registry) {
        this.registry = registry;
    }

    public void validate(String typeSlug, Map<String, Object> customFields) {
        ActivityTypeDefinition type = registry.get(typeSlug);
        validateFields(customFields, type.getFieldDefinitions());
    }

    private void validateFields(Map<String, Object> customFields, List<CustomFieldDefinition> definitions) {
        if (customFields == null) {
            throw new ApiException("VALIDATION_ERROR", "Custom fields are required", 400);
        }

        Set<String> allowedNames = definitions.stream()
            .map(CustomFieldDefinition::name)
            .collect(Collectors.toSet());

        for (String key : customFields.keySet()) {
            if (!allowedNames.contains(key)) {
                throw new ApiException("VALIDATION_ERROR", "Unknown field: " + key, 400);
            }
        }

        for (CustomFieldDefinition def : definitions) {
            Object value = customFields.get(def.name());

            if (value == null) {
                if (def.required()) {
                    throw new ApiException("VALIDATION_ERROR", "Required field missing: " + def.name(), 400);
                }
                continue;
            }

            switch (def.type()) {
                case TEXT -> validateTextField(def.name(), value, def.required());
                case NUMBER, NUMBER_WITH_UNIT -> validateNumberField(def.name(), value);
                case SELECT -> validateSelectField(def.name(), value, def.options());
                case BOOLEAN -> validateBooleanField(def.name(), value);
                case TIME_OF_DAY -> validateTimeField(def.name(), value);
                case DATE_RANGE -> validateDateRangeField(def.name(), value);
            }
        }
    }

    private void validateTextField(String name, Object value, boolean required) {
        if (!(value instanceof String)) {
            throw new ApiException("VALIDATION_ERROR", name + " must be a string", 400);
        }
        if (required && ((String) value).isBlank()) {
            throw new ApiException("VALIDATION_ERROR", name + " must not be blank", 400);
        }
    }

    private void validateNumberField(String name, Object value) {
        if (!(value instanceof Number)) {
            throw new ApiException("VALIDATION_ERROR", name + " must be a number", 400);
        }
    }

    private void validateSelectField(String name, Object value, List<String> options) {
        if (!(value instanceof String)) {
            throw new ApiException("VALIDATION_ERROR", name + " must be a string", 400);
        }
        if (!options.contains(value)) {
            throw new ApiException("VALIDATION_ERROR", name + " has invalid value: " + value, 400);
        }
    }

    private void validateBooleanField(String name, Object value) {
        if (!(value instanceof Boolean)) {
            throw new ApiException("VALIDATION_ERROR", name + " must be a boolean", 400);
        }
    }

    private void validateTimeField(String name, Object value) {
        if (!(value instanceof String timeStr)) {
            throw new ApiException("VALIDATION_ERROR", name + " must be a time string (HH:mm)", 400);
        }
        try {
            LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (DateTimeParseException e) {
            throw new ApiException("VALIDATION_ERROR", name + " must be in HH:mm format", 400);
        }
    }

    private void validateDateRangeField(String name, Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new ApiException("VALIDATION_ERROR", name + " must be an object with start and end", 400);
        }
        Object startObj = map.get("start");
        Object endObj = map.get("end");
        if (startObj == null || endObj == null) {
            throw new ApiException("VALIDATION_ERROR", name + " must have both start and end dates", 400);
        }
        try {
            LocalDate start = LocalDate.parse(startObj.toString());
            LocalDate end = LocalDate.parse(endObj.toString());
            if (end.isBefore(start)) {
                throw new ApiException("VALIDATION_ERROR", name + " end date must be >= start date", 400);
            }
        } catch (DateTimeParseException e) {
            throw new ApiException("VALIDATION_ERROR", name + " must contain valid ISO dates (YYYY-MM-DD)", 400);
        }
    }
}
