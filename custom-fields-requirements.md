# Custom Field Types — Requirements

## Context

Junto is a platform where users **propose activities for others to join** (not log completed activities). Each activity has a type (cycling, hiking, board game night, etc.), and each type defines its own custom fields describing what the activity involves.

## Current State

- 4 generic field types: `TEXT`, `NUMBER`, `SELECT`, `BOOLEAN`
- `NUMBER` is a bare number with no unit — displaying "45" is meaningless without knowing if it's km, miles, minutes, etc.
- Each activity type composes its own set of custom fields
- Frontend renders forms dynamically based on field definitions
- Backend stores custom field values as JSONB
- Validation happens per activity type via `ActivityFieldValidator`

## Problem

A bare number has no context. "45" could mean km, miles, minutes, hours, laps. Activity creators need to attach **units** to numeric fields so joiners understand what the activity involves.

## Requirements

1. **Activity creators define their own fields per activity type** — not developer-defined field types
2. **Units are fixed per field definition** (MVP) — e.g. a "distance" field is always km, not user-selectable at input time
3. **Units are curated** (not free text) to avoid inconsistency ("km" vs "kilometers" vs "kms")
4. **Different activity types have different fields** — cycling needs distance/duration, board games needs player count/skill level, hiking needs date range/difficulty, etc.
5. **Field types should be semantic** — each type has clear rendering rules and data shape
6. **Keep both NUMBER and NUMBER_WITH_UNIT** — some values are unitless (max participants, player count)

## Field Types

Replace current `TEXT`, `NUMBER`, `SELECT`, `BOOLEAN` with:

| Type | Stores | UI Rendering | Example Use |
|------|--------|-------------|-------------|
| `TEXT` | string | text input | Description, meeting point |
| `NUMBER` | number (decimal allowed) | number input | Max participants, player count |
| `NUMBER_WITH_UNIT` | number (decimal allowed, unit fixed in definition) | number input + static unit suffix | Distance (45 km), duration (2 hours), elevation (800 m) |
| `SELECT` | string from options | dropdown | Skill level, difficulty |
| `BOOLEAN` | boolean | checkbox | "Equipment provided?" |
| `TIME_OF_DAY` | local wall-clock time (HH:mm, not timezone-aware) | time picker | "Meet at 9:00 AM" |
| `DATE_RANGE` | start date + end date (same-day allowed) | two date inputs | "Multi-day hike, June 1-3" |

### Design decisions

- **TIME_OF_DAY**: local wall-clock time only, not timezone-aware. Activities already have a date and location, so "09:00" is unambiguous.
- **DATE_RANGE**: `end >= start` — same-day ranges are explicitly allowed.
- **NUMBER / NUMBER_WITH_UNIT**: accept decimal values (e.g. distance=2.5 km). Integer-only constraints (e.g. maxParticipants must be whole number) are enforced per activity type in validators, not by the field type itself.

## Curated Unit List (initial)

- Distance: `KM`, `MILES`, `METERS`
- Duration: `MINUTES`, `HOURS`
- Other: `LAPS`, `SETS`, `REPS`, `KG`, `LBS`

Each enum value has a display label:

