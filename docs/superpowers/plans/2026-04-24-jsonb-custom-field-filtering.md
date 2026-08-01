# JSONB Custom Field Filtering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add server-side filtering of activities by custom field values so `GET /api/activities?typeSlug=climbing&customFields[style]=Bouldering` returns only matching rows, with cursor-based pagination working correctly.

**Architecture:** The `custom_fields` JSONB column stays. Filtering uses PostgreSQL's `@>` containment operator (`custom_fields @> '{"style":"Bouldering"}'::jsonb`), which is supported by a GIN index and avoids the JPA Criteria API limitation with `jsonb_extract_path_text`. A one-time Hibernate `FunctionContributor` registers `@>` as the named function `jsonb_contains_op` so it can be used inside a `Specification`. `ActivityFilterParams` becomes a mutable class (not a record) so Spring MVC can bind `customFields[key]=value` query params into a `Map<String, String>`. Validation of field names against the type registry prevents arbitrary key injection. **Scope:** `SELECT` and `BOOLEAN` fields only in v1. `NUMBER_WITH_UNIT` and `DATE_RANGE` need range semantics — deferred.

**Tech Stack:** Java 21, Spring Boot 3, Hibernate 6, JPA Criteria API, PostgreSQL GIN index, Flyway

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `resources/db/migration/V016__add_gin_index_custom_fields.sql` | Create | GIN index on `custom_fields` |
| `core/config/JsonbFunctionContributor.java` | Create | Register `jsonb_contains_op` with Hibernate 6 |
| `resources/META-INF/services/org.hibernate.boot.model.FunctionContributor` | Create | SPI registration for `JsonbFunctionContributor` |
| `activity/dto/ActivityFilterParams.java` | Modify | Record → class, add `Map<String, String> customFields` |
| `activity/ActivityService.java` | Modify | Add custom field predicate to `list()` |
| `test: activity/ActivityIntegrationTest.java` | Modify | Add custom field filter tests |

---

## Task 1: Add GIN index migration

**Files:**
- Create: `backend/src/main/resources/db/migration/V016__add_gin_index_custom_fields.sql`

- [ ] **Step 1: Write the migration**

Create `backend/src/main/resources/db/migration/V016__add_gin_index_custom_fields.sql`:

```sql
-- GIN index enables the @> containment operator used for custom field filtering.
-- jsonb_extract_path_text does NOT use this index; @> does.
-- CONCURRENTLY is intentionally omitted: Flyway runs migrations in a transaction,
-- and CREATE INDEX CONCURRENTLY cannot run inside a transaction.
CREATE INDEX IF NOT EXISTS idx_activities_custom_fields_gin
    ON activities USING GIN (custom_fields);
```

- [ ] **Step 2: Run the migration**

```bash
./gradlew flywayMigrate -p backend
```
Expected: `Successfully applied 1 migration to schema "public"`

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/V016__add_gin_index_custom_fields.sql
git commit -m "feat: add GIN index on activities.custom_fields for @> containment queries"
```

---

## Task 2: Register jsonb_contains_op Hibernate function

**Files:**
- Create: `backend/src/main/java/ch/junto/core/config/JsonbFunctionContributor.java`
- Create: `backend/src/main/resources/META-INF/services/org.hibernate.boot.model.FunctionContributor`

The `@>` operator is not a SQL function, so JPA can't reference it by name. Hibernate 6's `FunctionContributor` SPI lets us register a named pattern `(?1 @> ?2::jsonb)` and call it as `jsonb_contains_op(col, literal)` from a `Specification`.

- [ ] **Step 1: Write JsonbFunctionContributor**

Create `backend/src/main/java/ch/junto/core/config/JsonbFunctionContributor.java`:

```java
package ch.junto.core.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

/**
 * Registers the PostgreSQL @> containment operator as a named Hibernate function.
 *
 * Usage in Criteria API:
 *   cb.isTrue(cb.function("jsonb_contains_op", Boolean.class, root.get("customFields"), cb.literal(json)))
 *
 * Equivalent SQL: custom_fields @> '<json>'::jsonb
 */
public class JsonbFunctionContributor implements FunctionContributor {

    @Override
    public void contributeFunctions(FunctionContributions functionContributions) {
        functionContributions.getFunctionRegistry().registerPattern(
            "jsonb_contains_op",
            "(?1 @> ?2::jsonb)",
            functionContributions.getTypeConfiguration()
                .getBasicTypeRegistry()
                .resolve(StandardBasicTypes.BOOLEAN)
        );
    }
}
```

- [ ] **Step 2: Register via Java SPI**

Create `backend/src/main/resources/META-INF/services/org.hibernate.boot.model.FunctionContributor`:

```
ch.junto.core.config.JsonbFunctionContributor
```

- [ ] **Step 3: Verify compilation and app startup**

```bash
./gradlew compileJava -p backend
./gradlew bootRun -p backend
```
Expected: compiles and starts without errors. No `FunctionContributor` or SPI warnings in logs.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/ch/junto/core/config/JsonbFunctionContributor.java \
      backend/src/main/resources/META-INF/services/org.hibernate.boot.model.FunctionContributor
git commit -m "feat: register jsonb_contains_op Hibernate function for @> containment queries"
```

