# Activity & ActivityType Rework Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the fragile ActivityType DB entity + per-sport validator architecture with a code-defined registry and single generic validation service.

**Architecture:** Activity types become `@Component` beans implementing `ActivityTypeDefinition`, collected into an `ActivityTypeRegistry` with strict startup validation. A single `ActivityFieldValidationService` replaces five per-sport validators. The `activity_types` table is dropped; Activity stores an immutable `typeSlug` string. List responses include pre-formatted summary field values.

**Tech Stack:** Java 21, Spring Boot 3, JPA/Hibernate, PostgreSQL, Flyway, SpringDoc OpenAPI

**Spec:** `docs/superpowers/specs/2026-04-24-activity-activitytype-rework-design.md`

---

## File Map

### New files

All paths relative to `backend/src/main/java/ch/junto/` unless noted.

| File | Responsibility |
|------|----------------|
| `activitytype/definition/ActivityTypeDefinition.java` | Interface for type definitions |
| `activitytype/definition/CustomFieldDefinition.java` | Record (moved from `activitytype/dto/`) |
| `activitytype/ActivityTypeRegistry.java` | Collects definitions, strict startup validation |
| `activitytype/definition/ClimbingType.java` | Climbing metadata + field definitions |
| `activitytype/definition/CyclingType.java` | Cycling metadata + field definitions |
| `activitytype/definition/RunningType.java` | Running metadata + field definitions |
| `activitytype/definition/SkiingType.java` | Skiing metadata + field definitions |
| `activitytype/definition/YogaType.java` | Yoga metadata + field definitions |
| `activity/ActivityFieldValidationService.java` | Generic validation for all types |
| `activity/dto/ActivitySummaryField.java` | Pre-formatted summary field for list responses |
| `resources/db/migration/V015__rework_activity_types.sql` | Migration |
| `test: activitytype/ActivityTypeRegistryTest.java` | Registry + per-type + startup validation tests |
| `test: activity/ActivityFieldValidationServiceTest.java` | Generic validation tests |

### Modified files
| File | What changes |
|------|-------------|
| `activity/entity/Activity.java` | Replace `type` FK with `typeSlug`, add `imageUrl`, remove `subtype`/`difficultyLabel` |
| `activity/dto/ActivityCreateRequest.java` | `activityTypeId` (UUID) → `typeSlug` (String) |
| `activity/dto/ActivityDetailResponse.java` | Remove `activityTypeId`, add `imageUrl` |
| `activity/dto/ActivityListItemResponse.java` | Add `List<ActivitySummaryField> summary` |
| `activity/dto/ActivityFilterParams.java` | `activityTypeId` → `typeSlug` |
| `activity/ActivityService.java` | Use registry + validation service, build summary fields, fix image URL |
| `activity/ActivityController.java` | Add Swagger annotations |
| `activity/enums/ActivityStatus.java` | Remove `DRAFT` |
| `activitytype/ActivityTypeService.java` | Read from registry instead of DB |
| `activitytype/ActivityTypeController.java` | Add Swagger annotations |
| `activitytype/dto/ActivityTypeResponse.java` | Remove `id`, add `defaultImageUrl`, `summaryFields` |
| `testFixtures: fixtures/ActivityFixture.java` | Use `typeSlug` string |
| `test: activity/ActivityServiceTest.java` | Mock registry + validation service |
| `test: activity/ActivityIntegrationTest.java` | Remove DB seeding, use typeSlug, add new test cases |
| `test: activitytype/ActivityTypeServiceTest.java` | Test registry-backed listing |
| `test: activitytype/dto/CustomFieldDefinitionTest.java` | Update import path |

### Deleted files
| File | Reason |
|------|--------|
| `activity/validation/ActivityFieldValidator.java` | Replaced by `ActivityFieldValidationService` |
| `activity/validation/ClimbingFieldValidator.java` | Replaced by `ClimbingType` |
| `activity/validation/CyclingFieldValidator.java` | Replaced by `CyclingType` |
| `activity/validation/RunningFieldValidator.java` | Replaced by `RunningType` |
| `activity/validation/SkiingFieldValidator.java` | Replaced by `SkiingType` |
| `activity/validation/YogaFieldValidator.java` | Replaced by `YogaType` |
| `activitytype/entity/ActivityType.java` | DB entity removed |
| `activitytype/ActivityTypeRepository.java` | DB repository removed |
| `activitytype/dto/CustomFieldDefinition.java` | Moved to `definition/` |
| `activitytype/model/CustomFieldDefinition.java` | Dead enum |
| `activitytype/model/Option.java` | Dead code |
| `activitytype/model/ValidatorConfig.java` | Dead code |
| `activity/model/Option.java` | Dead code |
| `activity/model/ValidatorConfig.java` | Dead code |
| `activity/model/ValidatorType.java` | Dead code |
| `testFixtures: fixtures/ActivityTypeFixture.java` | No longer needed |
| `test: activity/validation/GenericFieldValidatorTest.java` | Replaced |
| `test: activity/validation/ClimbingFieldValidatorTest.java` | Replaced |

---

## Task 1: Move CustomFieldDefinition and create ActivityTypeDefinition interface

**Files:**
- Create: `activitytype/definition/ActivityTypeDefinition.java`
- Move: `activitytype/dto/CustomFieldDefinition.java` → `activitytype/definition/CustomFieldDefinition.java`
- Modify: `test: activitytype/dto/CustomFieldDefinitionTest.java` (update import)

- [ ] **Step 1: Create the CustomFieldDefinition record in its new package**

Create `backend/src/main/java/ch/junto/activitytype/definition/CustomFieldDefinition.java`:

```java
package ch.junto.activitytype.definition;

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
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        if (label == null || label.isBlank()) throw new IllegalArgumentException("label must not be blank");
        if (type == FieldType.SELECT) {
            if (options == null || options.isEmpty()) throw new IllegalArgumentException("SELECT fields must define options");
        } else {
            options = options == null ? List.of() : List.of();
        }
        if (type == FieldType.NUMBER_WITH_UNIT) {
            if (unit == null) throw new IllegalArgumentException("NUMBER_WITH_UNIT fields must define a unit");
        } else {
            if (unit != null) throw new IllegalArgumentException("Only NUMBER_WITH_UNIT fields may define a unit");
        }
        if (options != null && !options.isEmpty()) {
            options = List.copyOf(options);
        }
    }
}
```

- [ ] **Step 2: Create ActivityTypeDefinition interface**

Create `backend/src/main/java/ch/junto/activitytype/definition/ActivityTypeDefinition.java`:

```java
package ch.junto.activitytype.definition;

import java.util.List;

/**
 * Defines an activity type's metadata and field schema.
 *
 * Slugs and field names are persisted in the activity table and must remain
 * stable once released. Changing a slug requires a data migration.
 */
public interface ActivityTypeDefinition {
    /** Stable identifier. Must match ^[a-z0-9-]+$ and never be renamed. */
    String getSlug();
    String getName();
    String getIconName();
    /** Required. Fallback image when activity has no custom imageUrl. */
    String getDefaultImageUrl();
    /** Field names must be unique within this list. */
    List<CustomFieldDefinition> getFieldDefinitions();
    /** Ordered list referencing field names from getFieldDefinitions(). */
    List<String> getSummaryFields();
}
```

- [ ] **Step 3: Delete the old CustomFieldDefinition in dto package**

```bash
rm backend/src/main/java/ch/junto/activitytype/dto/CustomFieldDefinition.java
```

- [ ] **Step 4: Update all imports across codebase from `activitytype.dto.CustomFieldDefinition` to `activitytype.definition.CustomFieldDefinition`**

Files that import `CustomFieldDefinition`:
- `activitytype/ActivityTypeService.java`
- `activity/validation/ActivityFieldValidator.java`
- `activity/validation/ClimbingFieldValidator.java`
- `activity/validation/CyclingFieldValidator.java`
- `activity/validation/RunningFieldValidator.java`
- `activity/validation/SkiingFieldValidator.java`
- `activity/validation/YogaFieldValidator.java`
- `test: activitytype/dto/CustomFieldDefinitionTest.java`
- `test: activity/validation/GenericFieldValidatorTest.java`
- `test: activity/validation/ClimbingFieldValidatorTest.java`

In each file, replace:
```java
import ch.junto.activitytype.dto.CustomFieldDefinition;
```
with:
```java
import ch.junto.activitytype.definition.CustomFieldDefinition;
```

Also update `CustomFieldDefinitionTest.java` package declaration if needed, or just update its import.

- [ ] **Step 5: Verify compilation**

Run: `./gradlew compileJava compileTestJava -p backend`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Run existing tests to confirm nothing broke**

Run: `./gradlew test -p backend`
Expected: All existing tests PASS

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor: move CustomFieldDefinition to definition package, add ActivityTypeDefinition interface"
```

---

## Task 2: Create ActivityTypeRegistry with strict startup validation

**Files:**
- Create: `activitytype/ActivityTypeRegistry.java`
- Create: `test: activitytype/ActivityTypeRegistryTest.java`

- [ ] **Step 1: Write ActivityTypeRegistry**

Create `backend/src/main/java/ch/junto/activitytype/ActivityTypeRegistry.java`:

```java
package ch.junto.activitytype;

