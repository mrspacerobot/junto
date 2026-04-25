# OpenAPI Sync Design

**Date:** 2026-04-24  
**Scope:** Regenerate OpenAPI spec from backend, update frontend generated types, analyze and document needed code changes

---

## Context

The backend has accumulated significant uncommitted changes (custom fields work, validation refactors, package restructuring). The frontend's generated API types in `src/api/generated/` are out of sync. The goal is to close that gap with a structured regeneration + analysis workflow.

The project already has the full pipeline wired:
- **Backend:** springdoc + `openapi-gradle-plugin` → `docs/openapi.json`
- **Frontend:** `@hey-api/openapi-ts` → `src/api/generated/` (`types.gen.ts`, `sdk.gen.ts`, `client.gen.ts`)

---

## Approach: Combined Diff + Type-check (Approach C)

Regenerate the spec, snapshot the old generated files for diffing, regenerate frontend types, then use both TypeScript errors and semantic diffs to produce a complete picture of what needs updating.

This is preferred over type-check-only (misses silent behavioral gaps) and diff-only (misses compilation regressions).

---

## Generation Pipeline

1. Run the springdoc gradle task (`generateOpenApiDocs`) against the current backend state (including all uncommitted changes), outputting to `docs/openapi.json`
2. Snapshot the old `src/api/generated/` files before overwriting
3. Run `npm run generate-api` in the frontend to regenerate `src/api/generated/`
4. Diff old vs new generated files

---

## Report

Written to `docs/superpowers/specs/2026-04-24-openapi-sync-report.md`. Covers:

1. **New/removed/changed endpoints** — what changed in the spec itself
2. **Type diffs** — changed types in `types.gen.ts` (new fields, removed fields, enum changes)
3. **SDK diffs** — changed function signatures in `sdk.gen.ts`
4. **TypeScript errors** — output of `vue-tsc --noEmit` after regeneration
5. **Affected frontend files** — for each change, which stores/composables/components reference that type or SDK call
6. **Priority ranking:**
   - 🔴 Breaking: TypeScript compilation errors
   - 🟡 Silent regressions: New fields present in API but unused in UI
   - 🟢 Dead code: Removed endpoints/types still referenced

---

## Implementation Spec

Written to `docs/superpowers/specs/2026-04-24-openapi-sync-spec.md`. Covers:

1. **Scope** — which frontend changes to make, derived from the report
2. **Change list** — per-file breakdown of what to add/update/remove
3. **Out of scope** — anything deferred
4. **Testing notes** — which views/flows to manually verify after changes

This spec feeds directly into a writing-plans implementation plan.

---

## Success Criteria

- `docs/openapi.json` reflects the current backend state
- `src/api/generated/` is regenerated from the new spec
- Report clearly identifies all frontend code that needs updating, prioritized
- Spec is specific enough to hand off to an implementation plan
