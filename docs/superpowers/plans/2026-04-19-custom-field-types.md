# Custom Field Types Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the 4 generic field types (TEXT, NUMBER, SELECT, BOOLEAN) with 7 semantic field types including NUMBER_WITH_UNIT, TIME_OF_DAY, and DATE_RANGE, plus a curated Unit enum.

**Architecture:** Add `FieldType` and `Unit` enums in a new `model` package. Update `CustomFieldDefinition` record to use `FieldType` enum instead of `String`, add `Unit unit` field, and enforce invariants in compact constructor. Add a shared default helper method `validateFields(...)` to `ActivityFieldValidator` that performs generic validation for all field types. Concrete validators continue implementing `validate(...)` and delegate to `validateFields(...)`. Update each concrete validator to use new types. Update frontend `ActivityForm.vue` to render new types with a unit label lookup map.

**Tech Stack:** Java 21 records, Spring Boot, PostgreSQL JSONB, Vue 3, Tailwind CSS

**Important notes:**
- `ApiException` import path is `ch.junto.core.exception.ApiException` — verified in codebase
- `CustomFields.java` — only add `getDouble()`, preserve all existing methods
- Concrete validators — preserve any existing `normalize()` logic, only update field definitions and `validate()` delegation
- Java enums serialize as string names by default (e.g. `"KM"` not `{"name":"KM","label":"km"}`), so frontend must use a lookup map for unit labels
- `TIME_OF_DAY` validation uses `DateTimeFormatter.ofPattern("HH:mm")` to enforce strict format, not loose `LocalTime.parse()`

---

## File Structure

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `backend/src/main/java/ch/junto/activitytype/model/FieldType.java` | Enum: TEXT, NUMBER, NUMBER_WITH_UNIT, SELECT, BOOLEAN, TIME_OF_DAY, DATE_RANGE |
| Create | `backend/src/main/java/ch/junto/activitytype/model/Unit.java` | Enum with display labels: KM, MILES, METERS, MINUTES, HOURS, etc. |
| Modify | `backend/src/main/java/ch/junto/activitytype/dto/CustomFieldDefinition.java` | Change `type` from String to FieldType, add `Unit unit`, add compact constructor validation |
| Modify | `backend/src/main/java/ch/junto/activity/entity/CustomFields.java` | Add `getDouble()` helper (preserve existing methods) |
| Modify | `backend/src/main/java/ch/junto/activity/validation/ActivityFieldValidator.java` | Add `validateFields(...)` default helper method for generic validation |
| Modify | `backend/src/main/java/ch/junto/activity/validation/ClimbingFieldValidator.java` | Update to use new FieldType enum |
| Modify | `backend/src/main/java/ch/junto/activity/validation/CyclingFieldValidator.java` | Add field definitions with NUMBER_WITH_UNIT |
| Modify | `backend/src/main/java/ch/junto/activity/validation/RunningFieldValidator.java` | Add field definitions with NUMBER_WITH_UNIT |
| Modify | `backend/src/main/java/ch/junto/activity/validation/SkiingFieldValidator.java` | Add field definitions with NUMBER_WITH_UNIT |
| Modify | `backend/src/main/java/ch/junto/activity/validation/YogaFieldValidator.java` | Add field definitions |
| Modify | `frontend/src/components/activity/ActivityForm.vue` | Add renderers for NUMBER_WITH_UNIT, TIME_OF_DAY, DATE_RANGE; add unit label map |
| Create | `backend/src/test/java/ch/junto/activitytype/model/FieldTypeTest.java` | Verify enum values |
| Create | `backend/src/test/java/ch/junto/activitytype/model/UnitTest.java` | Verify enum labels |
| Create | `backend/src/test/java/ch/junto/activitytype/dto/CustomFieldDefinitionTest.java` | Test compact constructor validation |
| Create | `backend/src/test/java/ch/junto/activity/validation/GenericFieldValidatorTest.java` | Test validateFields logic for all field types |
| Modify | `backend/src/test/java/ch/junto/activity/validation/ClimbingFieldValidatorTest.java` | Update for new constructor signature |

---

## Phase 1: Enums and Data Model

### Task 1: Create FieldType enum

**Files:**
- Create: `backend/src/main/java/ch/junto/activitytype/model/FieldType.java`
- Test: `backend/src/test/java/ch/junto/activitytype/model/FieldTypeTest.java`

- [ ] **Step 1: Write the failing test**

```java
package ch.junto.activitytype.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FieldTypeTest {

    @Test
    void hasAllExpectedValues() {
        FieldType[] values = FieldType.values();
        assertThat(values).containsExactlyInAnyOrder(
            FieldType.TEXT,
            FieldType.NUMBER,
            FieldType.NUMBER_WITH_UNIT,
            FieldType.SELECT,
            FieldType.BOOLEAN,
            FieldType.TIME_OF_DAY,
            FieldType.DATE_RANGE
        );
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "ch.junto.activitytype.model.FieldTypeTest" -i 2>&1 | tail -5`
Expected: FAIL — class not found