import ch.junto.activitytype.definition.ActivityTypeDefinition;
import ch.junto.activitytype.definition.CustomFieldDefinition;
import ch.junto.core.exception.NotFoundException;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class ActivityTypeRegistry {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9-]+$");

    private final Map<String, ActivityTypeDefinition> types;
    private final List<ActivityTypeDefinition> sortedTypes;

    public ActivityTypeRegistry(List<ActivityTypeDefinition> definitions) {
        validate(definitions);
        this.types = definitions.stream()
            .collect(Collectors.toMap(ActivityTypeDefinition::getSlug, d -> d));
        this.sortedTypes = definitions.stream()
            .sorted(Comparator.comparing(ActivityTypeDefinition::getSlug))
            .toList();
    }

    public ActivityTypeDefinition get(String slug) {
        ActivityTypeDefinition type = types.get(slug);
        if (type == null) {
            throw new NotFoundException("Unknown activity type: " + slug);
        }
        return type;
    }

    public List<ActivityTypeDefinition> listAll() {
        return sortedTypes;
    }

    public boolean exists(String slug) {
        return types.containsKey(slug);
    }

    private void validate(List<ActivityTypeDefinition> definitions) {
        Set<String> seenSlugs = new HashSet<>();

        for (ActivityTypeDefinition def : definitions) {
            String slug = def.getSlug();

            requireNonBlank(slug, "slug");
            if (!SLUG_PATTERN.matcher(slug).matches()) {
                throw new IllegalStateException(
                    "Activity type slug '" + slug + "' must match ^[a-z0-9-]+$");
            }
            if (!seenSlugs.add(slug)) {
                throw new IllegalStateException("Duplicate activity type slug: " + slug);
            }

            requireNonBlank(def.getName(), "name for type '" + slug + "'");
            requireNonBlank(def.getIconName(), "iconName for type '" + slug + "'");
            requireNonBlank(def.getDefaultImageUrl(), "defaultImageUrl for type '" + slug + "'");

            List<CustomFieldDefinition> fields = def.getFieldDefinitions();
            if (fields == null || fields.isEmpty()) {
                throw new IllegalStateException(
                    "Activity type '" + slug + "' must define at least one field");
            }

            Set<String> fieldNames = new HashSet<>();
            for (CustomFieldDefinition field : fields) {
                if (!fieldNames.add(field.name())) {
                    throw new IllegalStateException(
                        "Duplicate field name '" + field.name() + "' in type '" + slug + "'");
                }
            }

            List<String> summaryFields = def.getSummaryFields();
            if (summaryFields != null) {
                Set<String> seenSummary = new HashSet<>();
                for (String sf : summaryFields) {
                    if (!fieldNames.contains(sf)) {
                        throw new IllegalStateException(
                            "Summary field '" + sf + "' in type '" + slug + "' does not reference a defined field");
                    }
                    if (!seenSummary.add(sf)) {
                        throw new IllegalStateException(
                            "Duplicate summary field '" + sf + "' in type '" + slug + "'");
                    }
                }
            }
        }
    }

    private void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Activity type " + fieldName + " must not be blank");
        }
    }
}
```

- [ ] **Step 2: Write ActivityTypeRegistryTest**

Create `backend/src/test/java/ch/junto/activitytype/ActivityTypeRegistryTest.java`:

```java
package ch.junto.activitytype;

