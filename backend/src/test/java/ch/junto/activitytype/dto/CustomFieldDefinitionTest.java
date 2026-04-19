package ch.junto.activitytype.dto;

import ch.junto.activitytype.model.FieldType;
import ch.junto.activitytype.model.Unit;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomFieldDefinitionTest {

    @Test
    void createsValidSelectField() {
        CustomFieldDefinition def = new CustomFieldDefinition(
            "difficulty", "Difficulty", FieldType.SELECT, true,
            List.of("BEGINNER", "INTERMEDIATE", "ADVANCED"), null);
        assertThat(def.name()).isEqualTo("difficulty");
        assertThat(def.options()).containsExactly("BEGINNER", "INTERMEDIATE", "ADVANCED");
        assertThat(def.unit()).isNull();
    }

    @Test
    void createsValidNumberWithUnitField() {
        CustomFieldDefinition def = new CustomFieldDefinition(
            "distance", "Distance", FieldType.NUMBER_WITH_UNIT, true, null, Unit.KM);
        assertThat(def.unit()).isEqualTo(Unit.KM);
        assertThat(def.options()).isEmpty();
    }

    @Test
    void createsValidTextField() {
        CustomFieldDefinition def = new CustomFieldDefinition(
            "notes", "Notes", FieldType.TEXT, false, null, null);
        assertThat(def.options()).isEmpty();
        assertThat(def.unit()).isNull();
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> new CustomFieldDefinition(
            "", "Label", FieldType.TEXT, false, null, null))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("name is required");
    }

    @Test
    void rejectsNullName() {
        assertThatThrownBy(() -> new CustomFieldDefinition(
            null, "Label", FieldType.TEXT, false, null, null))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("name is required");
    }

    @Test
    void rejectsBlankLabel() {
        assertThatThrownBy(() -> new CustomFieldDefinition(
            "name", "  ", FieldType.TEXT, false, null, null))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("label is required");
    }

    @Test
    void rejectsSelectWithoutOptions() {
        assertThatThrownBy(() -> new CustomFieldDefinition(
            "style", "Style", FieldType.SELECT, true, List.of(), null))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("SELECT fields must define options");
    }

    @Test
    void rejectsNonSelectWithOptions() {
        assertThatThrownBy(() -> new CustomFieldDefinition(
            "count", "Count", FieldType.NUMBER, false, List.of("A", "B"), null))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("Only SELECT fields may define options");
    }

    @Test
    void rejectsNumberWithUnitWithoutUnit() {
        assertThatThrownBy(() -> new CustomFieldDefinition(
            "distance", "Distance", FieldType.NUMBER_WITH_UNIT, true, null, null))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("NUMBER_WITH_UNIT fields must define a unit");
    }

    @Test
    void rejectsNonNumberWithUnitWithUnit() {
        assertThatThrownBy(() -> new CustomFieldDefinition(
            "count", "Count", FieldType.NUMBER, false, null, Unit.KM))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("Only NUMBER_WITH_UNIT fields may define a unit");
    }

    @Test
    void normalizesNullOptionsToEmptyList() {
        CustomFieldDefinition def = new CustomFieldDefinition(
            "notes", "Notes", FieldType.TEXT, false, null, null);
        assertThat(def.options()).isEmpty();
    }
}
