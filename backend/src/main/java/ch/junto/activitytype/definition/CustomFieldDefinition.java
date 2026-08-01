package ch.junto.activitytype.definition;

import ch.junto.activitytype.enums.FieldType;
import ch.junto.activitytype.enums.Unit;
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
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        if (label == null || label.isBlank()) throw new IllegalArgumentException("label must not be blank");
        if (type == FieldType.SELECT) {
            if (options == null || options.isEmpty()) throw new IllegalArgumentException("SELECT fields must define options");
            options = List.copyOf(options);
        } else {
            if (options != null && !options.isEmpty()) throw new IllegalArgumentException("Only SELECT fields may define options");
            options = List.of();
        }
        if (type == FieldType.NUMBER_WITH_UNIT) {
            if (unit == null) throw new IllegalArgumentException("NUMBER_WITH_UNIT fields must define a unit");
        } else {
            if (unit != null) throw new IllegalArgumentException("Only NUMBER_WITH_UNIT fields may define a unit");
        }
    }
}