import ch.junto.activitytype.definition.ActivityTypeDefinition;
import ch.junto.activitytype.definition.CustomFieldDefinition;
import ch.junto.activitytype.model.FieldType;
import ch.junto.core.exception.NotFoundException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ActivityTypeRegistryTest {

    private ActivityTypeDefinition testType(String slug) {
        return testType(slug, List.of("field1"), List.of("field1"));
    }

    private ActivityTypeDefinition testType(String slug, List<String> fieldNames, List<String> summaryFields) {
        List<CustomFieldDefinition> defs = fieldNames.stream()
            .map(n -> new CustomFieldDefinition(n, n, FieldType.TEXT, false, null, null))
            .toList();
        return new ActivityTypeDefinition() {
            @Override public String getSlug() { return slug; }
            @Override public String getName() { return slug; }
            @Override public String getIconName() { return slug; }
            @Override public String getDefaultImageUrl() { return "https://example.com/" + slug + ".jpg"; }
            @Override public List<CustomFieldDefinition> getFieldDefinitions() { return defs; }
            @Override public List<String> getSummaryFields() { return summaryFields; }
        };
    }

    @Test
    void get_knownSlug_returnsDefinition() {
        var registry = new ActivityTypeRegistry(List.of(testType("test")));
        assertThat(registry.get("test").getSlug()).isEqualTo("test");
    }

    @Test
    void get_unknownSlug_throwsNotFoundException() {
        var registry = new ActivityTypeRegistry(List.of(testType("test")));
        assertThatThrownBy(() -> registry.get("unknown"))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void exists_knownSlug_returnsTrue() {
        var registry = new ActivityTypeRegistry(List.of(testType("test")));
        assertThat(registry.exists("test")).isTrue();
    }

    @Test
    void exists_unknownSlug_returnsFalse() {
        var registry = new ActivityTypeRegistry(List.of(testType("test")));
        assertThat(registry.exists("unknown")).isFalse();
    }

    @Test
    void listAll_returnsSortedBySlug() {
        var registry = new ActivityTypeRegistry(List.of(
            testType("cycling"), testType("alpine"), testType("boxing")
        ));
        assertThat(registry.listAll())
            .extracting(ActivityTypeDefinition::getSlug)
            .containsExactly("alpine", "boxing", "cycling");
    }

    @Test
    void rejects_duplicateSlugs() {
        assertThatThrownBy(() -> new ActivityTypeRegistry(List.of(
            testType("test"), testType("test")
        ))).isInstanceOf(IllegalStateException.class)
           .hasMessageContaining("Duplicate");
    }

    @Test
    void rejects_blankSlug() {
        assertThatThrownBy(() -> new ActivityTypeRegistry(List.of(testType(""))))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejects_invalidSlugFormat() {
        assertThatThrownBy(() -> new ActivityTypeRegistry(List.of(testType("Bad Slug!"))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("must match");
    }

    @Test
    void rejects_summaryFieldNotInDefinitions() {
        assertThatThrownBy(() -> new ActivityTypeRegistry(List.of(
            testType("test", List.of("field1"), List.of("nonexistent"))
        ))).isInstanceOf(IllegalStateException.class)
           .hasMessageContaining("does not reference");
    }

    @Test
    void rejects_duplicateSummaryFields() {
        assertThatThrownBy(() -> new ActivityTypeRegistry(List.of(
            testType("test", List.of("field1"), List.of("field1", "field1"))
        ))).isInstanceOf(IllegalStateException.class)
           .hasMessageContaining("Duplicate summary");
    }

    @Test
    void rejects_duplicateFieldNames() {
        var badType = new ActivityTypeDefinition() {
            @Override public String getSlug() { return "test"; }
            @Override public String getName() { return "Test"; }
            @Override public String getIconName() { return "test"; }
            @Override public String getDefaultImageUrl() { return "https://example.com/test.jpg"; }
            @Override public List<CustomFieldDefinition> getFieldDefinitions() {
                return List.of(
                    new CustomFieldDefinition("dup", "Dup1", FieldType.TEXT, false, null, null),
                    new CustomFieldDefinition("dup", "Dup2", FieldType.TEXT, false, null, null)
                );
            }
            @Override public List<String> getSummaryFields() { return List.of(); }
        };
        assertThatThrownBy(() -> new ActivityTypeRegistry(List.of(badType)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate field name");
    }

    @Test
    void rejects_emptyFieldDefinitions() {
        var badType = new ActivityTypeDefinition() {
            @Override public String getSlug() { return "test"; }
            @Override public String getName() { return "Test"; }
            @Override public String getIconName() { return "test"; }
            @Override public String getDefaultImageUrl() { return "https://example.com/test.jpg"; }
            @Override public List<CustomFieldDefinition> getFieldDefinitions() { return List.of(); }
            @Override public List<String> getSummaryFields() { return List.of(); }
        };
        assertThatThrownBy(() -> new ActivityTypeRegistry(List.of(badType)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("at least one field");
    }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew test --tests "ch.junto.activitytype.ActivityTypeRegistryTest" -p backend`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/ch/junto/activitytype/ActivityTypeRegistry.java \
      backend/src/test/java/ch/junto/activitytype/ActivityTypeRegistryTest.java
git commit -m "feat: add ActivityTypeRegistry with strict startup validation"
```

---

## Task 3: Create type definition components

**Files:**
- Create: `activitytype/definition/ClimbingType.java`
- Create: `activitytype/definition/CyclingType.java`
- Create: `activitytype/definition/RunningType.java`
- Create: `activitytype/definition/SkiingType.java`
- Create: `activitytype/definition/YogaType.java`
- Modify: `test: activitytype/ActivityTypeRegistryTest.java` (add per-type tests)

- [ ] **Step 1: Create ClimbingType**

Create `backend/src/main/java/ch/junto/activitytype/definition/ClimbingType.java`:

```java
package ch.junto.activitytype.definition;

import ch.junto.activitytype.model.FieldType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ClimbingType implements ActivityTypeDefinition {

    private static final List<String> STYLES = List.of("Bouldering", "Sport", "Trad", "Top Rope", "Ice", "Alpine");
    private static final List<String> ENVIRONMENTS = List.of("Indoor", "Outdoor");
    private static final List<String> EXPERIENCE_LEVELS = List.of("Beginner", "Intermediate", "Advanced");

    @Override public String getSlug() { return "climbing"; }
    @Override public String getName() { return "Climbing"; }
    @Override public String getIconName() { return "mountain"; }
    @Override public String getDefaultImageUrl() { return "https://images.unsplash.com/photo-1522163182402-834f871fd851?auto=format&fit=crop&w=800&q=80"; }

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
    public List<String> getSummaryFields() { return List.of("style", "grade"); }
}
```

- [ ] **Step 2: Create CyclingType**

Create `backend/src/main/java/ch/junto/activitytype/definition/CyclingType.java`:

```java
package ch.junto.activitytype.definition;

import ch.junto.activitytype.model.FieldType;
import ch.junto.activitytype.model.Unit;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CyclingType implements ActivityTypeDefinition {

    private static final List<String> DIFFICULTIES = List.of("Beginner", "Intermediate", "Advanced");

    @Override public String getSlug() { return "cycling"; }
    @Override public String getName() { return "Cycling"; }
    @Override public String getIconName() { return "bike"; }
    @Override public String getDefaultImageUrl() { return "https://images.unsplash.com/photo-1541625602330-2277a4c4b08d?auto=format&fit=crop&w=800&q=80"; }

    @Override
    public List<CustomFieldDefinition> getFieldDefinitions() {
        return List.of(
            new CustomFieldDefinition("distance", "Distance", FieldType.NUMBER_WITH_UNIT, true, null, Unit.KM),
            new CustomFieldDefinition("duration", "Duration", FieldType.NUMBER_WITH_UNIT, false, null, Unit.HOURS),
            new CustomFieldDefinition("difficulty", "Difficulty", FieldType.SELECT, true, DIFFICULTIES, null)
        );
    }

    @Override
    public List<String> getSummaryFields() { return List.of("distance"); }
}
```

- [ ] **Step 3: Create RunningType**

Create `backend/src/main/java/ch/junto/activitytype/definition/RunningType.java`:

```java
package ch.junto.activitytype.definition;

import ch.junto.activitytype.model.FieldType;
import ch.junto.activitytype.model.Unit;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RunningType implements ActivityTypeDefinition {

    private static final List<String> SURFACES = List.of("Road", "Trail", "Track", "Treadmill");
    private static final List<String> DIFFICULTIES = List.of("Beginner", "Intermediate", "Advanced");

    @Override public String getSlug() { return "running"; }
    @Override public String getName() { return "Running"; }
    @Override public String getIconName() { return "running"; }
    @Override public String getDefaultImageUrl() { return "https://images.unsplash.com/photo-1476480862126-209bfaa8edc8?auto=format&fit=crop&w=800&q=80"; }

    @Override
    public List<CustomFieldDefinition> getFieldDefinitions() {
        return List.of(
            new CustomFieldDefinition("distance", "Distance", FieldType.NUMBER_WITH_UNIT, true, null, Unit.KM),
            new CustomFieldDefinition("surface", "Surface", FieldType.SELECT, true, SURFACES, null),
            new CustomFieldDefinition("difficulty", "Difficulty", FieldType.SELECT, true, DIFFICULTIES, null)
        );
    }

    @Override
    public List<String> getSummaryFields() { return List.of("distance"); }
}
```

- [ ] **Step 4: Create SkiingType**

Create `backend/src/main/java/ch/junto/activitytype/definition/SkiingType.java`:

```java
package ch.junto.activitytype.definition;

import ch.junto.activitytype.model.FieldType;
import ch.junto.activitytype.model.Unit;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SkiingType implements ActivityTypeDefinition {

    private static final List<String> LEVELS = List.of("Beginner", "Intermediate", "Advanced", "Expert");

    @Override public String getSlug() { return "skiing"; }
    @Override public String getName() { return "Skiing"; }
    @Override public String getIconName() { return "skiing"; }
    @Override public String getDefaultImageUrl() { return "https://images.unsplash.com/photo-1551698618-1fed5d975506?auto=format&fit=crop&w=800&q=80"; }

    @Override
    public List<CustomFieldDefinition> getFieldDefinitions() {
        return List.of(
            new CustomFieldDefinition("level", "Level", FieldType.SELECT, true, LEVELS, null),
            new CustomFieldDefinition("elevationGain", "Elevation Gain", FieldType.NUMBER_WITH_UNIT, false, null, Unit.METERS)
        );
    }

    @Override
    public List<String> getSummaryFields() { return List.of("level"); }
}
```

- [ ] **Step 5: Create YogaType**

Create `backend/src/main/java/ch/junto/activitytype/definition/YogaType.java`:

```java
package ch.junto.activitytype.definition;

import ch.junto.activitytype.model.FieldType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class YogaType implements ActivityTypeDefinition {

    private static final List<String> STYLES = List.of("Vinyasa", "Hatha", "Ashtanga", "Yin", "Bikram", "Restorative");
    private static final List<String> LEVELS = List.of("Beginner", "Intermediate", "Advanced");

    @Override public String getSlug() { return "yoga"; }
    @Override public String getName() { return "Yoga"; }
    @Override public String getIconName() { return "yoga"; }
    @Override public String getDefaultImageUrl() { return "https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?auto=format&fit=crop&w=800&q=80"; }

    @Override
    public List<CustomFieldDefinition> getFieldDefinitions() {
        return List.of(
            new CustomFieldDefinition("style", "Style", FieldType.SELECT, true, STYLES, null),
            new CustomFieldDefinition("level", "Level", FieldType.SELECT, true, LEVELS, null),
            new CustomFieldDefinition("bringMat", "Bring your own mat", FieldType.BOOLEAN, false, null, null)
        );
    }

    @Override
    public List<String> getSummaryFields() { return List.of("style"); }
}
```

- [ ] **Step 6: Add per-type and full-registry tests to ActivityTypeRegistryTest**

Add to the existing `ActivityTypeRegistryTest.java`:

```java
// Add these imports:
import ch.junto.activitytype.definition.*;
import java.util.Set;
import java.util.stream.Collectors;

// Add a helper and tests:
private ActivityTypeRegistry fullRegistry() {
    return new ActivityTypeRegistry(List.of(
        new ClimbingType(), new CyclingType(), new RunningType(),
        new SkiingType(), new YogaType()
    ));
}

@Test
void fullRegistry_containsAllFiveTypes() {
    var registry = fullRegistry();
    assertThat(registry.listAll()).hasSize(5);
    assertThat(registry.exists("climbing")).isTrue();
    assertThat(registry.exists("cycling")).isTrue();
    assertThat(registry.exists("running")).isTrue();
    assertThat(registry.exists("skiing")).isTrue();
    assertThat(registry.exists("yoga")).isTrue();
}

@Test
void fullRegistry_listAllIsSortedBySlug() {
    var registry = fullRegistry();
    assertThat(registry.listAll())
        .extracting(ActivityTypeDefinition::getSlug)
        .containsExactly("climbing", "cycling", "running", "skiing", "yoga");
}

@Test
void allTypes_summaryFieldsReferenceActualFieldNames() {
    var registry = fullRegistry();
    for (var type : registry.listAll()) {
        Set<String> fieldNames = type.getFieldDefinitions().stream()
            .map(CustomFieldDefinition::name)
            .collect(Collectors.toSet());
        for (String sf : type.getSummaryFields()) {
            assertThat(fieldNames)
                .as("Summary field '%s' in %s", sf, type.getSlug())
                .contains(sf);
        }
    }
}

@Test void climbingType_hasCorrectFields() {
    var t = new ClimbingType();
    assertThat(t.getFieldDefinitions()).hasSize(5);
    assertThat(t.getSummaryFields()).containsExactly("style", "grade");
}

@Test void cyclingType_hasCorrectFields() {
    var t = new CyclingType();
    assertThat(t.getFieldDefinitions()).hasSize(3);
    assertThat(t.getSummaryFields()).containsExactly("distance");
}

@Test void runningType_hasCorrectFields() {
    var t = new RunningType();
    assertThat(t.getFieldDefinitions()).hasSize(3);
    assertThat(t.getSummaryFields()).containsExactly("distance");
}

@Test void skiingType_hasCorrectFields() {
    var t = new SkiingType();
    assertThat(t.getFieldDefinitions()).hasSize(2);
    assertThat(t.getSummaryFields()).containsExactly("level");
}

@Test void yogaType_hasCorrectFields() {
    var t = new YogaType();
    assertThat(t.getFieldDefinitions()).hasSize(3);
    assertThat(t.getSummaryFields()).containsExactly("style");
}
```

- [ ] **Step 7: Run tests**

Run: `./gradlew test --tests "ch.junto.activitytype.ActivityTypeRegistryTest" -p backend`
Expected: All tests PASS

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/ch/junto/activitytype/definition/ \
      backend/src/test/java/ch/junto/activitytype/ActivityTypeRegistryTest.java
git commit -m "feat: add type definition components for all five sports"
```

---

## Task 4: Create ActivityFieldValidationService

**Files:**
- Create: `activity/ActivityFieldValidationService.java`
- Create: `test: activity/ActivityFieldValidationServiceTest.java`

- [ ] **Step 1: Create ActivityFieldValidationService**

Create `backend/src/main/java/ch/junto/activity/ActivityFieldValidationService.java`:

```java
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
```

- [ ] **Step 2: Create ActivityFieldValidationServiceTest**

Create `backend/src/test/java/ch/junto/activity/ActivityFieldValidationServiceTest.java`:

```java
package ch.junto.activity;

import ch.junto.activitytype.ActivityTypeRegistry;
import ch.junto.activitytype.definition.ActivityTypeDefinition;
import ch.junto.activitytype.definition.ClimbingType;
import ch.junto.activitytype.definition.CustomFieldDefinition;
import ch.junto.activitytype.model.FieldType;
import ch.junto.activitytype.model.Unit;
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
```

- [ ] **Step 3: Run tests**

Run: `./gradlew test --tests "ch.junto.activity.ActivityFieldValidationServiceTest" -p backend`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/ch/junto/activity/ActivityFieldValidationService.java \
      backend/src/test/java/ch/junto/activity/ActivityFieldValidationServiceTest.java
git commit -m "feat: add ActivityFieldValidationService with generic field validation"
```

---

## Task 5: DB migration and entity/DTO updates

**Files:**
- Create: `resources/db/migration/V015__rework_activity_types.sql`
- Modify: `activity/entity/Activity.java`
- Modify: `activity/dto/ActivityCreateRequest.java`
- Modify: `activity/dto/ActivityDetailResponse.java`
- Create: `activity/dto/ActivitySummaryField.java`
- Modify: `activity/dto/ActivityListItemResponse.java`
- Modify: `activity/dto/ActivityFilterParams.java`
- Modify: `activity/enums/ActivityStatus.java`

- [ ] **Step 1: Write the migration**

Create `backend/src/main/resources/db/migration/V015__rework_activity_types.sql`:

```sql
-- Add type_slug and populate from activity_types
ALTER TABLE activities ADD COLUMN type_slug VARCHAR(50);
UPDATE activities SET type_slug = (SELECT slug FROM activity_types WHERE id = activities.activity_type_id);
ALTER TABLE activities ALTER COLUMN type_slug SET NOT NULL;
CREATE INDEX idx_activities_type_slug ON activities (type_slug);

-- Add optional image_url for creator overrides
ALTER TABLE activities ADD COLUMN image_url VARCHAR(500);

-- Drop FK and old columns
ALTER TABLE activities DROP CONSTRAINT IF EXISTS fk_activities_activity_type_id;
ALTER TABLE activities DROP COLUMN IF EXISTS activity_type_id;
ALTER TABLE activities DROP COLUMN IF EXISTS subtype;
ALTER TABLE activities DROP COLUMN IF EXISTS difficulty_label;

-- Drop activity_types table
DROP TABLE IF EXISTS activity_types;
```

- [ ] **Step 2: Update Activity entity**

Rewrite `backend/src/main/java/ch/junto/activity/entity/Activity.java`:

```java
package ch.junto.activity.entity;

import ch.junto.user.entity.User;
import ch.junto.activity.enums.ActivityStatus;
import ch.junto.activity.enums.JoinPolicy;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "activities")
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Column(name = "type_slug", nullable = false)
    private String typeSlug;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column
    private String city;

    @Column(name = "location_lat")
    private Double locationLat;

    @Column(name = "location_lng")
    private Double locationLng;

    @Column(columnDefinition = "geography(Point, 4326)")
    private Point location;

    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @Column(name = "signup_deadline")
    private OffsetDateTime signupDeadline;

    @Column(name = "max_participants", nullable = false)
    private int maxParticipants = 10;

    @Enumerated(EnumType.STRING)
    @Column(name = "join_policy", nullable = false)
    private JoinPolicy joinPolicy = JoinPolicy.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityStatus status = ActivityStatus.ACTIVE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_fields", columnDefinition = "jsonb")
    private CustomFields customFields = new CustomFields();

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // --- Getters and Setters ---

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public User getCreator() { return creator; }
    public void setCreator(User creator) { this.creator = creator; }

    public String getTypeSlug() { return typeSlug; }
    public void setTypeSlug(String typeSlug) { this.typeSlug = typeSlug; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public Double getLocationLat() { return locationLat; }
    public void setLocationLat(Double locationLat) { this.locationLat = locationLat; }

    public Double getLocationLng() { return locationLng; }
    public void setLocationLng(Double locationLng) { this.locationLng = locationLng; }

    public Point getLocation() { return location; }
    public void setLocation(Point location) { this.location = location; }

    public OffsetDateTime getStartsAt() { return startsAt; }
    public void setStartsAt(OffsetDateTime startsAt) { this.startsAt = startsAt; }

    public OffsetDateTime getSignupDeadline() { return signupDeadline; }
    public void setSignupDeadline(OffsetDateTime signupDeadline) { this.signupDeadline = signupDeadline; }

    public int getMaxParticipants() { return maxParticipants; }
    public void setMaxParticipants(int maxParticipants) { this.maxParticipants = maxParticipants; }

    public JoinPolicy getJoinPolicy() { return joinPolicy; }
    public void setJoinPolicy(JoinPolicy joinPolicy) { this.joinPolicy = joinPolicy; }

    public ActivityStatus getStatus() { return status; }
    public void setStatus(ActivityStatus status) { this.status = status; }

    public CustomFields getCustomFields() { return customFields; }
    public void setCustomFields(CustomFields customFields) { this.customFields = customFields; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 3: Update ActivityCreateRequest**

Rewrite `backend/src/main/java/ch/junto/activity/dto/ActivityCreateRequest.java`:

```java
package ch.junto.activity.dto;

import ch.junto.activity.enums.JoinPolicy;
import ch.junto.activity.entity.CustomFields;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record ActivityCreateRequest(
    @NotBlank String title,
    String description,
    @NotBlank @Schema(example = "cycling") String typeSlug,
    String city,
    Double locationLat,
    Double locationLng,
    @NotNull OffsetDateTime startsAt,
    OffsetDateTime signupDeadline,
    int maxParticipants,
    @NotNull JoinPolicy joinPolicy,
    CustomFields customFields
) {}
```

- [ ] **Step 4: Update ActivityDetailResponse**

Rewrite `backend/src/main/java/ch/junto/activity/dto/ActivityDetailResponse.java`:

```java
package ch.junto.activity.dto;

import ch.junto.activity.enums.ActivityStatus;
import ch.junto.activity.enums.JoinPolicy;
import ch.junto.activity.entity.CustomFields;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ActivityDetailResponse(
    UUID id,
    String title,
    String description,
    String activityTypeName,
    String activityTypeSlug,
    String city,
    Double locationLat,
    Double locationLng,
    OffsetDateTime startsAt,
    OffsetDateTime signupDeadline,
    int maxParticipants,
    int currentParticipants,
    JoinPolicy joinPolicy,
    ActivityStatus status,
    CustomFields customFields,
    String imageUrl,
    UUID creatorId,
    String creatorDisplayName,
    String creatorAvatarUrl,
    boolean isJoinable,
    OffsetDateTime effectiveDeadline
) {}
```

- [ ] **Step 5: Create ActivitySummaryField record**

Create `backend/src/main/java/ch/junto/activity/dto/ActivitySummaryField.java`:

```java
package ch.junto.activity.dto;

public record ActivitySummaryField(
    String fieldName,
    String label,
    Object value,
    String displayValue
) {}
```

- [ ] **Step 6: Update ActivityListItemResponse**

Rewrite `backend/src/main/java/ch/junto/activity/dto/ActivityListItemResponse.java`:

```java
package ch.junto.activity.dto;

import ch.junto.activity.enums.ActivityStatus;
import ch.junto.activity.entity.CustomFields;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ActivityListItemResponse(
    UUID id,
    String title,
    String activityTypeName,
    String activityTypeSlug,
    String city,
    OffsetDateTime startsAt,
    int maxParticipants,
    int currentParticipants,
    ActivityStatus status,
    String creatorDisplayName,
    String creatorAvatarUrl,
    CustomFields customFields,
    String imageUrl,
    List<String> participantAvatars,
    List<ActivitySummaryField> summary
) {}
```

- [ ] **Step 7: Update ActivityFilterParams**

Rewrite `backend/src/main/java/ch/junto/activity/dto/ActivityFilterParams.java`:

```java
package ch.junto.activity.dto;

import ch.junto.activity.enums.ActivityStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record ActivityFilterParams(
    String city,
    @Schema(example = "cycling") String typeSlug,
    ActivityStatus status,
    OffsetDateTime fromDate,
    OffsetDateTime toDate,
    Boolean joinable
) {}
```

- [ ] **Step 8: Remove DRAFT from ActivityStatus**

Rewrite `backend/src/main/java/ch/junto/activity/enums/ActivityStatus.java`:

```java
package ch.junto.activity.enums;

public enum ActivityStatus {
    ACTIVE,
    CANCELLED,
    COMPLETED
}
```

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat: update Activity entity, DTOs, and DB migration for type_slug"
```

---

## Task 6: Rewrite ActivityService, ActivityTypeService, and responses

**Files:**
- Modify: `activity/ActivityService.java`
- Modify: `activitytype/ActivityTypeService.java`
- Modify: `activitytype/dto/ActivityTypeResponse.java`
- Modify: `activitytype/ActivityTypeController.java` (method name change)

- [ ] **Step 1: Update ActivityTypeResponse**

Rewrite `backend/src/main/java/ch/junto/activitytype/dto/ActivityTypeResponse.java`:

```java
package ch.junto.activitytype.dto;

import ch.junto.activitytype.definition.CustomFieldDefinition;
import java.util.List;

public record ActivityTypeResponse(
    String slug,
    String name,
    String iconName,
    String defaultImageUrl,
    List<CustomFieldDefinition> customFields,
    List<String> summaryFields
) {}
```

- [ ] **Step 2: Rewrite ActivityTypeService**

Rewrite `backend/src/main/java/ch/junto/activitytype/ActivityTypeService.java`:

```java
package ch.junto.activitytype;

import ch.junto.activitytype.definition.ActivityTypeDefinition;
import ch.junto.activitytype.dto.ActivityTypeResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ActivityTypeService {

    private final ActivityTypeRegistry registry;

    public ActivityTypeService(ActivityTypeRegistry registry) {
        this.registry = registry;
    }

    public List<ActivityTypeResponse> listTypes() {
        return registry.listAll().stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    private ActivityTypeResponse mapToResponse(ActivityTypeDefinition type) {
        return new ActivityTypeResponse(
            type.getSlug(),
            type.getName(),
            type.getIconName(),
            type.getDefaultImageUrl(),
            type.getFieldDefinitions(),
            type.getSummaryFields()
        );
    }
}
```

- [ ] **Step 3: Update ActivityTypeController method call**

In `backend/src/main/java/ch/junto/activitytype/ActivityTypeController.java`, change line 21:

Replace:
```java
        return activityTypeService.listActiveTypes();
```
With:
```java
        return activityTypeService.listTypes();
```

- [ ] **Step 4: Rewrite ActivityService**

Rewrite `backend/src/main/java/ch/junto/activity/ActivityService.java`:

```java
package ch.junto.activity;

import ch.junto.activity.dto.*;
import ch.junto.activitytype.ActivityTypeRegistry;
import ch.junto.activitytype.definition.ActivityTypeDefinition;
import ch.junto.activitytype.definition.CustomFieldDefinition;
import ch.junto.activitytype.model.FieldType;
import ch.junto.participant.ParticipantService;
import ch.junto.activity.entity.Activity;
import ch.junto.user.entity.User;
import ch.junto.activity.enums.ActivityStatus;
import ch.junto.core.exception.NotFoundException;
import ch.junto.core.exception.ForbiddenException;
import ch.junto.core.pagination.CursorPage;
import ch.junto.core.pagination.CursorPageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final ActivityTypeRegistry typeRegistry;
    private final ActivityFieldValidationService validationService;
    private final ParticipantService participantService;

    public ActivityService(
        ActivityRepository activityRepository,
        ActivityTypeRegistry typeRegistry,
        ActivityFieldValidationService validationService,
        ParticipantService participantService
    ) {
        this.activityRepository = activityRepository;
        this.typeRegistry = typeRegistry;
        this.validationService = validationService;
        this.participantService = participantService;
    }

    public ActivityDetailResponse create(ActivityCreateRequest request, User creator) {
        ActivityTypeDefinition type = typeRegistry.get(request.typeSlug());

        Activity activity = new Activity();
        activity.setCreator(creator);
        activity.setTypeSlug(type.getSlug());
        activity.setTitle(request.title());
        activity.setDescription(request.description());
        activity.setCity(request.city());
        activity.setLocationLat(request.locationLat());
        activity.setLocationLng(request.locationLng());
        activity.setStartsAt(request.startsAt());
        activity.setSignupDeadline(request.signupDeadline());
        activity.setMaxParticipants(request.maxParticipants());
        activity.setJoinPolicy(request.joinPolicy());

        if (request.customFields() != null) {
            activity.setCustomFields(request.customFields());
        }

        validationService.validate(type.getSlug(), activity.getCustomFields());

        activity = activityRepository.save(activity);
        participantService.addCreator(activity, creator);
        return mapToDetailResponse(activity);
    }

    @Transactional(readOnly = true)
    public ActivityDetailResponse getDetail(UUID id) {
        Activity activity = activityRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Activity not found"));
        return mapToDetailResponse(activity);
    }

    public ActivityDetailResponse update(UUID id, ActivityUpdateRequest request, User user) {
        Activity activity = activityRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Activity not found"));

        if (!activity.getCreator().getId().equals(user.getId())) {
            throw new ForbiddenException("Only creator can update activity");
        }

        activity.setTitle(request.title());
        activity.setDescription(request.description());
        activity.setCity(request.city());
        activity.setLocationLat(request.locationLat());
        activity.setLocationLng(request.locationLng());
        activity.setStartsAt(request.startsAt());
        activity.setSignupDeadline(request.signupDeadline());

        if (request.customFields() != null) {
            activity.getCustomFields().putAll(request.customFields());
        }

        validationService.validate(activity.getTypeSlug(), activity.getCustomFields());

        activity = activityRepository.save(activity);
        return mapToDetailResponse(activity);
    }

    public void cancel(UUID id, User user) {
        Activity activity = activityRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Activity not found"));

        if (!activity.getCreator().getId().equals(user.getId())) {
            throw new ForbiddenException("Only creator can cancel activity");
        }

        activity.setStatus(ActivityStatus.CANCELLED);
        activityRepository.save(activity);
    }

    public void complete(UUID id, User user) {
        Activity activity = activityRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Activity not found"));

        if (!activity.getCreator().getId().equals(user.getId())) {
            throw new ForbiddenException("Only creator can complete activity");
        }

        activity.setStatus(ActivityStatus.COMPLETED);
        activityRepository.save(activity);
    }

    @Transactional(readOnly = true)
    public CursorPage<ActivityListItemResponse> list(ActivityFilterParams filters, CursorPageable pageable) {
        Specification<Activity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filters.city() != null && !filters.city().isBlank()) {
                predicates.add(cb.equal(root.get("city"), filters.city()));
            }
            if (filters.typeSlug() != null && !filters.typeSlug().isBlank()) {
                predicates.add(cb.equal(root.get("typeSlug"), filters.typeSlug()));
            }
            if (filters.status() != null) {
                predicates.add(cb.equal(root.get("status"), filters.status()));
            } else {
                predicates.add(cb.equal(root.get("status"), ActivityStatus.ACTIVE));
            }
            if (filters.fromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startsAt"), filters.fromDate()));
            }
            if (filters.toDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startsAt"), filters.toDate()));
            }

            if (pageable.hasCursor()) {
                String decoded = new String(Base64.getDecoder().decode(pageable.getCursor()));
                String[] parts = decoded.split("\\|");
                OffsetDateTime cursorTime = OffsetDateTime.parse(parts[0]);
                UUID cursorId = UUID.fromString(parts[1]);

                predicates.add(cb.or(
                    cb.greaterThan(root.get("startsAt"), cursorTime),
                    cb.and(
                        cb.equal(root.get("startsAt"), cursorTime),
                        cb.greaterThan(root.get("id"), cursorId)
                    )
                ));
            }

            query.orderBy(cb.asc(root.get("startsAt")), cb.asc(root.get("id")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Pageable limitedPage = PageRequest.of(0, pageable.getLimit() + 1);
        List<Activity> activities = activityRepository.findAll(spec, limitedPage).getContent();

        boolean hasNext = activities.size() > pageable.getLimit();
        List<Activity> resultList = hasNext ? activities.subList(0, pageable.getLimit()) : activities;

        String nextCursor = null;
        if (hasNext) {
            Activity last = resultList.get(resultList.size() - 1);
            String cursorRaw = last.getStartsAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) + "|" + last.getId();
            nextCursor = Base64.getEncoder().encodeToString(cursorRaw.getBytes());
        }

        List<ActivityListItemResponse> dtos = resultList.stream()
            .map(this::mapToListItemResponse)
            .collect(Collectors.toList());

        return new CursorPage<>(dtos, nextCursor);
    }

    @Transactional
    public int autoCompleteStartedActivities() {
        OffsetDateTime threshold = OffsetDateTime.now().minusHours(4);
        List<Activity> started = activityRepository.findAll((root, query, cb) ->
            cb.and(
                cb.equal(root.get("status"), ActivityStatus.ACTIVE),
                cb.lessThan(root.get("startsAt"), threshold)
            )
        );

        for (Activity a : started) {
            a.setStatus(ActivityStatus.COMPLETED);
        }
        activityRepository.saveAll(started);
        return started.size();
    }

    private String resolveImageUrl(Activity a) {
        if (a.getImageUrl() != null) {
            return a.getImageUrl();
        }
        return typeRegistry.get(a.getTypeSlug()).getDefaultImageUrl();
    }

    private List<ActivitySummaryField> buildSummary(Activity a) {
        ActivityTypeDefinition type = typeRegistry.get(a.getTypeSlug());
        Map<String, CustomFieldDefinition> fieldDefs = type.getFieldDefinitions().stream()
            .collect(Collectors.toMap(CustomFieldDefinition::name, d -> d));

        List<ActivitySummaryField> summary = new ArrayList<>();
        for (String fieldName : type.getSummaryFields()) {
            CustomFieldDefinition def = fieldDefs.get(fieldName);
            Object value = a.getCustomFields().get(fieldName);
            if (value == null) continue;

            String displayValue;
            if (def.type() == FieldType.NUMBER_WITH_UNIT && def.unit() != null) {
                displayValue = value + " " + def.unit().name().toLowerCase();
            } else {
                displayValue = value.toString();
            }

            summary.add(new ActivitySummaryField(fieldName, def.label(), value, displayValue));
        }
        return summary;
    }

    private ActivityListItemResponse mapToListItemResponse(Activity a) {
        long currentParticipants = participantService.countApproved(a.getId());
        List<String> participantAvatars = participantService.getTopParticipantAvatars(a.getId(), 3);
        ActivityTypeDefinition type = typeRegistry.get(a.getTypeSlug());

        return new ActivityListItemResponse(
            a.getId(),
            a.getTitle(),
            type.getName(),
            type.getSlug(),
            a.getCity(),
            a.getStartsAt(),
            a.getMaxParticipants(),
            (int) currentParticipants,
            a.getStatus(),
            a.getCreator().getDisplayName(),
            a.getCreator().getAvatarUrl(),
            a.getCustomFields(),
            resolveImageUrl(a),
            participantAvatars,
            buildSummary(a)
        );
    }

    private ActivityDetailResponse mapToDetailResponse(Activity a) {
        long currentParticipants = participantService.countApproved(a.getId());
        ActivityTypeDefinition type = typeRegistry.get(a.getTypeSlug());

        OffsetDateTime effectiveDeadline = a.getSignupDeadline() != null ? a.getSignupDeadline() : a.getStartsAt();
        boolean isJoinable = a.getStatus() == ActivityStatus.ACTIVE &&
                           OffsetDateTime.now().isBefore(effectiveDeadline) &&
                           currentParticipants < a.getMaxParticipants();

        return new ActivityDetailResponse(
            a.getId(),
            a.getTitle(),
            a.getDescription(),
            type.getName(),
            type.getSlug(),
            a.getCity(),
            a.getLocationLat(),
            a.getLocationLng(),
            a.getStartsAt(),
            a.getSignupDeadline(),
            a.getMaxParticipants(),
            (int) currentParticipants,
            a.getJoinPolicy(),
            a.getStatus(),
            a.getCustomFields(),
            resolveImageUrl(a),
            a.getCreator().getId(),
            a.getCreator().getDisplayName(),
            a.getCreator().getAvatarUrl(),
            isJoinable,
            effectiveDeadline
        );
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: rewrite ActivityService and ActivityTypeService for registry"
```

---

## Task 7: Delete old files

**Files to delete:**

- [ ] **Step 1: Delete all old validator files**

```bash
rm backend/src/main/java/ch/junto/activity/validation/ActivityFieldValidator.java \
   backend/src/main/java/ch/junto/activity/validation/ClimbingFieldValidator.java \
   backend/src/main/java/ch/junto/activity/validation/CyclingFieldValidator.java \
   backend/src/main/java/ch/junto/activity/validation/RunningFieldValidator.java \
   backend/src/main/java/ch/junto/activity/validation/SkiingFieldValidator.java \
   backend/src/main/java/ch/junto/activity/validation/YogaFieldValidator.java
```

- [ ] **Step 2: Delete old entity, repository, and dead model files**

```bash
rm backend/src/main/java/ch/junto/activitytype/entity/ActivityType.java \
   backend/src/main/java/ch/junto/activitytype/ActivityTypeRepository.java \
   backend/src/main/java/ch/junto/activitytype/model/CustomFieldDefinition.java \
   backend/src/main/java/ch/junto/activitytype/model/Option.java \
   backend/src/main/java/ch/junto/activitytype/model/ValidatorConfig.java \
   backend/src/main/java/ch/junto/activity/model/Option.java \
   backend/src/main/java/ch/junto/activity/model/ValidatorConfig.java \
   backend/src/main/java/ch/junto/activity/model/ValidatorType.java
```

- [ ] **Step 3: Delete replaced test files and fixture**

```bash
rm backend/src/test/java/ch/junto/activity/validation/GenericFieldValidatorTest.java \
   backend/src/test/java/ch/junto/activity/validation/ClimbingFieldValidatorTest.java \
   backend/src/testFixtures/java/ch/junto/fixtures/ActivityTypeFixture.java
```

- [ ] **Step 4: Commit**

```bash
git add -u
git commit -m "refactor: remove old validators, ActivityType entity, and dead code"
```

---

## Task 8: Update test fixtures and unit tests

**Files:**
- Modify: `testFixtures: fixtures/ActivityFixture.java`
- Modify: `test: activity/ActivityServiceTest.java`
- Modify: `test: activitytype/ActivityTypeServiceTest.java`

- [ ] **Step 1: Rewrite ActivityFixture**

Rewrite `backend/src/testFixtures/java/ch/junto/fixtures/ActivityFixture.java`:

```java
package ch.junto.fixtures;

import ch.junto.activity.entity.Activity;
import ch.junto.user.entity.User;
import ch.junto.activity.enums.ActivityStatus;
import ch.junto.activity.enums.JoinPolicy;
import ch.junto.activity.entity.CustomFields;
import java.time.OffsetDateTime;
import java.util.UUID;

public class ActivityFixture {

    public static Activity defaultActivity(User creator, String typeSlug) {
        Activity activity = newActivity(creator, typeSlug);
        activity.setId(UUID.randomUUID());
        return activity;
    }

    public static Activity newActivity(User creator, String typeSlug) {
        Activity activity = new Activity();
        activity.setCreator(creator);
        activity.setTypeSlug(typeSlug);
        activity.setTitle("Test Activity");
        activity.setDescription("Test Description");
        activity.setCity("Boulder");
        activity.setStartsAt(OffsetDateTime.now().plusDays(1));
        activity.setMaxParticipants(10);
        activity.setJoinPolicy(JoinPolicy.OPEN);
        activity.setStatus(ActivityStatus.ACTIVE);

        CustomFields fields = new CustomFields();
        if ("climbing".equals(typeSlug)) {
            fields.put("style", "Bouldering");
            fields.put("environment", "Indoor");
            fields.put("experienceLevel", "Beginner");
        }
        activity.setCustomFields(fields);

        return activity;
    }
}
```

- [ ] **Step 2: Rewrite ActivityServiceTest**

Rewrite `backend/src/test/java/ch/junto/activity/ActivityServiceTest.java`:

```java
package ch.junto.activity;

import ch.junto.activity.dto.ActivityCreateRequest;
import ch.junto.activity.dto.ActivityDetailResponse;
import ch.junto.activity.dto.ActivityUpdateRequest;
import ch.junto.activitytype.ActivityTypeRegistry;
import ch.junto.activitytype.definition.ClimbingType;
import ch.junto.fixtures.ActivityFixture;
import ch.junto.fixtures.UserFixture;
import ch.junto.activity.entity.Activity;
import ch.junto.user.entity.User;
import ch.junto.activity.enums.JoinPolicy;
import ch.junto.core.exception.NotFoundException;
import ch.junto.core.exception.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    @Mock private ActivityRepository activityRepository;
    @Mock private ActivityFieldValidationService validationService;
    @Mock private ch.junto.participant.ParticipantService participantService;

    private ActivityTypeRegistry typeRegistry;
    private ActivityService activityService;

    @BeforeEach
    void setUp() {
        typeRegistry = new ActivityTypeRegistry(List.of(new ClimbingType()));
        activityService = new ActivityService(activityRepository, typeRegistry, validationService, participantService);
    }

    @Test
    void create_validRequest_savesAndReturnsDetail() {
        User creator = UserFixture.defaultUser();
        ActivityCreateRequest request = new ActivityCreateRequest(
            "Test Title", "Test Desc", "climbing", "Boulder",
            null, null, OffsetDateTime.now().plusDays(1), null,
            10, JoinPolicy.OPEN, null
        );

        when(activityRepository.save(any(Activity.class))).thenAnswer(i -> {
            Activity a = i.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        ActivityDetailResponse response = activityService.create(request, creator);

        assertThat(response.title()).isEqualTo("Test Title");
        assertThat(response.activityTypeSlug()).isEqualTo("climbing");
        assertThat(response.imageUrl()).isNotBlank();
        verify(validationService).validate(eq("climbing"), any());
        verify(activityRepository).save(any());
    }

    @Test
    void create_unknownType_throwsNotFoundException() {
        User creator = UserFixture.defaultUser();
        ActivityCreateRequest request = new ActivityCreateRequest(
            "Test Title", "Test Desc", "unknown", "Boulder",
            null, null, OffsetDateTime.now().plusDays(1), null,
            10, JoinPolicy.OPEN, null
        );

        assertThatThrownBy(() -> activityService.create(request, creator))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void create_imageUrlFallsBackToTypeDefault() {
        User creator = UserFixture.defaultUser();
        ActivityCreateRequest request = new ActivityCreateRequest(
            "Test", "Desc", "climbing", "City",
            null, null, OffsetDateTime.now().plusDays(1), null,
            10, JoinPolicy.OPEN, null
        );

        when(activityRepository.save(any(Activity.class))).thenAnswer(i -> {
            Activity a = i.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        ActivityDetailResponse response = activityService.create(request, creator);
        assertThat(response.imageUrl()).isEqualTo(new ClimbingType().getDefaultImageUrl());
    }

    @Test
    void getDetail_found_returnsDetail() {
        User creator = UserFixture.defaultUser();
        Activity activity = ActivityFixture.defaultActivity(creator, "climbing");

        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));

        ActivityDetailResponse response = activityService.getDetail(activity.getId());

        assertThat(response.id()).isEqualTo(activity.getId());
        assertThat(response.title()).isEqualTo(activity.getTitle());
    }

    @Test
    void getDetail_notFound_throwsNotFoundException() {
        UUID id = UUID.randomUUID();
        when(activityRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> activityService.getDetail(id))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void update_byCreator_savesAndReturnsDetail() {
        User creator = UserFixture.defaultUser();
        Activity activity = ActivityFixture.defaultActivity(creator, "climbing");
        ActivityUpdateRequest request = new ActivityUpdateRequest(
            "Updated Title", "New Desc", "New City", null, null,
            OffsetDateTime.now().plusDays(2), null, null
        );

        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(activityRepository.save(any(Activity.class))).thenReturn(activity);

        ActivityDetailResponse response = activityService.update(activity.getId(), request, creator);

        assertThat(response.title()).isEqualTo("Updated Title");
        verify(activityRepository).save(activity);
    }

    @Test
    void update_notCreator_throwsForbiddenException() {
        User creator = UserFixture.defaultUser();
        User otherUser = UserFixture.defaultUser();
        otherUser.setId(UUID.randomUUID());

        Activity activity = ActivityFixture.defaultActivity(creator, "climbing");
        ActivityUpdateRequest request = new ActivityUpdateRequest(
            "Updated Title", "New Desc", "New City", null, null,
            OffsetDateTime.now().plusDays(2), null, null
        );

        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));

        assertThatThrownBy(() -> activityService.update(activity.getId(), request, otherUser))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void update_notFound_throwsNotFoundException() {
        UUID id = UUID.randomUUID();
        User user = UserFixture.defaultUser();
        ActivityUpdateRequest request = new ActivityUpdateRequest(
            "Updated Title", "New Desc", "New City", null, null,
            OffsetDateTime.now().plusDays(2), null, null
        );

        when(activityRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> activityService.update(id, request, user))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void cancel_notCreator_throwsForbiddenException() {
        User creator = UserFixture.defaultUser();
        User otherUser = UserFixture.defaultUser();
        otherUser.setId(UUID.randomUUID());
        Activity activity = ActivityFixture.defaultActivity(creator, "climbing");

        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));

        assertThatThrownBy(() -> activityService.cancel(activity.getId(), otherUser))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void cancel_notFound_throwsNotFoundException() {
        UUID id = UUID.randomUUID();
        when(activityRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> activityService.cancel(id, UserFixture.defaultUser()))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void complete_notCreator_throwsForbiddenException() {
        User creator = UserFixture.defaultUser();
        User otherUser = UserFixture.defaultUser();
        otherUser.setId(UUID.randomUUID());
        Activity activity = ActivityFixture.defaultActivity(creator, "climbing");

        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));

        assertThatThrownBy(() -> activityService.complete(activity.getId(), otherUser))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void complete_notFound_throwsNotFoundException() {
        UUID id = UUID.randomUUID();
        when(activityRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> activityService.complete(id, UserFixture.defaultUser()))
            .isInstanceOf(NotFoundException.class);
    }
}
```

- [ ] **Step 3: Rewrite ActivityTypeServiceTest**

Rewrite `backend/src/test/java/ch/junto/activitytype/ActivityTypeServiceTest.java`:

```java
package ch.junto.activitytype;

import ch.junto.activitytype.definition.ClimbingType;
import ch.junto.activitytype.definition.CyclingType;
import ch.junto.activitytype.dto.ActivityTypeResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityTypeServiceTest {

    @Test
    void listTypes_returnsAllRegisteredTypes() {
        var registry = new ActivityTypeRegistry(List.of(new ClimbingType(), new CyclingType()));
        var service = new ActivityTypeService(registry);

        List<ActivityTypeResponse> result = service.listTypes();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ActivityTypeResponse::slug)
            .containsExactlyInAnyOrder("climbing", "cycling");
    }

    @Test
    void listTypes_includesFieldDefinitionsAndSummaryFields() {
        var registry = new ActivityTypeRegistry(List.of(new ClimbingType()));
        var service = new ActivityTypeService(registry);

        List<ActivityTypeResponse> result = service.listTypes();

        assertThat(result).hasSize(1);
        var climbing = result.get(0);
        assertThat(climbing.customFields()).hasSize(5);
        assertThat(climbing.summaryFields()).containsExactly("style", "grade");
        assertThat(climbing.defaultImageUrl()).isNotBlank();
    }
}
```

- [ ] **Step 4: Run all unit tests**

Run: `./gradlew test --tests "ch.junto.activity.ActivityServiceTest" --tests "ch.junto.activitytype.ActivityTypeServiceTest" --tests "ch.junto.activitytype.ActivityTypeRegistryTest" --tests "ch.junto.activity.ActivityFieldValidationServiceTest" -p backend`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add -u
git add backend/src/testFixtures/java/ch/junto/fixtures/ActivityFixture.java \
      backend/src/test/java/ch/junto/activity/ActivityServiceTest.java \
      backend/src/test/java/ch/junto/activitytype/ActivityTypeServiceTest.java
git commit -m "refactor: update test fixtures and unit tests for registry architecture"
```

---

## Task 9: Update integration tests

**Files:**
- Modify: `test: activity/ActivityIntegrationTest.java`
- Check: `test: activity/ActivitySpatialTest.java`

- [ ] **Step 1: Check ActivitySpatialTest for ActivityType references**

Read `backend/src/test/java/ch/junto/activity/ActivitySpatialTest.java` and check if it uses `ActivityTypeFixture` or `ActivityType`. Update if needed to use `ActivityFixture.newActivity(user, "climbing")`.

- [ ] **Step 2: Rewrite ActivityIntegrationTest**

Rewrite `backend/src/test/java/ch/junto/activity/ActivityIntegrationTest.java`:

```java
package ch.junto.activity;

import ch.junto.activity.dto.ActivityCreateRequest;
import ch.junto.activity.dto.ActivityUpdateRequest;
import ch.junto.activity.entity.Activity;
import ch.junto.activity.entity.CustomFields;
import ch.junto.activity.enums.ActivityStatus;
import ch.junto.activity.enums.JoinPolicy;
import ch.junto.base.BaseIntegrationTest;
import ch.junto.fixtures.ActivityFixture;
import ch.junto.fixtures.UserFixture;
import ch.junto.helpers.DatabaseHelper;
import ch.junto.user.UserRepository;
import ch.junto.user.entity.User;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Activity Integration")
class ActivityIntegrationTest extends BaseIntegrationTest {

    @Autowired private ObjectMapper objectMapper;
    @Autowired private DatabaseHelper dbHelper;
    @Autowired private UserRepository userRepository;
    @Autowired private ActivityRepository activityRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = dbHelper.save(UserFixture.newUser("user-" + UUID.randomUUID() + "@junto.app"));
    }

    private String json(Object dto) {
        try { return objectMapper.writeValueAsString(dto); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    private MockHttpSession buildSession(User user) {
        MockHttpSession session = new MockHttpSession();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    @Test @DisplayName("should create activity successfully")
    void shouldCreateActivity() throws Exception {
        CustomFields customFields = new CustomFields();
        customFields.put("style", "Bouldering");
        customFields.put("grade", "V5");
        customFields.put("environment", "Indoor");
        customFields.put("experienceLevel", "Beginner");
        customFields.put("needsEquipment", true);

        ActivityCreateRequest request = new ActivityCreateRequest(
            "Integration Test Climbing", "A test description", "climbing", "Zurich",
            47.3769, 8.5417, OffsetDateTime.now().plusDays(2), OffsetDateTime.now().plusDays(1),
            5, JoinPolicy.OPEN, customFields
        );

        mockMvc.perform(post("/api/activities")
                .session(buildSession(testUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Integration Test Climbing"))
            .andExpect(jsonPath("$.activityTypeSlug").value("climbing"))
            .andExpect(jsonPath("$.imageUrl").isNotEmpty());

        List<Activity> activities = activityRepository.findAll();
        assertThat(activities).hasSize(1);
        assertThat(activities.get(0).getTitle()).isEqualTo("Integration Test Climbing");
        assertThat(activities.get(0).getTypeSlug()).isEqualTo("climbing");
    }

    @Test @DisplayName("should reject creation if unauthorized")
    void shouldRejectUnauthenticatedCreation() throws Exception {
        ActivityCreateRequest request = new ActivityCreateRequest(
            "Test", "Desc", "climbing", "City", null, null,
            OffsetDateTime.now().plusDays(1), null, 10, JoinPolicy.OPEN, null
        );

        mockMvc.perform(post("/api/activities")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test @DisplayName("should reject creation with invalid payload")
    void shouldRejectInvalidCreation() throws Exception {
        ActivityCreateRequest request = new ActivityCreateRequest(
            "", "Desc", "climbing", "", null, null,
            OffsetDateTime.now().plusDays(1), null, 10, JoinPolicy.OPEN, null
        );

        mockMvc.perform(post("/api/activities")
                .session(buildSession(testUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("should reject creation with invalid custom fields")
    void shouldRejectInvalidCustomFields() throws Exception {
        CustomFields customFields = new CustomFields();
        customFields.put("style", "InvalidStyle");
        customFields.put("environment", "Outdoor");
        customFields.put("experienceLevel", "Intermediate");

        ActivityCreateRequest request = new ActivityCreateRequest(
            "Test Climbing", "Desc", "climbing", "Zurich",
            null, null, OffsetDateTime.now().plusDays(1), null,
            5, JoinPolicy.OPEN, customFields
        );

        mockMvc.perform(post("/api/activities")
                .session(buildSession(testUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("should get activity detail")
    void shouldGetActivityDetail() throws Exception {
        Activity activity = dbHelper.save(ActivityFixture.newActivity(testUser, "climbing"));

        mockMvc.perform(get("/api/activities/" + activity.getId())
                .session(buildSession(testUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(activity.getId().toString()))
            .andExpect(jsonPath("$.title").value(activity.getTitle()))
            .andExpect(jsonPath("$.imageUrl").isNotEmpty());
    }

    @Test @DisplayName("should return 404 for non-existent activity")
    void shouldReturn404ForMissingActivity() throws Exception {
        mockMvc.perform(get("/api/activities/" + UUID.randomUUID())
                .session(buildSession(testUser)))
            .andExpect(status().isNotFound());
    }

    @Test @DisplayName("should update activity successfully")
    void shouldUpdateActivity() throws Exception {
        Activity activity = dbHelper.save(ActivityFixture.newActivity(testUser, "climbing"));

        ActivityUpdateRequest request = new ActivityUpdateRequest(
            "Updated Title", "Updated Desc", "Geneva", null, null,
            OffsetDateTime.now().plusDays(3), null, null
        );

        mockMvc.perform(put("/api/activities/" + activity.getId())
                .session(buildSession(testUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Updated Title"));

        Activity updated = activityRepository.findById(activity.getId()).orElseThrow();
        assertThat(updated.getTitle()).isEqualTo("Updated Title");
        assertThat(updated.getCity()).isEqualTo("Geneva");
    }

    @Test @DisplayName("should forbid update by non-creator")
    void shouldForbidUpdateByNonCreator() throws Exception {
        Activity activity = dbHelper.save(ActivityFixture.newActivity(testUser, "climbing"));
        User otherUser = dbHelper.save(UserFixture.newUser("other-" + UUID.randomUUID() + "@junto.app"));

        ActivityUpdateRequest request = new ActivityUpdateRequest(
            "Hacked Title", "Desc", "City", null, null,
            OffsetDateTime.now().plusDays(1), null, null
        );

        mockMvc.perform(put("/api/activities/" + activity.getId())
                .session(buildSession(otherUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isForbidden());
    }

    @Test @DisplayName("should cancel activity")
    void shouldCancelActivity() throws Exception {
        Activity activity = dbHelper.save(ActivityFixture.newActivity(testUser, "climbing"));

        mockMvc.perform(post("/api/activities/" + activity.getId() + "/cancel")
                .session(buildSession(testUser)))
            .andExpect(status().isOk());

        Activity cancelled = activityRepository.findById(activity.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(ActivityStatus.CANCELLED);
    }

    @Test @DisplayName("should complete activity")
    void shouldCompleteActivity() throws Exception {
        Activity activity = dbHelper.save(ActivityFixture.newActivity(testUser, "climbing"));

        mockMvc.perform(post("/api/activities/" + activity.getId() + "/complete")
                .session(buildSession(testUser)))
            .andExpect(status().isOk());

        Activity completed = activityRepository.findById(activity.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(ActivityStatus.COMPLETED);
    }

    @Test @DisplayName("should list activity types with field definitions")
    void shouldListActivityTypes() throws Exception {
        mockMvc.perform(get("/api/activity-types")
                .session(buildSession(testUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[?(@.slug == 'climbing')]").exists())
            .andExpect(jsonPath("$[?(@.slug == 'climbing')].customFields").isNotEmpty())
            .andExpect(jsonPath("$[?(@.slug == 'climbing')].summaryFields").isNotEmpty())
            .andExpect(jsonPath("$[?(@.slug == 'climbing')].defaultImageUrl").isNotEmpty());
    }
}
```

- [ ] **Step 3: Run full test suite**

Run: `./gradlew test -p backend`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add -u
git commit -m "refactor: update integration tests for registry architecture"
```

---

## Task 10: Add Swagger annotations

**Files:**
- Modify: `activity/ActivityController.java`
- Modify: `activitytype/ActivityTypeController.java`

- [ ] **Step 1: Add Swagger annotations to ActivityController**

Rewrite `backend/src/main/java/ch/junto/activity/ActivityController.java`:

```java
package ch.junto.activity;

import ch.junto.activity.dto.*;
import ch.junto.auth.CurrentUser;
import ch.junto.user.entity.User;
import ch.junto.core.pagination.CursorPage;
import ch.junto.core.pagination.CursorPageable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/activities")
@Tag(name = "Activities", description = "Create, list, update, and manage activities")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @PostMapping
    @Operation(summary = "Create activity", description = "Create a new activity. Custom fields are validated against the activity type's field definitions.")
    @ApiResponse(responseCode = "200", description = "Activity created")
    @ApiResponse(responseCode = "400", description = "Invalid request or custom field validation failed")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    public ActivityDetailResponse create(@RequestBody @Valid ActivityCreateRequest request, @CurrentUser User user) {
        return activityService.create(request, user);
    }

    @GetMapping
    @Operation(summary = "List activities", description = "List activities with optional filters and cursor-based pagination")
    @ApiResponse(responseCode = "200", description = "Page of activities")
    public CursorPage<ActivityListItemResponse> list(ActivityFilterParams filters, CursorPageable pageable) {
        return activityService.list(filters, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get activity detail")
    @ApiResponse(responseCode = "200", description = "Activity details")
    @ApiResponse(responseCode = "404", description = "Activity not found")
    public ActivityDetailResponse getDetail(@PathVariable UUID id) {
        return activityService.getDetail(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update activity", description = "Only the creator can update. Type cannot be changed.")
    @ApiResponse(responseCode = "200", description = "Activity updated")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Not the activity creator")
    @ApiResponse(responseCode = "404", description = "Activity not found")
    public ActivityDetailResponse update(
        @PathVariable UUID id,
        @RequestBody @Valid ActivityUpdateRequest request,
        @CurrentUser User user
    ) {
        return activityService.update(id, request, user);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel activity", description = "Only the creator can cancel.")
    @ApiResponse(responseCode = "200", description = "Activity cancelled")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Not the activity creator")
    @ApiResponse(responseCode = "404", description = "Activity not found")
    public void cancel(@PathVariable UUID id, @CurrentUser User user) {
        activityService.cancel(id, user);
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Complete activity", description = "Only the creator can complete.")
    @ApiResponse(responseCode = "200", description = "Activity completed")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Not the activity creator")
    @ApiResponse(responseCode = "404", description = "Activity not found")
    public void complete(@PathVariable UUID id, @CurrentUser User user) {
        activityService.complete(id, user);
    }
}
```

- [ ] **Step 2: Add Swagger annotations to ActivityTypeController**

Rewrite `backend/src/main/java/ch/junto/activitytype/ActivityTypeController.java`:

```java
package ch.junto.activitytype;

import ch.junto.activitytype.dto.ActivityTypeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/activity-types")
@Tag(name = "Activity Types", description = "Available activity types and their field definitions")
public class ActivityTypeController {

    private final ActivityTypeService activityTypeService;

    public ActivityTypeController(ActivityTypeService activityTypeService) {
        this.activityTypeService = activityTypeService;
    }

    @GetMapping
    @Operation(summary = "List activity types", description = "Returns all activity types with field definitions, summary field configuration, and default images")
    @ApiResponse(responseCode = "200", description = "List of activity types")
    public List<ActivityTypeResponse> list() {
        return activityTypeService.listTypes();
    }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew test -p backend`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/ch/junto/activity/ActivityController.java \
      backend/src/main/java/ch/junto/activitytype/ActivityTypeController.java
git commit -m "docs: add Swagger annotations to Activity and ActivityType controllers"
```

---

## Task 11: Final verification

- [ ] **Step 1: Run full test suite**

Run: `./gradlew test -p backend`
Expected: All tests PASS

- [ ] **Step 2: Verify no remaining references to deleted code**

```bash
grep -r "ActivityFieldValidator\b\|ActivityTypeRepository\|ActivityTypeFixture\|activityTypeId\|getSubtype\|getDifficultyLabel\|DRAFT\|activitytype\.dto\.CustomFieldDefinition" \
  backend/src/main/java/ backend/src/test/java/ backend/src/testFixtures/java/ \
  --include="*.java" -l
```

Expected: No matches

- [ ] **Step 3: Run the application**

Run: `./gradlew bootRun -p backend`
Expected: Application starts without errors. Swagger UI at `/swagger-ui.html` shows Activities and Activity Types tags with documented endpoints.

- [ ] **Step 4: Final commit if any fixups needed**
