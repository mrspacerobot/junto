# Activity & ActivityType — Design Issues

This document describes the current structural problems in the `activity` and `activitytype` packages of the Junto backend (Spring Boot / JPA). The goal is to get design recommendations for a clean rework.

---

## Context

Junto is a social activity-planning app. Users create **activities** (e.g. a Sunday cycling ride, a yoga session) which other users can join. Each activity has an **ActivityType** (cycling, running, climbing, skiing, yoga) that determines its custom metadata fields.

The stack is Java 21, Spring Boot 3, JPA/Hibernate, PostgreSQL.

---

## Issue 1: ActivityType is a DB entity but all real behavior is in code

### Current state

`ActivityType` is a JPA entity stored in the `activity_types` table:

```java
@Entity
@Table(name = "activity_types")
public class ActivityType {
    UUID id;
    String slug;       // e.g. "cycling", "climbing"
    String name;       // e.g. "Cycling", "Climbing"
    String iconName;
    boolean active;
    OffsetDateTime createdAt;
    OffsetDateTime updatedAt;
}
```

The `ActivityTypeController` exposes a single `GET /api/activity-types` endpoint that lists active types.

### The problem

Every piece of real behavior for a type — field definitions, validation rules, normalization logic — lives in code, not in the database. There are five Spring `@Component` validators:

- `ClimbingFieldValidator`
- `CyclingFieldValidator`
- `RunningFieldValidator`
- `SkiingFieldValidator`
- `YogaFieldValidator`

Each implements `ActivityFieldValidator`:

```java
public interface ActivityFieldValidator {
    String getActivityTypeSlug();   // must match the DB slug exactly
    List<CustomFieldDefinition> getFieldDefinitions();
    void validate(Map<String, Object> customFields);
    default void normalize(Activity activity) {}
}
```

The link between DB row and code behavior is a **string slug** that must match by convention. There is no compile-time enforcement. A typo or DB migration that renames a slug silently breaks validation — the service just skips validation for that type.

Adding a new sport requires:
1. A DB migration inserting a new row
2. A new Java `@Component` validator

The DB row contributes almost nothing except the `active` toggle and the display name/icon.

### Also: image URLs are hardcoded in ActivityService

The list response includes an `imageUrl` per activity. This is resolved in `ActivityService` via a raw switch on slug — not in the validator, not in the DB:

```java
String imageUrl = switch (a.getType().getSlug()) {
    case "cycling"  -> "https://images.unsplash.com/...";
    case "climbing" -> "https://images.unsplash.com/...";
    case "skiing"   -> "https://images.unsplash.com/...";
    case "running"  -> "https://images.unsplash.com/...";
    case "yoga"     -> "https://images.unsplash.com/...";
    default         -> "https://images.unsplash.com/...";
};
```

This must be manually kept in sync with the validator list and the DB rows.

---

## Issue 2: Two conflicting `CustomFieldDefinition` classes

There are two classes with the same name in different packages:

### `activitytype.dto.CustomFieldDefinition` (record — the one actually used)

```java
public record CustomFieldDefinition(
    String name,
    String label,
    FieldType type,
    boolean required,
    List<String> options,
    Unit unit
) { /* compact constructor with validation */ }
```

This is what all five validators instantiate and return from `getFieldDefinitions()`. It is also what `ActivityTypeResponse` exposes to the API.

### `activitytype.model.CustomFieldDefinition` (enum — dead/legacy code)

```java
public enum CustomFieldDefinition {
    STYLE("style", FieldType.TEXT, "Style", false, null, null, null, null, null),
    GRADE("grade", FieldType.TEXT, "Grade", false, null, null, null, null, null),
    ENVIRONMENT("environment", FieldType.SELECT, "Environment", false, null, null, null, null, null),
    EXPERIENCE_LEVEL("experienceLevel", FieldType.SELECT, "Experience Level", false, null, null, null, null, null),
    NEEDS_EQUIPMENT("needsEquipment", FieldType.BOOLEAN, "Needs Equipment", false, null, null, null, null, null);
    // 9-param constructor, fields for defaultValue, validationPattern, unit, validators, options
}
```

This enum is never referenced anywhere except its own file. It appears to be an abandoned design attempt that was never deleted. It has 9 constructor parameters (vs 6 in the record) and carries fields (`defaultValue`, `validationPattern`, `validators`) that don't exist in the record. Its constants describe only climbing-related fields and don't match the actual definitions used by the validators.

---

## Issue 3: Denormalized `subtype` and `difficultyLabel` on Activity

`Activity` has two columns that duplicate data already in `customFields`:

```java
@Column
private String subtype;          // e.g. "Bouldering" (from climbing.style)

@Column(name = "difficulty_label")
private String difficultyLabel;  // e.g. "6b" (from climbing.grade)
```

These are populated in `ClimbingFieldValidator.normalize()`:

```java
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
```

No other validator (running, cycling, skiing, yoga) overrides `normalize()`. So `subtype` and `difficultyLabel` are only populated for climbing and are otherwise null. The `ActivityDetailResponse` and `ActivityListItemResponse` do not expose these fields at all — they are never sent to the client. They appear to be unused DB columns that add write complexity (must be kept in sync with `customFields`) without providing value.

---

## Issue 4: ActivityStatus.DRAFT exists but is never used

The `ActivityStatus` enum includes `DRAFT`:

```java
public enum ActivityStatus {
    DRAFT,
    ACTIVE,
    CANCELLED,
    COMPLETED
}
```

Activities are always created with `status = ACTIVE` (the entity default). There is no creation flow that sets `DRAFT`. The list endpoint filters to `ACTIVE` by default. `DRAFT` is never referenced in any service, query, or test. It either represents an unimplemented feature or dead code.

---

## Summary of issues

| # | Issue | Impact |
|---|-------|--------|
| 1a | ActivityType DB entity adds a fragile string-slug coupling to code validators | Silent failures when slugs diverge |
| 1b | Image URLs hardcoded in `ActivityService` switch statement | Must update 3 places to add a new sport |
| 2 | Two `CustomFieldDefinition` classes with same name, different packages | Confusing imports, dead enum code |
| 3 | `subtype` and `difficultyLabel` denormalized on Activity, never exposed to API | Wasted writes, sync complexity |
| 4 | `ActivityStatus.DRAFT` never used | Dead code or undocumented intent |

---

## Questions for the design

1. Should `ActivityType` remain a DB entity or move fully to code (enum/config)?
2. If DB entity stays, should `imageUrl` be stored there too?
3. How should the link between an ActivityType and its validator be made type-safe?
4. Should `subtype`/`difficultyLabel` be removed, or are they intended for future use (e.g. filtering by subtype)?
5. Is `DRAFT` status intentional as a future feature, or should it be removed?