---

## Task 3: Update ActivityFilterParams

**Files:**
- Modify: `backend/src/main/java/ch/junto/activity/dto/ActivityFilterParams.java`

Spring MVC can only bind `customFields[style]=Sport` into a `Map<String, String>` field when the enclosing object is a mutable bean with a setter — records don't have setters. Change `ActivityFilterParams` from a record to a class.

- [ ] **Step 1: Write the failing tests**

Add to `backend/src/test/java/ch/junto/activity/ActivityIntegrationTest.java` — inside the class, before the closing `}`:

```java
@Test @DisplayName("should filter activities by custom field value")
void shouldFilterByCustomField() throws Exception {
    Activity bouldering = dbHelper.save(ActivityFixture.newActivity(testUser, "climbing"));
    // ActivityFixture.newActivity sets style=Bouldering for climbing

    mockMvc.perform(get("/api/activities")
            .session(buildSession(testUser))
            .param("typeSlug", "climbing")
            .param("customFields[style]", "Bouldering"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(bouldering.getId().toString()));

    mockMvc.perform(get("/api/activities")
            .session(buildSession(testUser))
            .param("typeSlug", "climbing")
            .param("customFields[style]", "Sport"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());
}

@Test @DisplayName("should return 400 if customFields filter used without typeSlug")
void shouldRejectCustomFieldFilterWithoutTypeSlug() throws Exception {
    mockMvc.perform(get("/api/activities")
            .session(buildSession(testUser))
            .param("customFields[style]", "Bouldering"))
        .andExpect(status().isBadRequest());
}

@Test @DisplayName("should return 400 if customFields filter references unknown field")
void shouldRejectUnknownCustomFieldFilter() throws Exception {
    mockMvc.perform(get("/api/activities")
            .session(buildSession(testUser))
            .param("typeSlug", "climbing")
            .param("customFields[nonexistent]", "Value"))
        .andExpect(status().isBadRequest());
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./gradlew test -p backend \
  --tests "ch.junto.activity.ActivityIntegrationTest.shouldFilterByCustomField" \
  --tests "ch.junto.activity.ActivityIntegrationTest.shouldRejectCustomFieldFilterWithoutTypeSlug" \
  --tests "ch.junto.activity.ActivityIntegrationTest.shouldRejectUnknownCustomFieldFilter"
```
Expected: FAIL — `customFields` param is not bound because `ActivityFilterParams` is a record.

- [ ] **Step 3: Replace ActivityFilterParams with a mutable class**

Replace the full file `backend/src/main/java/ch/junto/activity/dto/ActivityFilterParams.java`:

```java
package ch.junto.activity.dto;

import ch.junto.activity.enums.ActivityStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Filter parameters for GET /api/activities.
 * Must be a mutable class (not a record) so Spring MVC can bind
 * customFields[key]=value query params into the Map field.
 */
public class ActivityFilterParams {

    private String city;

    @Schema(example = "cycling")
    private String typeSlug;

    private ActivityStatus status;
    private OffsetDateTime fromDate;
    private OffsetDateTime toDate;
    private Boolean joinable;

    /**
     * Custom field equality filters. Requires typeSlug to be set.
     * Only SELECT and BOOLEAN fields are supported in v1.
     * Example: customFields[style]=Bouldering
     */
    private Map<String, String> customFields;

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getTypeSlug() { return typeSlug; }
    public void setTypeSlug(String typeSlug) { this.typeSlug = typeSlug; }

    public ActivityStatus getStatus() { return status; }
    public void setStatus(ActivityStatus status) { this.status = status; }

    public OffsetDateTime getFromDate() { return fromDate; }
    public void setFromDate(OffsetDateTime fromDate) { this.fromDate = fromDate; }

    public OffsetDateTime getToDate() { return toDate; }
    public void setToDate(OffsetDateTime toDate) { this.toDate = toDate; }

    public Boolean getJoinable() { return joinable; }
    public void setJoinable(Boolean joinable) { this.joinable = joinable; }

    public Map<String, String> getCustomFields() { return customFields; }
    public void setCustomFields(Map<String, String> customFields) { this.customFields = customFields; }
}
```

- [ ] **Step 4: Fix accessor calls in ActivityService**

`ActivityService` calls filter params via record accessor syntax. Change every call to getter syntax:

