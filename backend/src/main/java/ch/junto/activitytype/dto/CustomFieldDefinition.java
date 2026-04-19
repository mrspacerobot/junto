package ch.junto.activitytype.dto;

import ch.junto.activitytype.model.FieldType;
import ch.junto.activitytype.model.Unit;

import java.util.List;

public record CustomFieldDefinition(
    String name,
    String label,
    FieldType type,
    boolean required,
    List<String> options,
    Unit unit
) {
    public CustomFieldDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label is required");
        }
        options = options == null ? List.of() : List.copyOf(options);
        if (type == FieldType.SELECT && options.isEmpty()) {
            throw new IllegalArgumentException("SELECT fields must define options");
        }
        if (type != FieldType.SELECT && !options.isEmpty()) {
            throw new IllegalArgumentException("Only SELECT fields may define options");
        }
        if (type == FieldType.NUMBER_WITH_UNIT && unit == null) {
            throw new IllegalArgumentException("NUMBER_WITH_UNIT fields must define a unit");
        }
        if (type != FieldType.NUMBER_WITH_UNIT && unit != null) {
            throw new IllegalArgumentException("Only NUMBER_WITH_UNIT fields may define a unit");
        }
    }
}
