package ch.junto.activity.validation;

import ch.junto.activitytype.dto.CustomFieldDefinition;
import ch.junto.activitytype.model.FieldType;
import ch.junto.activitytype.model.Unit;
import ch.junto.core.exception.ApiException;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenericFieldValidatorTest {

    private static class TestValidator implements ActivityFieldValidator {
        private final List<CustomFieldDefinition> defs;
        TestValidator(List<CustomFieldDefinition> defs) { this.defs = defs; }
        @Override public String getActivityTypeSlug() { return "test"; }
        @Override public void validate(Map<String, Object> customFields) { validateFields(customFields, defs); }
        @Override public List<CustomFieldDefinition> getFieldDefinitions() { return defs; }
    }

    @Test void textField_acceptsString() {
        var v = new TestValidator(List.of(new CustomFieldDefinition("n", "N", FieldType.TEXT, false, null, null)));
        var f = new HashMap<String, Object>(); f.put("n", "hello");
        assertThatCode(() -> v.validate(f)).doesNotThrowAnyException();
    }
    @Test void textField_rejectsNonString() {
        var v = new TestValidator(List.of(new CustomFieldDefinition("n", "N", FieldType.TEXT, false, null, null)));
        var f = new HashMap<String, Object>(); f.put("n", 42);
        assertThatThrownBy(() -> v.validate(f)).isInstanceOf(ApiException.class);
    }
    @Test void requiredTextField_rejectsBlank() {
        var v = new TestValidator(List.of(new CustomFieldDefinition("n", "N", FieldType.TEXT, true, null, null)));
        var f = new HashMap<String, Object>(); f.put("n", "  ");
        assertThatThrownBy(() -> v.validate(f)).isInstanceOf(ApiException.class);
    }
    @Test void numberField_acceptsNumber() {
        var v = new TestValidator(List.of(new CustomFieldDefinition("n", "N", FieldType.NUMBER, false, null, null)));
        var f = new HashMap<String, Object>(); f.put("n", 8);
        assertThatCode(() -> v.validate(f)).doesNotThrowAnyException();
    }
    @Test void numberField_acceptsDecimal() {
        var v = new TestValidator(List.of(new CustomFieldDefinition("n", "N", FieldType.NUMBER, false, null, null)));
        var f = new HashMap<String, Object>(); f.put("n", 2.5);
        assertThatCode(() -> v.validate(f)).doesNotThrowAnyException();
    }
    @Test void numberField_rejectsNonNumber() {
        var v = new TestValidator(List.of(new CustomFieldDefinition("n", "N", FieldType.NUMBER, false, null, null)));
        var f = new HashMap<String, Object>(); f.put("n", "eight");
        assertThatThrownBy(() -> v.validate(f)).isInstanceOf(ApiException.class);
    }
    @Test void numberWithUnit_acceptsNumber() {
        var v = new TestValidator(List.of(new CustomFieldDefinition("d", "D", FieldType.NUMBER_WITH_UNIT, true, null, Unit.KM)));
        var f = new HashMap<String, Object>(); f.put("d", 45);
        assertThatCode(() -> v.validate(f)).doesNotThrowAnyException();
    }
    @Test void numberWithUnit_rejectsNonNumber() {
        var v = new TestValidator(List.of(new CustomFieldDefinition("d", "D", FieldType.NUMBER_WITH_UNIT, true, null, Unit.KM)));
        var f = new HashMap<String, Object>(); f.put("d", "far");
        assertThatThrownBy(() -> v.validate(f)).isInstanceOf(ApiException.class);
    }
    @Test void selectField_acceptsValidOption() {
        var v = new TestValidator(List.of(new CustomFieldDefinition("l", "L", FieldType.SELECT, true, List.of("A","B","C"), null)));
        var f = new HashMap<String, Object>(); f.put("l", "A");
        assertThatCode(() -> v.validate(f)).doesNotThrowAnyException();
    }
    @Test void selectField_rejectsInvalidOption() {
        var v = new TestValidator(List.of(new CustomFieldDefinition("l", "L", FieldType.SELECT, true, List.of("A","B","C"), null)));
        var f = new HashMap<String, Object>(); f.put("l", "Z");
        assertThatThrownBy(() -> v.validate(f)).isInstanceOf(ApiException.class);
    }
    @Test void booleanField_acceptsBoolean() {
        var v = new TestValidator(List.of(new CustomFieldDefinition("b", "B", FieldType.BOOLEAN, false, null, null)));
        var f = new HashMap<String, Object>(); f.put("b", true);
        assertThatCode(() -> v.validate(f)).doesNotThrowAnyException();
    }
    @Test void booleanField_rejectsNonBoolean() {
        var v = new TestValidator(List.of(new CustomFieldDefinition("b", "B", FieldType.BOOLEAN, false, null, null)));
        var f = new HashMap<String, Object>(); f.put("b", "yes");
        assertThatThrownBy(() -> v.validate(f)).isInstanceOf(ApiException.class);
    }
    @Test void timeOfDay_acceptsValidTime() {
        var v = new TestValidator(List.of(new CustomFieldDefinition("t", "T", FieldType.TIME_OF_DAY, true, null, null)));
        var f = new HashMap<String, Object>(); f.put("t", "09:00");
        assertThatCode(() -> v.validate(f)).doesNotThrowAnyException();
    }
    @Test void timeOfDay_rejectsInvalidTime() {
        var v = new TestValidator(List.of(new CustomFieldDefinition("t", "T", FieldType.TIME_OF_DAY, true, null, null)));
        var f = new HashMap<String, Object>(); f.put("t", "9am");
        assertThatThrownBy(() -> v.validate(f)).isInstanceOf(ApiException.class);
    }
    @Test void timeOfDay_rejectsTimeWithSeconds() {
        var v = new TestValidator(List.of(new CustomFieldDefinition("t", "T", FieldType.TIME_OF_DAY, true, null, null)));
        var f = new HashMap<String, Object>(); f.put("t", "09:00:30");
        assertThatThrownBy(() -> v.validate(f)).isInstanceOf(ApiException.class);
    }
    @Test void dateRange_acceptsValidRange() {
        var v = new TestValidator(List.of(new CustomFieldDefinition("d", "D", FieldType.DATE_RANGE, true, null, null)));
        var f = new HashMap<String, Object>(); f.put("d", Map.of("start", "2026-06-01", "end", "2026-06-03"));
        assertThatCode(() -> v.validate(f)).doesNotThrowAnyException();
    }
    @Test void dateRange_acceptsSameDay() {
        var v = new TestValidator(List.of(new CustomFieldDefinition("d", "D", FieldType.DATE_RANGE, true, null, null)));
        var f = new HashMap<String, Object>(); f.put("d", Map.of("start", "2026-06-01", "end", "2026-06-01"));
        assertThatCode(() -> v.validate(f)).doesNotThrowAnyException();
    }
    @Test void dateRange_rejectsEndBeforeStart() {
        var v = new TestValidator(List.of(new CustomFieldDefinition("d", "D", FieldType.DATE_RANGE, true, null, null)));
        var f = new HashMap<String, Object>(); f.put("d", Map.of("start", "2026-06-03", "end", "2026-06-01"));
        assertThatThrownBy(() -> v.validate(f)).isInstanceOf(ApiException.class);
    }
    @Test void dateRange_rejectsMissingEnd() {
        var v = new TestValidator(List.of(new CustomFieldDefinition("d", "D", FieldType.DATE_RANGE, true, null, null)));
        var f = new HashMap<String, Object>(); f.put("d", Map.of("start", "2026-06-01"));
        assertThatThrownBy(() -> v.validate(f)).isInstanceOf(ApiException.class);
    }
    @Test void rejectsUnknownFieldName() {
        var v = new TestValidator(List.of(new CustomFieldDefinition("n", "N", FieldType.TEXT, false, null, null)));
        var f = new HashMap<String, Object>(); f.put("unknown", "value");
        assertThatThrownBy(() -> v.validate(f)).isInstanceOf(ApiException.class);
    }
    @Test void rejectsMissingRequiredField() {
        var v = new TestValidator(List.of(new CustomFieldDefinition("n", "N", FieldType.TEXT, true, null, null)));
        assertThatThrownBy(() -> v.validate(new HashMap<>())).isInstanceOf(ApiException.class);
    }
    @Test void allowsMissingOptionalField() {
        var v = new TestValidator(List.of(new CustomFieldDefinition("n", "N", FieldType.TEXT, false, null, null)));
        assertThatCode(() -> v.validate(new HashMap<>())).doesNotThrowAnyException();
    }
    @Test void rejectsNullValueForRequiredField() {
        var v = new TestValidator(List.of(new CustomFieldDefinition("n", "N", FieldType.TEXT, true, null, null)));
        var f = new HashMap<String, Object>(); f.put("n", null);
        assertThatThrownBy(() -> v.validate(f)).isInstanceOf(ApiException.class);
    }
}