- [ ] **Step 3: Write minimal implementation**

```java
package ch.junto.activitytype.model;

public enum FieldType {
    TEXT,
    NUMBER,
    NUMBER_WITH_UNIT,
    SELECT,
    BOOLEAN,
    TIME_OF_DAY,
    DATE_RANGE
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "ch.junto.activitytype.model.FieldTypeTest" -i 2>&1 | tail -5`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/ch/junto/activitytype/model/FieldType.java backend/src/test/java/ch/junto/activitytype/model/FieldTypeTest.java
git commit -m "feat(fields): add FieldType enum with 7 semantic types"
```

---

### Task 2: Create Unit enum

**Files:**
- Create: `backend/src/main/java/ch/junto/activitytype/model/Unit.java`
- Test: `backend/src/test/java/ch/junto/activitytype/model/UnitTest.java`

- [ ] **Step 1: Write the failing test**

```java
package ch.junto.activitytype.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class UnitTest {

    @Test
    void hasAllExpectedValues() {
        Unit[] values = Unit.values();
        assertThat(values).containsExactlyInAnyOrder(
            Unit.KM, Unit.MILES, Unit.METERS,
            Unit.MINUTES, Unit.HOURS,
            Unit.LAPS, Unit.SETS, Unit.REPS,
            Unit.KG, Unit.LBS
        );
    }

    @Test
    void displayLabels() {
        assertThat(Unit.KM.getLabel()).isEqualTo("km");
        assertThat(Unit.MILES.getLabel()).isEqualTo("miles");
        assertThat(Unit.METERS.getLabel()).isEqualTo("m");
        assertThat(Unit.MINUTES.getLabel()).isEqualTo("minutes");
        assertThat(Unit.HOURS.getLabel()).isEqualTo("hours");
        assertThat(Unit.LAPS.getLabel()).isEqualTo("laps");
        assertThat(Unit.SETS.getLabel()).isEqualTo("sets");
        assertThat(Unit.REPS.getLabel()).isEqualTo("reps");
        assertThat(Unit.KG.getLabel()).isEqualTo("kg");
        assertThat(Unit.LBS.getLabel()).isEqualTo("lbs");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "ch.junto.activitytype.model.UnitTest" -i 2>&1 | tail -5`
Expected: FAIL — class not found

- [ ] **Step 3: Write minimal implementation**

```java
package ch.junto.activitytype.model;

public enum Unit {
    KM("km"),
    MILES("miles"),
    METERS("m"),
    MINUTES("minutes"),
    HOURS("hours"),
    LAPS("laps"),
    SETS("sets"),
    REPS("reps"),
    KG("kg"),
    LBS("lbs");

    private final String label;

    Unit(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "ch.junto.activitytype.model.UnitTest" -i 2>&1 | tail -5`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/ch/junto/activitytype/model/Unit.java backend/src/test/java/ch/junto/activitytype/model/UnitTest.java
git commit -m "feat(fields): add Unit enum with display labels"
```

---

### Task 3: Update CustomFieldDefinition record

**Files:**
- Modify: `backend/src/main/java/ch/junto/activitytype/dto/CustomFieldDefinition.java`
- Create: `backend/src/test/java/ch/junto/activitytype/dto/CustomFieldDefinitionTest.java`

- [ ] **Step 1: Write the failing tests**

```java
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
            List.of("BEGINNER", "INTERMEDIATE", "ADVANCED"), null
        );
        assertThat(def.name()).isEqualTo("difficulty");
        assertThat(def.options()).containsExactly("BEGINNER", "INTERMEDIATE", "ADVANCED");
        assertThat(def.unit()).isNull();
    }

    @Test
    void createsValidNumberWithUnitField() {
        CustomFieldDefinition def = new CustomFieldDefinition(
            "distance", "Distance", FieldType.NUMBER_WITH_UNIT, true,
            null, Unit.KM
        );
        assertThat(def.unit()).isEqualTo(Unit.KM);
        assertThat(def.options()).isEmpty();
    }

    @Test
    void createsValidTextField() {
        CustomFieldDefinition def = new CustomFieldDefinition(
            "notes", "Notes", FieldType.TEXT, false, null, null
        );
        assertThat(def.options()).isEmpty();
        assertThat(def.unit()).isNull();
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> new CustomFieldDefinition(
            "", "Label", FieldType.TEXT, false, null, null
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessage("name is required");
    }

    @Test
    void rejectsNullName() {
        assertThatThrownBy(() -> new CustomFieldDefinition(
            null, "Label", FieldType.TEXT, false, null, null
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessage("name is required");
    }

    @Test
    void rejectsBlankLabel() {
        assertThatThrownBy(() -> new CustomFieldDefinition(
            "name", "  ", FieldType.TEXT, false, null, null
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessage("label is required");
    }

    @Test
    void rejectsSelectWithoutOptions() {
        assertThatThrownBy(() -> new CustomFieldDefinition(
            "style", "Style", FieldType.SELECT, true, List.of(), null
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessage("SELECT fields must define options");
    }

    @Test
    void rejectsNonSelectWithOptions() {
        assertThatThrownBy(() -> new CustomFieldDefinition(
            "count", "Count", FieldType.NUMBER, false,
            List.of("A", "B"), null
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Only SELECT fields may define options");
    }

    @Test
    void rejectsNumberWithUnitWithoutUnit() {
        assertThatThrownBy(() -> new CustomFieldDefinition(
            "distance", "Distance", FieldType.NUMBER_WITH_UNIT, true,
            null, null
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessage("NUMBER_WITH_UNIT fields must define a unit");
    }

    @Test
    void rejectsNonNumberWithUnitWithUnit() {
        assertThatThrownBy(() -> new CustomFieldDefinition(
            "count", "Count", FieldType.NUMBER, false, null, Unit.KM
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Only NUMBER_WITH_UNIT fields may define a unit");
    }

    @Test
    void normalizesNullOptionsToEmptyList() {
        CustomFieldDefinition def = new CustomFieldDefinition(
            "notes", "Notes", FieldType.TEXT, false, null, null
        );
        assertThat(def.options()).isEmpty();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./gradlew test --tests "ch.junto.activitytype.dto.CustomFieldDefinitionTest" -i 2>&1 | tail -10`
Expected: FAIL — `CustomFieldDefinition` constructor doesn't accept `FieldType` or `Unit` yet

- [ ] **Step 3: Write minimal implementation**

Replace the full contents of `backend/src/main/java/ch/junto/activitytype/dto/CustomFieldDefinition.java` with:

```java
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "ch.junto.activitytype.dto.CustomFieldDefinitionTest" -i 2>&1 | tail -5`
Expected: PASS (all 11 tests)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/ch/junto/activitytype/dto/CustomFieldDefinition.java backend/src/test/java/ch/junto/activitytype/dto/CustomFieldDefinitionTest.java
git commit -m "feat(fields): update CustomFieldDefinition with FieldType enum, Unit, and compact constructor validation"
```

---

## Phase 2: Validation and Validators

### Task 4: Add getDouble to CustomFields and add validateFields helper to ActivityFieldValidator

**Files:**
- Modify: `backend/src/main/java/ch/junto/activity/entity/CustomFields.java`
- Modify: `backend/src/main/java/ch/junto/activity/validation/ActivityFieldValidator.java`
- Create: `backend/src/test/java/ch/junto/activity/validation/GenericFieldValidatorTest.java`

**Key design:** `validate(...)` remains the abstract interface method that each concrete validator implements. `validateFields(...)` is a new default helper method on the interface that performs generic type-based validation. Concrete validators delegate by calling `validateFields(customFields, getFieldDefinitions())` from their `validate()` method.

- [ ] **Step 1: Write the failing tests**

```java
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

        TestValidator(List<CustomFieldDefinition> defs) {
            this.defs = defs;
        }

        @Override
        public String getActivityTypeSlug() { return "test"; }

        @Override
        public void validate(Map<String, Object> customFields) {
            validateFields(customFields, defs);
        }

        @Override
        public List<CustomFieldDefinition> getFieldDefinitions() { return defs; }
    }

    @Test
    void validate_textField_acceptsString() {
        TestValidator v = new TestValidator(List.of(
            new CustomFieldDefinition("notes", "Notes", FieldType.TEXT, false, null, null)
        ));
        Map<String, Object> fields = new HashMap<>();
        fields.put("notes", "hello");
        assertThatCode(() -> v.validate(fields)).doesNotThrowAnyException();
    }

    @Test
    void validate_textField_rejectsNonString() {
        TestValidator v = new TestValidator(List.of(
            new CustomFieldDefinition("notes", "Notes", FieldType.TEXT, false, null, null)
        ));
        Map<String, Object> fields = new HashMap<>();
        fields.put("notes", 42);
        assertThatThrownBy(() -> v.validate(fields))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void validate_requiredTextField_rejectsBlank() {
        TestValidator v = new TestValidator(List.of(
            new CustomFieldDefinition("notes", "Notes", FieldType.TEXT, true, null, null)
        ));
        Map<String, Object> fields = new HashMap<>();
        fields.put("notes", "  ");
        assertThatThrownBy(() -> v.validate(fields))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void validate_numberField_acceptsNumber() {
        TestValidator v = new TestValidator(List.of(
            new CustomFieldDefinition("count", "Count", FieldType.NUMBER, false, null, null)
        ));
        Map<String, Object> fields = new HashMap<>();
        fields.put("count", 8);
        assertThatCode(() -> v.validate(fields)).doesNotThrowAnyException();
    }

    @Test
    void validate_numberField_acceptsDecimal() {
        TestValidator v = new TestValidator(List.of(
            new CustomFieldDefinition("count", "Count", FieldType.NUMBER, false, null, null)
        ));
        Map<String, Object> fields = new HashMap<>();
        fields.put("count", 2.5);
        assertThatCode(() -> v.validate(fields)).doesNotThrowAnyException();
    }

    @Test
    void validate_numberField_rejectsNonNumber() {
        TestValidator v = new TestValidator(List.of(
            new CustomFieldDefinition("count", "Count", FieldType.NUMBER, false, null, null)
        ));
        Map<String, Object> fields = new HashMap<>();
        fields.put("count", "eight");
        assertThatThrownBy(() -> v.validate(fields))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void validate_numberWithUnit_acceptsNumber() {
        TestValidator v = new TestValidator(List.of(
            new CustomFieldDefinition("distance", "Distance", FieldType.NUMBER_WITH_UNIT, true, null, Unit.KM)
        ));
        Map<String, Object> fields = new HashMap<>();
        fields.put("distance", 45);
        assertThatCode(() -> v.validate(fields)).doesNotThrowAnyException();
    }

    @Test
    void validate_numberWithUnit_rejectsNonNumber() {
        TestValidator v = new TestValidator(List.of(
            new CustomFieldDefinition("distance", "Distance", FieldType.NUMBER_WITH_UNIT, true, null, Unit.KM)
        ));
        Map<String, Object> fields = new HashMap<>();
        fields.put("distance", "far");
        assertThatThrownBy(() -> v.validate(fields))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void validate_selectField_acceptsValidOption() {
        TestValidator v = new TestValidator(List.of(
            new CustomFieldDefinition("level", "Level", FieldType.SELECT, true,
                List.of("BEGINNER", "INTERMEDIATE", "ADVANCED"), null)
        ));
        Map<String, Object> fields = new HashMap<>();
        fields.put("level", "BEGINNER");
        assertThatCode(() -> v.validate(fields)).doesNotThrowAnyException();
    }

    @Test
    void validate_selectField_rejectsInvalidOption() {
        TestValidator v = new TestValidator(List.of(
            new CustomFieldDefinition("level", "Level", FieldType.SELECT, true,
                List.of("BEGINNER", "INTERMEDIATE", "ADVANCED"), null)
        ));
        Map<String, Object> fields = new HashMap<>();
        fields.put("level", "EXPERT");
        assertThatThrownBy(() -> v.validate(fields))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void validate_booleanField_acceptsBoolean() {
        TestValidator v = new TestValidator(List.of(
            new CustomFieldDefinition("gear", "Gear", FieldType.BOOLEAN, false, null, null)
        ));
        Map<String, Object> fields = new HashMap<>();
        fields.put("gear", true);
        assertThatCode(() -> v.validate(fields)).doesNotThrowAnyException();
    }

    @Test
    void validate_booleanField_rejectsNonBoolean() {
        TestValidator v = new TestValidator(List.of(
            new CustomFieldDefinition("gear", "Gear", FieldType.BOOLEAN, false, null, null)
        ));
        Map<String, Object> fields = new HashMap<>();
        fields.put("gear", "yes");
        assertThatThrownBy(() -> v.validate(fields))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void validate_timeOfDay_acceptsValidTime() {
        TestValidator v = new TestValidator(List.of(
            new CustomFieldDefinition("meetTime", "Meet Time", FieldType.TIME_OF_DAY, true, null, null)
        ));
        Map<String, Object> fields = new HashMap<>();
        fields.put("meetTime", "09:00");
        assertThatCode(() -> v.validate(fields)).doesNotThrowAnyException();
    }

    @Test
    void validate_timeOfDay_rejectsInvalidTime() {
        TestValidator v = new TestValidator(List.of(
            new CustomFieldDefinition("meetTime", "Meet Time", FieldType.TIME_OF_DAY, true, null, null)
        ));
        Map<String, Object> fields = new HashMap<>();
        fields.put("meetTime", "9am");
        assertThatThrownBy(() -> v.validate(fields))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void validate_timeOfDay_rejectsTimeWithSeconds() {
        TestValidator v = new TestValidator(List.of(
            new CustomFieldDefinition("meetTime", "Meet Time", FieldType.TIME_OF_DAY, true, null, null)
        ));
        Map<String, Object> fields = new HashMap<>();
        fields.put("meetTime", "09:00:30");
        assertThatThrownBy(() -> v.validate(fields))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void validate_dateRange_acceptsValidRange() {
        TestValidator v = new TestValidator(List.of(
            new CustomFieldDefinition("tripDates", "Trip Dates", FieldType.DATE_RANGE, true, null, null)
        ));
        Map<String, Object> fields = new HashMap<>();
        fields.put("tripDates", Map.of("start", "2026-06-01", "end", "2026-06-03"));
        assertThatCode(() -> v.validate(fields)).doesNotThrowAnyException();
    }

    @Test
    void validate_dateRange_acceptsSameDay() {
        TestValidator v = new TestValidator(List.of(
            new CustomFieldDefinition("tripDates", "Trip Dates", FieldType.DATE_RANGE, true, null, null)
        ));
        Map<String, Object> fields = new HashMap<>();
        fields.put("tripDates", Map.of("start", "2026-06-01", "end", "2026-06-01"));
        assertThatCode(() -> v.validate(fields)).doesNotThrowAnyException();
    }

    @Test
    void validate_dateRange_rejectsEndBeforeStart() {
        TestValidator v = new TestValidator(List.of(
            new CustomFieldDefinition("tripDates", "Trip Dates", FieldType.DATE_RANGE, true, null, null)
        ));
        Map<String, Object> fields = new HashMap<>();
        fields.put("tripDates", Map.of("start", "2026-06-03", "end", "2026-06-01"));
        assertThatThrownBy(() -> v.validate(fields))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void validate_dateRange_rejectsMissingEnd() {
        TestValidator v = new TestValidator(List.of(
            new CustomFieldDefinition("tripDates", "Trip Dates", FieldType.DATE_RANGE, true, null, null)
        ));
        Map<String, Object> fields = new HashMap<>();
        fields.put("tripDates", Map.of("start", "2026-06-01"));
        assertThatThrownBy(() -> v.validate(fields))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void validate_rejectsUnknownFieldName() {
        TestValidator v = new TestValidator(List.of(
            new CustomFieldDefinition("notes", "Notes", FieldType.TEXT, false, null, null)
        ));
        Map<String, Object> fields = new HashMap<>();
        fields.put("unknown", "value");
        assertThatThrownBy(() -> v.validate(fields))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void validate_rejectsMissingRequiredField() {
        TestValidator v = new TestValidator(List.of(
            new CustomFieldDefinition("notes", "Notes", FieldType.TEXT, true, null, null)
        ));
        Map<String, Object> fields = new HashMap<>();
        assertThatThrownBy(() -> v.validate(fields))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void validate_allowsMissingOptionalField() {
        TestValidator v = new TestValidator(List.of(
            new CustomFieldDefinition("notes", "Notes", FieldType.TEXT, false, null, null)
        ));
        Map<String, Object> fields = new HashMap<>();
        assertThatCode(() -> v.validate(fields)).doesNotThrowAnyException();
    }

    @Test
    void validate_rejectsNullValueForRequiredField() {
        TestValidator v = new TestValidator(List.of(
            new CustomFieldDefinition("notes", "Notes", FieldType.TEXT, true, null, null)
        ));
        Map<String, Object> fields = new HashMap<>();
        fields.put("notes", null);
        assertThatThrownBy(() -> v.validate(fields))
            .isInstanceOf(ApiException.class);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./gradlew test --tests "ch.junto.activity.validation.GenericFieldValidatorTest" -i 2>&1 | tail -10`
Expected: FAIL — `validateFields` method does not exist on `ActivityFieldValidator` yet

- [ ] **Step 3: Write minimal implementation**

First, add only `getDouble()` to `CustomFields.java` — do NOT remove or change existing methods. Add this method after the existing `getInt()` method at line 16:

```java
    public Double getDouble(String key) {
        Object val = get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        return null;
    }
```

Then, update `ActivityFieldValidator.java`. Replace the full contents with:

```java
package ch.junto.activity.validation;

import ch.junto.activity.entity.Activity;
import ch.junto.activitytype.dto.CustomFieldDefinition;
import ch.junto.activitytype.model.FieldType;
import ch.junto.core.exception.ApiException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public interface ActivityFieldValidator {
    String getActivityTypeSlug();
    void validate(Map<String, Object> customFields);

    default void normalize(Activity activity) {
        // Optional hook to populate computed fields based on customFields
    }

    default List<CustomFieldDefinition> getFieldDefinitions() {
        return java.util.Collections.emptyList();
    }

    /**
     * Shared generic validation of submitted custom field values against field definitions.
     * Concrete validators should call this from their validate() method:
     *   validateFields(customFields, getFieldDefinitions());
     */
    default void validateFields(Map<String, Object> customFields, List<CustomFieldDefinition> definitions) {
        if (customFields == null) {
            throw new ApiException("VALIDATION_ERROR", "Custom fields are required", 400);
        }

        Set<String> allowedNames = definitions.stream()
            .map(CustomFieldDefinition::name)
            .collect(Collectors.toSet());

        // Reject unknown field names
        for (String key : customFields.keySet()) {
            if (!allowedNames.contains(key)) {
                throw new ApiException("VALIDATION_ERROR", "Unknown field: " + key, 400);
            }
        }

        // Validate each defined field
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
                case NUMBER -> validateNumberField(def.name(), value);
                case NUMBER_WITH_UNIT -> validateNumberField(def.name(), value);
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "ch.junto.activity.validation.GenericFieldValidatorTest" -i 2>&1 | tail -5`
Expected: PASS (all 23 tests)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/ch/junto/activity/entity/CustomFields.java backend/src/main/java/ch/junto/activity/validation/ActivityFieldValidator.java backend/src/test/java/ch/junto/activity/validation/GenericFieldValidatorTest.java
git commit -m "feat(fields): add validateFields helper and getDouble to CustomFields"
```

---

### Task 5: Update ClimbingFieldValidator

**Files:**
- Modify: `backend/src/main/java/ch/junto/activity/validation/ClimbingFieldValidator.java`
- Modify: `backend/src/test/java/ch/junto/activity/validation/ClimbingFieldValidatorTest.java`

**Important:** Preserve the existing `normalize()` method which populates `subtype` and `difficultyLabel`.

- [ ] **Step 1: Update the implementation**

Replace the full contents of `ClimbingFieldValidator.java` with:

```java
package ch.junto.activity.validation;

import ch.junto.activity.entity.Activity;
import ch.junto.activitytype.dto.CustomFieldDefinition;
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
    public String getActivityTypeSlug() {
        return "climbing";
    }

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
```

- [ ] **Step 2: Update the test**

Replace the full contents of `ClimbingFieldValidatorTest.java` with:

```java
package ch.junto.activity.validation;

import ch.junto.activity.entity.Activity;
import ch.junto.activity.entity.CustomFields;
import ch.junto.activitytype.model.FieldType;
import ch.junto.core.exception.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClimbingFieldValidatorTest {

    private final ClimbingFieldValidator validator = new ClimbingFieldValidator();

    @Test
    void normalize_populatesSubtypeAndDifficulty() {
        Activity activity = new Activity();
        CustomFields fields = new CustomFields();
        fields.put("style", "Sport");
        fields.put("grade", "5.10a");
        fields.put("environment", "Outdoor");
        fields.put("experienceLevel", "Intermediate");
        activity.setCustomFields(fields);

        validator.normalize(activity);

        assertThat(activity.getSubtype()).isEqualTo("Sport");
        assertThat(activity.getDifficultyLabel()).isEqualTo("5.10a");
    }

    @Test
    void validate_validFields_passes() {
        CustomFields fields = new CustomFields();
        fields.put("style", "Bouldering");
        fields.put("grade", "V5");
        fields.put("environment", "Indoor");
        fields.put("experienceLevel", "Beginner");
        fields.put("needsEquipment", true);

        assertThatCode(() -> validator.validate(fields)).doesNotThrowAnyException();
    }

    @Test
    void validate_missingFields_throwsException() {
        CustomFields fields = new CustomFields();

        assertThatThrownBy(() -> validator.validate(fields))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void validate_invalidStyle_throwsException() {
        CustomFields fields = new CustomFields();
        fields.put("style", "InvalidStyle");
        fields.put("environment", "Outdoor");
        fields.put("experienceLevel", "Intermediate");

        assertThatThrownBy(() -> validator.validate(fields))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void getFieldDefinitions_returnsCorrectTypes() {
        var defs = validator.getFieldDefinitions();
        assertThat(defs).hasSize(5);
        assertThat(defs.get(0).type()).isEqualTo(FieldType.SELECT);
        assertThat(defs.get(1).type()).isEqualTo(FieldType.TEXT);
        assertThat(defs.get(4).type()).isEqualTo(FieldType.BOOLEAN);
    }
}
```

- [ ] **Step 3: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "ch.junto.activity.validation.ClimbingFieldValidatorTest" -i 2>&1 | tail -5`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/ch/junto/activity/validation/ClimbingFieldValidator.java backend/src/test/java/ch/junto/activity/validation/ClimbingFieldValidatorTest.java
git commit -m "refactor(fields): update ClimbingFieldValidator to use FieldType enum and generic validation"
```

---

### Task 6: Update remaining validators (Cycling, Running, Skiing, Yoga)

**Files:**
- Modify: `backend/src/main/java/ch/junto/activity/validation/CyclingFieldValidator.java`
- Modify: `backend/src/main/java/ch/junto/activity/validation/RunningFieldValidator.java`
- Modify: `backend/src/main/java/ch/junto/activity/validation/SkiingFieldValidator.java`
- Modify: `backend/src/main/java/ch/junto/activity/validation/YogaFieldValidator.java`

**Important:** These validators currently have stub `validate()` methods with comments like `// e.g. require 'distance_km'`. No existing `normalize()` or custom logic to preserve — safe to replace full contents.

- [ ] **Step 1: Update CyclingFieldValidator**

Replace the full contents with:

```java
package ch.junto.activity.validation;

import ch.junto.activitytype.dto.CustomFieldDefinition;
import ch.junto.activitytype.model.FieldType;
import ch.junto.activitytype.model.Unit;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class CyclingFieldValidator implements ActivityFieldValidator {

    private static final List<String> DIFFICULTIES = List.of("Beginner", "Intermediate", "Advanced");

    @Override
    public String getActivityTypeSlug() {
        return "cycling";
    }

    @Override
    public List<CustomFieldDefinition> getFieldDefinitions() {
        return List.of(
            new CustomFieldDefinition("distance", "Distance", FieldType.NUMBER_WITH_UNIT, true, null, Unit.KM),
            new CustomFieldDefinition("duration", "Duration", FieldType.NUMBER_WITH_UNIT, false, null, Unit.HOURS),
            new CustomFieldDefinition("difficulty", "Difficulty", FieldType.SELECT, true, DIFFICULTIES, null)
        );
    }

    @Override
    public void validate(Map<String, Object> customFields) {
        validateFields(customFields, getFieldDefinitions());
    }
}
```

- [ ] **Step 2: Update RunningFieldValidator**

Replace the full contents with:

```java
package ch.junto.activity.validation;

import ch.junto.activitytype.dto.CustomFieldDefinition;
import ch.junto.activitytype.model.FieldType;
import ch.junto.activitytype.model.Unit;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class RunningFieldValidator implements ActivityFieldValidator {

    private static final List<String> SURFACES = List.of("Road", "Trail", "Track", "Treadmill");
    private static final List<String> DIFFICULTIES = List.of("Beginner", "Intermediate", "Advanced");

    @Override
    public String getActivityTypeSlug() {
        return "running";
    }

    @Override
    public List<CustomFieldDefinition> getFieldDefinitions() {
        return List.of(
            new CustomFieldDefinition("distance", "Distance", FieldType.NUMBER_WITH_UNIT, true, null, Unit.KM),
            new CustomFieldDefinition("surface", "Surface", FieldType.SELECT, true, SURFACES, null),
            new CustomFieldDefinition("difficulty", "Difficulty", FieldType.SELECT, true, DIFFICULTIES, null)
        );
    }

    @Override
    public void validate(Map<String, Object> customFields) {
        validateFields(customFields, getFieldDefinitions());
    }
}
```

- [ ] **Step 3: Update SkiingFieldValidator**

Replace the full contents with:

```java
package ch.junto.activity.validation;

import ch.junto.activitytype.dto.CustomFieldDefinition;
import ch.junto.activitytype.model.FieldType;
import ch.junto.activitytype.model.Unit;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SkiingFieldValidator implements ActivityFieldValidator {

    private static final List<String> LEVELS = List.of("Beginner", "Intermediate", "Advanced", "Expert");

    @Override
    public String getActivityTypeSlug() {
        return "skiing";
    }

    @Override
    public List<CustomFieldDefinition> getFieldDefinitions() {
        return List.of(
            new CustomFieldDefinition("level", "Level", FieldType.SELECT, true, LEVELS, null),
            new CustomFieldDefinition("elevationGain", "Elevation Gain", FieldType.NUMBER_WITH_UNIT, false, null, Unit.METERS)
        );
    }

    @Override
    public void validate(Map<String, Object> customFields) {
        validateFields(customFields, getFieldDefinitions());
    }
}
```

- [ ] **Step 4: Update YogaFieldValidator**

Replace the full contents with:

```java
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

    @Override
    public String getActivityTypeSlug() {
        return "yoga";
    }

    @Override
    public List<CustomFieldDefinition> getFieldDefinitions() {
        return List.of(
            new CustomFieldDefinition("style", "Style", FieldType.SELECT, true, STYLES, null),
            new CustomFieldDefinition("level", "Level", FieldType.SELECT, true, LEVELS, null),
            new CustomFieldDefinition("bringMat", "Bring your own mat", FieldType.BOOLEAN, false, null, null)
        );
    }

    @Override
    public void validate(Map<String, Object> customFields) {
        validateFields(customFields, getFieldDefinitions());
    }
}
```

- [ ] **Step 5: Run all tests**

Run: `cd backend && ./gradlew test -i 2>&1 | tail -10`
Expected: ALL PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/ch/junto/activity/validation/CyclingFieldValidator.java backend/src/main/java/ch/junto/activity/validation/RunningFieldValidator.java backend/src/main/java/ch/junto/activity/validation/SkiingFieldValidator.java backend/src/main/java/ch/junto/activity/validation/YogaFieldValidator.java
git commit -m "feat(fields): update all validators with field definitions using new types and units"
```

---

## Phase 3: Frontend

### Task 7: Update frontend ActivityForm.vue

**Files:**
- Modify: `frontend/src/components/activity/ActivityForm.vue`

**Important frontend notes:**
- Java enums serialize as plain strings (e.g. `"KM"` not `{"name":"KM","label":"km"}`). Use a local `unitLabels` lookup map for display.
- DATE_RANGE fields must be initialized as `{ start: '', end: '' }` in the watcher BEFORE the template renders, not on `@input`.
- SELECT placeholder should not use `selected` attribute — `v-model` controls selection.

- [ ] **Step 1: Update the script section**

Replace the entire `<script setup lang="ts">` block with:

```typescript
<script setup lang="ts">
import { ref, computed, watch } from 'vue'

const props = defineProps<{
  types: any[]
  loading?: boolean
}>()

const emit = defineEmits(['submit'])

const unitLabels: Record<string, string> = {
  KM: 'km',
  MILES: 'miles',
  METERS: 'm',
  MINUTES: 'minutes',
  HOURS: 'hours',
  LAPS: 'laps',
  SETS: 'sets',
  REPS: 'reps',
  KG: 'kg',
  LBS: 'lbs'
}

const form = ref({
  title: '',
  description: '',
  activityTypeId: props.types[0]?.id || '',
  city: '',
  startsAt: '',
  signupDeadline: '',
  maxParticipants: 10,
  joinPolicy: 'OPEN',
  customFields: {} as Record<string, any>
})

const selectedType = computed(() => {
  return props.types.find(t => t.id === form.value.activityTypeId)
})

watch(() => form.value.activityTypeId, () => {
  form.value.customFields = {}
  // Initialize DATE_RANGE fields with empty objects before template renders
  const selected = props.types.find(t => t.id === form.value.activityTypeId)
  selected?.customFields?.forEach((field: any) => {
    if (field.type === 'DATE_RANGE') {
      form.value.customFields[field.name] = { start: '', end: '' }
    }
  })
})
</script>
```

- [ ] **Step 2: Update the template custom fields section**

Replace lines 121-161 (the entire `<template v-if="field.type === 'SELECT'">` through `</template>` for the else case) with:

```html
            <template v-if="field.type === 'SELECT'">
              <select
                v-model="form.customFields[field.name]"
                :required="field.required"
                class="w-full bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-sm focus:ring-2 focus:ring-primary-500 outline-none transition-all"
              >
                <option disabled value="">Select {{ field.label }}</option>
                <option v-for="opt in field.options" :key="opt" :value="opt">{{ opt }}</option>
              </select>
            </template>

            <template v-else-if="field.type === 'NUMBER'">
              <input
                v-model.number="form.customFields[field.name]"
                type="number"
                :required="field.required"
                class="w-full bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-sm focus:ring-2 focus:ring-primary-500 outline-none transition-all"
              />
            </template>

            <template v-else-if="field.type === 'NUMBER_WITH_UNIT'">
              <div class="flex items-center gap-2">
                <input
                  v-model.number="form.customFields[field.name]"
                  type="number"
                  step="any"
                  :required="field.required"
                  class="flex-1 bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-sm focus:ring-2 focus:ring-primary-500 outline-none transition-all"
                />
                <span class="text-sm font-medium text-slate-500 min-w-fit">{{ unitLabels[field.unit] }}</span>
              </div>
            </template>

            <template v-else-if="field.type === 'BOOLEAN'">
              <label class="flex items-center space-x-3 cursor-pointer">
                <input
                  v-model="form.customFields[field.name]"
                  type="checkbox"
                  :required="field.required"
                  class="w-5 h-5 rounded border-slate-300 text-primary-600 focus:ring-primary-500"
                />
                <span class="text-sm text-slate-700">Yes</span>
              </label>
            </template>

            <template v-else-if="field.type === 'TIME_OF_DAY'">
              <input
                v-model="form.customFields[field.name]"
                type="time"
                :required="field.required"
                class="w-full bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-sm focus:ring-2 focus:ring-primary-500 outline-none transition-all"
              />
            </template>

            <template v-else-if="field.type === 'DATE_RANGE'">
              <div class="grid grid-cols-2 gap-2">
                <input
                  v-model="form.customFields[field.name].start"
                  type="date"
                  :required="field.required"
                  class="bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-sm focus:ring-2 focus:ring-primary-500 outline-none transition-all"
                />
                <input
                  v-model="form.customFields[field.name].end"
                  type="date"
                  :required="field.required"
                  class="bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-sm focus:ring-2 focus:ring-primary-500 outline-none transition-all"
                />
              </div>
            </template>

            <template v-else>
              <input
                v-model="form.customFields[field.name]"
                type="text"
                :required="field.required"
                class="w-full bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-sm focus:ring-2 focus:ring-primary-500 outline-none transition-all"
              />
            </template>
```

- [ ] **Step 3: Verify frontend builds**

Run: `cd frontend && npm run build 2>&1 | tail -5`
Expected: Build succeeds

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/activity/ActivityForm.vue
git commit -m "feat(fields): add frontend renderers for NUMBER_WITH_UNIT, TIME_OF_DAY, and DATE_RANGE"
```

---

### Task 8: Run full test suite and verify

- [ ] **Step 1: Run all backend tests**

Run: `cd backend && ./gradlew test -i 2>&1 | tail -20`
Expected: ALL PASS

- [ ] **Step 2: Run frontend build**

Run: `cd frontend && npm run build 2>&1 | tail -5`
Expected: Build succeeds

- [ ] **Step 3: Final commit (if any fixes were needed)**

Only commit if fixes were required. Otherwise skip this step.
