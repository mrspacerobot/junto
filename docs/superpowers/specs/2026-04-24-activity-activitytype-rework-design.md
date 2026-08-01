# Activity & ActivityType Rework Design

## Problem

The activity and activitytype packages have five structural issues:

1. `ActivityType` is a DB entity but all real behavior lives in code validators, coupled by a fragile string slug
2. Two conflicting `CustomFieldDefinition` classes (a live record, a dead enum)
3. Denormalized `subtype`/`difficultyLabel` columns on Activity that are never exposed to the API
4. Image URLs hardcoded in a switch statement in `ActivityService`
5. `ActivityStatus.DRAFT` is never used

## Design Decisions

- ActivityType moves fully to code (no DB entity). The app supports unlimited activity types.
- Activity stores a `typeSlug` string instead of a FK to `activity_types`.
- `typeSlug` is **immutable after creation** — changing an activity's type is not supported.
- Five per-sport validator classes consolidate into one generic validation service.
- Unknown custom fields are **rejected** (400 error).
- `subtype`/`difficultyLabel` are replaced by a "summary fields" concept — each type declares which custom fields to surface on cards. List responses include pre-formatted summary values.
- Creators can optionally set an image URL on an activity; otherwise the type's default image is used.
- `DRAFT` status is removed.
- Swagger annotations added to activity and activity type controllers.
- `CustomFieldDefinition` moves from `activitytype.dto` to `activitytype.definition` (it's schema metadata, not a DTO).

## Stability Contracts

Activity type slugs, field names, and field semantics are **durable identifiers** persisted in the `activity` table's `type_slug` and `custom_fields` columns. Once released:

- **Slugs** must be globally unique, match `^[a-z0-9-]+$`, and never be renamed without a data migration.
- **Field names** within a type must be unique and stable. Renaming or changing a field's type/semantics requires careful migration of existing JSONB data.
- **Select options** should remain backward-compatible. Adding options is safe; removing or renaming existing options may invalidate stored data.

---

## 1. ActivityTypeDefinition Interface

Each activity type is a `@Component` implementing:

```java
public interface ActivityTypeDefinition {
    /** Stable identifier persisted in activity.type_slug. Must match ^[a-z0-9-]+$ */
    String getSlug();
    String getName();
    String getIconName();
    /** Required. Used as fallback when activity has no custom image. */
    String getDefaultImageUrl();
    /** Field names must be unique within this list. */
    List<CustomFieldDefinition> getFieldDefinitions();
    /** Must reference field names from getFieldDefinitions() only. */
    List<String> getSummaryFields();
}
```

One class per sport (e.g. `ClimbingType`, `CyclingType`). Each class is pure data — field definitions, display metadata, and summary field declarations. No validation logic.

Adding a new sport = one new `@Component` class.

## 2. ActivityTypeRegistry

A `@Component` that collects all `ActivityTypeDefinition` beans at startup into a `Map<String, ActivityTypeDefinition>` keyed by slug.

**Startup validation** (fail fast if any check fails):

Per-type checks:
- slug is non-null, non-blank, matches `^[a-z0-9-]+$`
- name, iconName, defaultImageUrl are non-null and non-blank
- field definitions are non-null and non-empty
- field names are unique within the type
- summary fields reference only defined field names
- summary fields contain no duplicates

Global checks:
- no duplicate slugs across types

Exposes:
- `get(slug)` — returns definition or throws `NotFoundException`
- `listAll()` — returns all definitions **sorted by slug** for stable API ordering
- `exists(slug)` — boolean check

The `ActivityTypeController` and `ActivityTypeService` read from this registry instead of the database. `ActivityTypeRepository` is deleted.

## 3. Consolidated Validation

The five per-sport validator classes and the `ActivityFieldValidator` interface are deleted.

A single `ActivityFieldValidationService` handles all types:

```java
@Service
public class ActivityFieldValidationService {
    private final ActivityTypeRegistry registry;

    public void validate(String typeSlug, Map<String, Object> customFields) {
        ActivityTypeDefinition type = registry.get(typeSlug);
        validateFields(type.getFieldDefinitions(), customFields);
    }
}
```

Behavior:
- `customFields == null` → throws 400 ("Custom fields are required")
- Unknown field keys → throws 400 ("Unknown field: X")
- Missing required fields → throws 400
- Type-specific validation for TEXT, NUMBER, NUMBER_WITH_UNIT, SELECT, BOOLEAN, TIME_OF_DAY, DATE_RANGE

The `validateFields` method is the existing generic validation logic from `ActivityFieldValidator.validateFields()`.

## 4. Summary Fields Replace subtype/difficultyLabel

The `subtype` and `difficultyLabel` columns are dropped from the Activity table. The `normalize()` hook is removed.

Each `ActivityTypeDefinition` declares `getSummaryFields()` — an ordered list of custom field names to surface on activity cards:

- Cycling: `["distance"]` → "100 km"
- Climbing: `["style", "grade"]` → "Bouldering / V5"
- Running: `["distance"]` → "10 km"
- Skiing: `["level"]` → "Advanced"
- Yoga: `["style"]` → "Vinyasa"

**List responses include pre-formatted summary values** to avoid pushing formatting logic to the frontend:

```java
record ActivitySummaryField(
    String fieldName,
    String label,
    Object value,
    String displayValue  // e.g. "100 km", "Bouldering"
) {}
```

`ActivityListItemResponse` includes `List<ActivitySummaryField> summary` built by the service. The service resolves display values by looking up the field's unit/type from `CustomFieldDefinition` and formatting accordingly.

Filtering by custom field values (e.g. cycling > 50 km) works via JSONB queries against `customFields` directly. No denormalized columns needed.

## 5. Activity Entity Changes

```java
// REMOVED
@ManyToOne(fetch = LAZY)
private ActivityType type;
private String subtype;
private String difficultyLabel;

// ADDED
@Column(name = "type_slug", nullable = false)
private String typeSlug;

@Column(name = "image_url")
private String imageUrl;  // optional creator override
```

`typeSlug` is set at creation and never changed via update.

Image resolution in response mapping: use `activity.imageUrl` if set, otherwise `registry.get(typeSlug).getDefaultImageUrl()`.

## 6. ActivityCreateRequest Changes

```java
// BEFORE
public record ActivityCreateRequest(
    @NotBlank String title,
    String description,
    @NotNull UUID activityTypeId,
    ...
)

// AFTER
public record ActivityCreateRequest(
    @NotBlank String title,
    String description,
    @NotBlank String typeSlug,
    ...
)
```

`ActivityUpdateRequest` does **not** include `typeSlug` — type is immutable after creation.

## 7. Cleanup

| Item | Action |
|------|--------|
| `CustomFieldDefinition` enum (`activitytype.model`) | Delete |
| `ActivityStatus.DRAFT` | Remove from enum |
| `activity_types` DB table | Drop via migration |
| `subtype`, `difficulty_label` columns on `activity` | Drop via migration |
| `ClimbingFieldValidator` | Delete |
| `CyclingFieldValidator` | Delete |
| `RunningFieldValidator` | Delete |
| `SkiingFieldValidator` | Delete |
| `YogaFieldValidator` | Delete |
| `ActivityFieldValidator` interface | Delete |
| `ActivityType` entity | Delete |
| `ActivityTypeRepository` | Delete |
| Image URL switch in `ActivityService` | Delete |
| `normalize()` method and all callers | Delete |
| `activity.model.Option` | Delete |
| `activity.model.ValidatorConfig` | Delete |
| `activity.model.ValidatorType` | Delete |
| `activitytype.model.Option` | Delete |
| `activitytype.model.ValidatorConfig` | Delete |
| `ActivityTypeFixture` | Delete |

## 8. Package Structure

```
ch.junto.activity/
  ActivityController
  ActivityService
  ActivityRepository
  ActivityFieldValidationService
  entity/
    Activity
    CustomFields
  dto/
    ActivityCreateRequest
    ActivityUpdateRequest
    ActivityDetailResponse
    ActivityListItemResponse
    ActivitySummaryField
    ActivityFilterParams
  enums/
    ActivityStatus                     # ACTIVE, CANCELLED, COMPLETED
    JoinPolicy

ch.junto.activitytype/
  ActivityTypeController
  ActivityTypeRegistry
  ActivityTypeService
  dto/
    ActivityTypeResponse
  definition/
    ActivityTypeDefinition             # interface
    CustomFieldDefinition              # record (moved from dto/)
    ClimbingType
    CyclingType
    RunningType
    SkiingType
    YogaType
  model/
    FieldType
    Unit
```

## 9. DB Migration

Single Flyway migration (V015):

1. Add `type_slug VARCHAR(50)` column to `activities`
2. Populate from existing data: `UPDATE activities SET type_slug = (SELECT slug FROM activity_types WHERE id = activities.activity_type_id)`
3. Add `NOT NULL` constraint on `type_slug`
4. Add index on `type_slug`
5. Add `image_url VARCHAR(500)` column to `activities` (nullable)
6. Drop FK constraint on `activity_type_id`
7. Drop `activity_type_id` column
8. Drop `subtype` column
9. Drop `difficulty_label` column
10. Drop `activity_types` table

## 10. Test Plan

### Existing tests — adapted

| Test file | Changes |
|-----------|---------|
| `GenericFieldValidatorTest` | Replaced by `ActivityFieldValidationServiceTest`. Same test cases, calls service directly. |
| `ClimbingFieldValidatorTest` | Deleted. Climbing-specific tests move to `ActivityTypeRegistryTest` and `ActivityFieldValidationServiceTest`. |
| `ActivityServiceTest` | Mocks `ActivityTypeRegistry` and `ActivityFieldValidationService`. Uses `typeSlug` strings. Removes `getSubtype()` assertions. |
| `ActivityIntegrationTest` | No DB seeding for activity types. Uses `typeSlug`. Removes `getSubtype()` assertion. |
| `ActivityTypeServiceTest` | Tests registry-backed listing. |
| `CustomFieldDefinitionTest` | Updated import path (`definition` instead of `dto`). |
| `FieldTypeTest` | Unchanged. |
| `ActivitySpatialTest` | Updated fixture usage. |

### New tests

| Test | Type | Coverage |
|------|------|----------|
| `ActivityTypeRegistryTest` | Unit | All types registered, no duplicate slugs, valid definitions, summary fields reference actual field names, slug format validation, startup failure on duplicate slugs, startup failure on invalid summary field references |
| Per-type definition tests | Unit | Each type's field count, field types, required flags, options values |
| Invalid custom fields | Integration | `POST /api/activities` with wrong field types returns 400 |
| Cancel/complete flow | Integration | Full lifecycle status changes |
| Activity types endpoint | Integration | `GET /api/activity-types` returns all types with field definitions, summary fields, stable ordering |
| Image fallback | Unit | Activity with imageUrl uses it; without uses type default |
| Duplicate slug startup failure | Unit | Registry constructor throws on duplicate slugs |
| Summary field resolution | Unit | Service correctly formats summary values with units |

## 11. Swagger Annotations

Add OpenAPI annotations to `ActivityController` and `ActivityTypeController` using SpringDoc (already configured):

### ActivityController

- `@Tag(name = "Activities")` on class
- `@Operation` on each endpoint with summary and description
- `@ApiResponse` for each status code (200, 400, 401, 403, 404)
- `@Schema(example = ...)` on `typeSlug` and key DTO fields

Endpoints to annotate:
- `POST /api/activities` — Create activity
- `GET /api/activities` — List activities (with filter params documented)
- `GET /api/activities/{id}` — Get activity detail
- `PUT /api/activities/{id}` — Update activity
- `POST /api/activities/{id}/cancel` — Cancel activity
- `POST /api/activities/{id}/complete` — Complete activity

### ActivityTypeController

- `@Tag(name = "Activity Types")` on class
- `@Operation` on `GET /api/activity-types` — List available activity types with field definitions
