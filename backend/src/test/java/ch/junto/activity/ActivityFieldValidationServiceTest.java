package ch.junto.activity;

import ch.junto.activitytype.ActivityTypeRegistry;
import ch.junto.activitytype.definition.ActivityTypeDefinition;
import ch.junto.activitytype.definition.ClimbingType;
import ch.junto.activitytype.definition.CustomFieldDefinition;
import ch.junto.activitytype.enums.FieldType;
import ch.junto.activitytype.enums.Unit;
import ch.junto.core.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActivityFieldValidationServiceTest {

    private ActivityFieldValidationService serviceFor(List<CustomFieldDefinition> defs) {
        ActivityTypeDefinition testType = new ActivityTypeDefinition() {
            @Override public String getSlug() { return "test"; }
            @Override public String getName() { return "Test"; }
            @Override public String getIconName() { return "test"; }
            @Override public String getDefaultImageUrl() { return "https://example.com/test.jpg"; }
            @Override public List<CustomFieldDefinition> getFieldDefinitions() { return defs; }
            @Override public List<String> getSummaryFields() { return List.of(); }
        };
        var registry = new ActivityTypeRegistry(List.of(testType));
        return new ActivityFieldValidationService(registry);
    }

    private void validate(List<CustomFieldDefinition> defs, Map<String, Object> fields) {
        serviceFor(defs).validate("test", fields);
    }

    // --- TEXT ---
    @Test void textField_acceptsString() {
        var defs = List.of(new CustomFieldDefinition("n", "N", FieldType.TEXT, false, null, null));
        assertThatCode(() -> validate(defs, Map.of("n", "hello"))).doesNotThrowAnyException();
    }
    @Test void textField_rejectsNonString() {
        var defs = List.of(new CustomFieldDefinition("n", "N", FieldType.TEXT, false, null, null));
        var f = new HashMap<String, Object>(); f.put("n", 42);
        assertThatThrownBy(() -> validate(defs, f)).isInstanceOf(ApiException.class);
    }
    @Test void requiredTextField_rejectsBlank() {
        var defs = List.of(new CustomFieldDefinition("n", "N", FieldType.TEXT, true, null, null));
        var f = new HashMap<String, Object>(); f.put("n", "  ");
        assertThatThrownBy(() -> validate(defs, f)).isInstanceOf(ApiException.class);
    }

    // --- NUMBER ---
    @Test void numberField_acceptsInteger() {
        var defs = List.of(new CustomFieldDefinition("n", "N", FieldType.NUMBER, false, null, null));
        assertThatCode(() -> validate(defs, Map.of("n", 8))).doesNotThrowAnyException();
    }
    @Test void numberField_acceptsDecimal() {
        var defs = List.of(new CustomFieldDefinition("n", "N", FieldType.NUMBER, false, null, null));
        assertThatCode(() -> validate(defs, Map.of("n", 2.5))).doesNotThrowAnyException();
    }
    @Test void numberField_rejectsNonNumber() {
        var defs = List.of(new CustomFieldDefinition("n", "N", FieldType.NUMBER, false, null, null));
        var f = new HashMap<String, Object>(); f.put("n", "eight");
        assertThatThrownBy(() -> validate(defs, f)).isInstanceOf(ApiException.class);
    }

    // --- NUMBER_WITH_UNIT ---
    @Test void numberWithUnit_acceptsNumber() {
        var defs = List.of(new CustomFieldDefinition("d", "D", FieldType.NUMBER_WITH_UNIT, true, null, Unit.KM));
        assertThatCode(() -> validate(defs, Map.of("d", 45))).doesNotThrowAnyException();
    }
    @Test void numberWithUnit_rejectsNonNumber() {
        var defs = List.of(new CustomFieldDefinition("d", "D", FieldType.NUMBER_WITH_UNIT, true, null, Unit.KM));
        var f = new HashMap<String, Object>(); f.put("d", "far");
        assertThatThrownBy(() -> validate(defs, f)).isInstanceOf(ApiException.class);
    }

    // --- SELECT ---
    @Test void selectField_acceptsValidOption() {
        var defs = List.of(new CustomFieldDefinition("l", "L", FieldType.SELECT, true, List.of("A","B","C"), null));
        assertThatCode(() -> validate(defs, Map.of("l", "A"))).doesNotThrowAnyException();
    }
    @Test void selectField_rejectsInvalidOption() {
        var defs = List.of(new CustomFieldDefinition("l", "L", FieldType.SELECT, true, List.of("A","B","C"), null));
        var f = new HashMap<String, Object>(); f.put("l", "Z");
        assertThatThrownBy(() -> validate(defs, f)).isInstanceOf(ApiException.class);
    }

    // --- BOOLEAN ---
    @Test void booleanField_acceptsBoolean() {
        var defs = List.of(new CustomFieldDefinition("b", "B", FieldType.BOOLEAN, false, null, null));
        assertThatCode(() -> validate(defs, Map.of("b", true))).doesNotThrowAnyException();
    }
    @Test void booleanField_rejectsNonBoolean() {
        var defs = List.of(new CustomFieldDefinition("b", "B", FieldType.BOOLEAN, false, null, null));
        var f = new HashMap<String, Object>(); f.put("b", "yes");
        assertThatThrownBy(() -> validate(defs, f)).isInstanceOf(ApiException.class);
    }

    // --- TIME_OF_DAY ---
    @Test void timeOfDay_acceptsValidTime() {
        var defs = List.of(new CustomFieldDefinition("t", "T", FieldType.TIME_OF_DAY, true, null, null));
        assertThatCode(() -> validate(defs, Map.of("t", "09:00"))).doesNotThrowAnyException();
    }
    @Test void timeOfDay_rejectsInvalidTime() {
        var defs = List.of(new CustomFieldDefinition("t", "T", FieldType.TIME_OF_DAY, true, null, null));
        var f = new HashMap<String, Object>(); f.put("t", "9am");
        assertThatThrownBy(() -> validate(defs, f)).isInstanceOf(ApiException.class);
    }
    @Test void timeOfDay_rejectsTimeWithSeconds() {
        var defs = List.of(new CustomFieldDefinition("t", "T", FieldType.TIME_OF_DAY, true, null, null));
        var f = new HashMap<String, Object>(); f.put("t", "09:00:30");
        assertThatThrownBy(() -> validate(defs, f)).isInstanceOf(ApiException.class);
    }

    // --- DATE_RANGE ---
    @Test void dateRange_acceptsValidRange() {
        var defs = List.of(new CustomFieldDefinition("d", "D", FieldType.DATE_RANGE, true, null, null));
        assertThatCode(() -> validate(defs, Map.of("d", Map.of("start", "2026-06-01", "end", "2026-06-03")))).doesNotThrowAnyException();
    }
    @Test void dateRange_acceptsSameDay() {
        var defs = List.of(new CustomFieldDefinition("d", "D", FieldType.DATE_RANGE, true, null, null));
        assertThatCode(() -> validate(defs, Map.of("d", Map.of("start", "2026-06-01", "end", "2026-06-01")))).doesNotThrowAnyException();
    }
    @Test void dateRange_rejectsEndBeforeStart() {
        var defs = List.of(new CustomFieldDefinition("d", "D", FieldType.DATE_RANGE, true, null, null));
        assertThatThrownBy(() -> validate(defs, Map.of("d", Map.of("start", "2026-06-03", "end", "2026-06-01")))).isInstanceOf(ApiException.class);
    }
    @Test void dateRange_rejectsMissingEnd() {
        var defs = List.of(new CustomFieldDefinition("d", "D", FieldType.DATE_RANGE, true, null, null));
        assertThatThrownBy(() -> validate(defs, Map.of("d", Map.of("start", "2026-06-01")))).isInstanceOf(ApiException.class);
    }

    // --- Schema-level ---
    @Test void rejectsUnknownFieldName() {
        var defs = List.of(new CustomFieldDefinition("n", "N", FieldType.TEXT, false, null, null));
        assertThatThrownBy(() -> validate(defs, Map.of("unknown", "value"))).isInstanceOf(ApiException.class);
    }
    @Test void rejectsMissingRequiredField() {
        var defs = List.of(new CustomFieldDefinition("n", "N", FieldType.TEXT, true, null, null));
        assertThatThrownBy(() -> validate(defs, new HashMap<>())).isInstanceOf(ApiException.class);
    }
    @Test void allowsMissingOptionalField() {
        var defs = List.of(new CustomFieldDefinition("n", "N", FieldType.TEXT, false, null, null));
        assertThatCode(() -> validate(defs, new HashMap<>())).doesNotThrowAnyException();
    }
    @Test void rejectsNullValueForRequiredField() {
        var defs = List.of(new CustomFieldDefinition("n", "N", FieldType.TEXT, true, null, null));
        var f = new HashMap<String, Object>(); f.put("n", null);
        assertThatThrownBy(() -> validate(defs, f)).isInstanceOf(ApiException.class);
    }

    // --- Integration with real type definitions ---
    @Test void validate_climbingFields_passes() {
        var registry = new ActivityTypeRegistry(List.of(new ClimbingType()));
        var service = new ActivityFieldValidationService(registry);
        var fields = new HashMap<String, Object>();
        fields.put("style", "Bouldering");
        fields.put("grade", "V5");
        fields.put("environment", "Indoor");
        fields.put("experienceLevel", "Beginner");
        fields.put("needsEquipment", true);
        assertThatCode(() -> service.validate("climbing", fields)).doesNotThrowAnyException();
    }

    @Test void validate_climbingFields_invalidStyle_throws() {
        var registry = new ActivityTypeRegistry(List.of(new ClimbingType()));
        var service = new ActivityFieldValidationService(registry);
        var fields = new HashMap<String, Object>();
        fields.put("style", "InvalidStyle");
        fields.put("environment", "Outdoor");
        fields.put("experienceLevel", "Intermediate");
        assertThatThrownBy(() -> service.validate("climbing", fields)).isInstanceOf(ApiException.class);
    }

    @Test void validate_climbingFields_missingRequired_throws() {
        var registry = new ActivityTypeRegistry(List.of(new ClimbingType()));
        var service = new ActivityFieldValidationService(registry);
        assertThatThrownBy(() -> service.validate("climbing", new HashMap<>())).isInstanceOf(ApiException.class);
    }
}