| Old | New |
|-----|-----|
| `filters.city()` | `filters.getCity()` |
| `filters.typeSlug()` | `filters.getTypeSlug()` |
| `filters.status()` | `filters.getStatus()` |
| `filters.fromDate()` | `filters.getFromDate()` |
| `filters.toDate()` | `filters.getToDate()` |

```bash
./gradlew compileJava -p backend
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/ch/junto/activity/dto/ActivityFilterParams.java \
      backend/src/main/java/ch/junto/activity/ActivityService.java \
      backend/src/test/java/ch/junto/activity/ActivityIntegrationTest.java
git commit -m "refactor: convert ActivityFilterParams to mutable class, add customFields filter field"
```

---

## Task 4: Add custom field predicate to ActivityService

**Files:**
- Modify: `backend/src/main/java/ch/junto/activity/ActivityService.java`

- [ ] **Step 1: Add imports**

At the top of `ActivityService.java`, add:

```java
import ch.junto.activitytype.definition.CustomFieldDefinition;
import ch.junto.core.exception.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
```

- [ ] **Step 2: Inject ObjectMapper**

Add `ObjectMapper` to the constructor:

```java
private final ObjectMapper objectMapper;

public ActivityService(
    ActivityRepository activityRepository,
    ActivityTypeRegistry typeRegistry,
    ActivityFieldValidationService validationService,
    ParticipantService participantService,
    ObjectMapper objectMapper
) {
    this.activityRepository = activityRepository;
    this.typeRegistry = typeRegistry;
    this.validationService = validationService;
    this.participantService = participantService;
    this.objectMapper = objectMapper;
}
```

- [ ] **Step 3: Add the predicate block inside list()**

In `list()`, add this block after the `toDate` predicate and before the cursor block:

```java
// Custom field containment filter.
// Requires typeSlug so field names can be validated against the type registry.
// Uses PostgreSQL @> containment: custom_fields @> '{"style":"Bouldering"}'::jsonb
// The GIN index (idx_activities_custom_fields_gin) serves this operator.
if (filters.getCustomFields() != null && !filters.getCustomFields().isEmpty()) {
    if (filters.getTypeSlug() == null || filters.getTypeSlug().isBlank()) {
        throw new ApiException("VALIDATION_ERROR",
            "typeSlug is required when filtering by custom fields", 400);
    }
    ActivityTypeDefinition type = typeRegistry.get(filters.getTypeSlug());
    Set<String> allowedFieldNames = type.getFieldDefinitions().stream()
        .map(CustomFieldDefinition::name)
        .collect(Collectors.toSet());

    for (String key : filters.getCustomFields().keySet()) {
        if (!allowedFieldNames.contains(key)) {
            throw new ApiException("VALIDATION_ERROR",
                "Unknown filter field: " + key, 400);
        }
    }

    try {
        String jsonFilter = objectMapper.writeValueAsString(filters.getCustomFields());
        predicates.add(cb.isTrue(
            cb.function("jsonb_contains_op", Boolean.class,
                root.get("customFields"),
                cb.literal(jsonFilter)
            )
        ));
    } catch (JsonProcessingException e) {
        throw new RuntimeException("Failed to serialize custom field filter", e);
    }
}
```

- [ ] **Step 4: Run the three new tests**

```bash
./gradlew test -p backend \
  --tests "ch.junto.activity.ActivityIntegrationTest.shouldFilterByCustomField" \
  --tests "ch.junto.activity.ActivityIntegrationTest.shouldRejectCustomFieldFilterWithoutTypeSlug" \
  --tests "ch.junto.activity.ActivityIntegrationTest.shouldRejectUnknownCustomFieldFilter"
```
Expected: all 3 PASS.

- [ ] **Step 5: Run full test suite**

```bash
./gradlew test -p backend
```
Expected: ALL tests PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/ch/junto/activity/ActivityService.java
git commit -m "feat: add JSONB @> custom field filter predicate to activity list endpoint"
```

---

## Task 5: Final verification

- [ ] **Step 1: Run full test suite**

```bash
./gradlew test -p backend
```
Expected: ALL tests PASS.

- [ ] **Step 2: Manual smoke test**

```bash
./gradlew bootRun -p backend
```

```bash
# Should return only climbing activities where style = Bouldering
curl "http://localhost:8080/api/activities?typeSlug=climbing&customFields%5Bstyle%5D=Bouldering"

# Should return 400 — typeSlug missing
curl "http://localhost:8080/api/activities?customFields%5Bstyle%5D=Bouldering"
```

- [ ] **Step 3: Verify GIN index is used (optional, requires data)**

In psql:
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM activities
WHERE custom_fields @> '{"style":"Bouldering"}'::jsonb
  AND type_slug = 'climbing';
```
Expected: `Bitmap Index Scan on idx_activities_custom_fields_gin` in the plan once the table has enough rows to make the index cost-effective.