```java
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

## Data Model

### CustomFieldDefinition

```java
public record CustomFieldDefinition(
    String name,
    String label,
    FieldType type,
    boolean required,
    List<String> options,    // only for SELECT, null/empty for others
    Unit unit                // only for NUMBER_WITH_UNIT, null for others
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

### Definition invariants

- `name` — required, non-blank, unique within an activity type, machine-readable format (e.g. `^[a-z][a-zA-Z0-9]*$` or `^[a-z0-9_]+$`)
- `label` — required, non-blank, human-readable
- `options` — normalized to `List.of()` for non-SELECT types
- `unit` — must be null for non-NUMBER_WITH_UNIT types

### FieldType enum

```java
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

## JSON Value Shapes (stored in Activity.customFields JSONB)

### TEXT
```json
{ "meetingPoint": "Main station" }
```

### NUMBER
```json
{ "maxParticipants": 8 }
```
Note: decimal values are allowed (e.g. 8.5). Integer-only constraints are per-activity-type.

### NUMBER_WITH_UNIT
```json
{ "distance": 45 }
```
Note: unit is stored in the field definition, not the value. The value is just a plain number.

### SELECT
```json
{ "difficulty": "INTERMEDIATE" }
```

### BOOLEAN
```json
{ "equipmentProvided": true }
```

### TIME_OF_DAY
```json
{ "meetTime": "09:00" }
```

### DATE_RANGE
```json
{ "tripDates": { "start": "2026-06-01", "end": "2026-06-03" } }
```

## Validation Rules

### Submitted value validation

| Type | Validation |
|------|-----------|
| `TEXT` | must be string, reject blank if required |
| `NUMBER` | must be numeric |
| `NUMBER_WITH_UNIT` | definition must include unit, value must be numeric |
| `SELECT` | value must be string, must exactly match one of `options` |
| `BOOLEAN` | must be boolean |
| `TIME_OF_DAY` | must be string in HH:mm format, validate via `LocalTime.parse()` |
| `DATE_RANGE` | object with `start` and `end` (both required if field present), parse as `LocalDate`, `end >= start` |

### General validation rules

- Reject unknown field names (not in the activity type's definitions)
- Reject missing required fields
- Ignore missing optional fields
- Present with `null` = invalid (no explicit nulls)
- Field-specific constraints (e.g. maxParticipants must be whole number >= 1, distance must be > 0) are enforced in per-activity-type validators, not by the field type system

## Frontend Rendering

In `ActivityForm.vue`:

- `TEXT`: text input
- `NUMBER`: number input
- `NUMBER_WITH_UNIT`: number input with static unit suffix (e.g. `[ 45 ] km`)
- `SELECT`: dropdown
- `BOOLEAN`: checkbox
- `TIME_OF_DAY`: `<input type="time">`
- `DATE_RANGE`: two date inputs (start + end)

No fancy date-range picker needed for MVP.

## Key Files to Modify

- `backend/src/main/java/ch/junto/activitytype/dto/CustomFieldDefinition.java` — update record with compact constructor validation
- `backend/src/main/java/ch/junto/activity/entity/CustomFields.java` — handle new value shapes
- `backend/src/main/java/ch/junto/activitytype/ActivityTypeService.java` — updated serialization
- `backend/src/main/java/ch/junto/activity/validation/ActivityFieldValidator.java` — new validation per type
- `frontend/src/components/activity/ActivityForm.vue` — new renderers per type

## New Files to Create

- `backend/src/main/java/ch/junto/activitytype/model/FieldType.java` — enum
- `backend/src/main/java/ch/junto/activitytype/model/Unit.java` — enum with display labels

## Example Definitions

### Cycling
```json
[
  {
    "name": "distance",
    "label": "Distance",
    "type": "NUMBER_WITH_UNIT",
    "required": true,
    "options": [],
    "unit": "KM"
  },
  {
    "name": "duration",
    "label": "Duration",
    "type": "NUMBER_WITH_UNIT",
    "required": false,
    "options": [],
    "unit": "HOURS"
  },
  {
    "name": "difficulty",
    "label": "Difficulty",
    "type": "SELECT",
    "required": true,
    "options": ["BEGINNER", "INTERMEDIATE", "ADVANCED"],
    "unit": null
  }
]
```

### Board game night
```json
[
  {
    "name": "playerCount",
    "label": "Player count",
    "type": "NUMBER",
    "required": true,
    "options": [],
    "unit": null
  },
  {
    "name": "skillLevel",
    "label": "Skill level",
    "type": "SELECT",
    "required": false,
    "options": ["CASUAL", "COMPETITIVE"],
    "unit": null
  },
  {
    "name": "bringSnacks",
    "label": "Bring snacks?",
    "type": "BOOLEAN",
    "required": false,
    "options": [],
    "unit": null
  }
]
```

### Hiking
```json
[
  {
    "name": "tripDates",
    "label": "Trip dates",
    "type": "DATE_RANGE",
    "required": true,
    "options": [],
    "unit": null
  },
  {
    "name": "meetTime",
    "label": "Meet time",
    "type": "TIME_OF_DAY",
    "required": true,
    "options": [],
    "unit": null
  },
  {
    "name": "elevationGain",
    "label": "Elevation gain",
    "type": "NUMBER_WITH_UNIT",
    "required": false,
    "options": [],
    "unit": "METERS"
  }
]
```

## Future Evolution (NOT in scope for MVP)

- `allowedUnits: List<Unit>` instead of fixed `unit` — let creators choose unit at input time
- `{ value, unit }` payload shape instead of plain number
- Unit conversion (e.g. display km as miles for different users)
- `placeholder: String?` and `helpText: String?` on CustomFieldDefinition
- Field-level constraints: `min`, `max`, `step`, `integerOnly`
- Name uniqueness validation within an activity type
