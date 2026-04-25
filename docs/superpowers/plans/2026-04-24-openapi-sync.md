# OpenAPI Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Regenerate the OpenAPI spec from the current backend, update frontend generated types, then produce a report and implementation spec documenting all needed frontend code changes.

**Architecture:** Run the springdoc gradle plugin against the current backend (with H2 in-memory DB), snapshot old generated types for diffing, regenerate frontend types via hey-api/openapi-ts, then combine TypeScript compilation errors and semantic type diffs to produce a prioritized change report and implementation spec.

**Tech Stack:** Spring Boot + springdoc-openapi-gradle-plugin, Vue 3 + TypeScript, @hey-api/openapi-ts, vue-tsc

---

## File Map

| File | Action | Purpose |
|------|--------|---------|
| `docs/openapi.json` | Overwrite | Regenerated from backend |
| `frontend/src/api/generated/types.gen.ts` | Overwrite | Regenerated types |
| `frontend/src/api/generated/sdk.gen.ts` | Overwrite | Regenerated SDK functions |
| `frontend/src/api/generated/index.ts` | Overwrite | Regenerated index |
| `/tmp/junto-openapi-snapshot/` | Create (temp) | Snapshot of old generated files |
| `docs/superpowers/specs/2026-04-24-openapi-sync-report.md` | Create | Analysis report |
| `docs/superpowers/specs/2026-04-24-openapi-sync-spec.md` | Create | Implementation spec |

---

## Task 1: Snapshot current generated types

**Files:**
- Read: `frontend/src/api/generated/types.gen.ts`
- Read: `frontend/src/api/generated/sdk.gen.ts`
- Read: `frontend/src/api/generated/index.ts`
- Create: `/tmp/junto-openapi-snapshot/` (temp, for diffing)

- [ ] **Step 1: Copy current generated files to temp snapshot**

```bash
mkdir -p /tmp/junto-openapi-snapshot
cp frontend/src/api/generated/types.gen.ts /tmp/junto-openapi-snapshot/types.gen.ts
cp frontend/src/api/generated/sdk.gen.ts /tmp/junto-openapi-snapshot/sdk.gen.ts
cp frontend/src/api/generated/index.ts /tmp/junto-openapi-snapshot/index.ts
echo "Snapshot saved to /tmp/junto-openapi-snapshot/"
ls -la /tmp/junto-openapi-snapshot/
```

Expected output: 3 files listed in the snapshot directory.

- [ ] **Step 2: Record current openapi.json endpoint count for verification**

```bash
cat docs/openapi.json | python3 -c "import json,sys; d=json.load(sys.stdin); paths=list(d.get('paths',{}).keys()); print(f'Current spec: {len(paths)} paths'); [print(' ', p) for p in sorted(paths)]"
```

Record this output — you'll compare it after regeneration to verify the spec changed.

---

## Task 2: Regenerate openapi.json from backend

**Files:**
- Modify: `docs/openapi.json`

The gradle plugin spins up the Spring Boot app with an H2 in-memory database, hits `http://localhost:8080/v3/api-docs`, and writes the result to `../docs/openapi.json`.

- [ ] **Step 1: Run the openapi gradle task from the backend directory**

```bash
cd backend && ./gradlew generateOpenApiDocs
```

This takes 30–60 seconds. Expected output ends with:
```
BUILD SUCCESSFUL in Xs
```

If it fails with port-already-in-use, kill the process on 8080 first:
```bash
lsof -ti:8080 | xargs kill -9
```

Then retry.

- [ ] **Step 2: Verify the spec was updated**

```bash
cat docs/openapi.json | python3 -c "import json,sys; d=json.load(sys.stdin); paths=list(d.get('paths',{}).keys()); print(f'New spec: {len(paths)} paths'); [print(' ', p) for p in sorted(paths)]"
```

Compare the path count and list to the snapshot from Task 1 Step 2. Note any additions or removals.

- [ ] **Step 3: Check spec info block for correctness**

```bash
cat docs/openapi.json | python3 -c "import json,sys; d=json.load(sys.stdin); info=d.get('info',{}); print('Title:', info.get('title')); print('Version:', info.get('version'))"
```

Expected: title and version are present and reasonable.

---

## Task 3: Regenerate frontend types

**Files:**
- Overwrite: `frontend/src/api/generated/types.gen.ts`
- Overwrite: `frontend/src/api/generated/sdk.gen.ts`
- Overwrite: `frontend/src/api/generated/index.ts`
- Overwrite: `frontend/src/api/generated/client.gen.ts`

- [ ] **Step 1: Run hey-api code generation**

```bash
cd frontend && npm run generate-api
```

Expected output ends with something like:
```
✓ Generated src/api/generated
```

If it fails, check that `docs/openapi.json` is valid JSON:
```bash
cat ../docs/openapi.json | python3 -m json.tool > /dev/null && echo "Valid JSON" || echo "INVALID JSON"
```

- [ ] **Step 2: Verify files were regenerated (timestamps updated)**

```bash
ls -la frontend/src/api/generated/*.ts frontend/src/api/generated/client/*.ts
```

All `.gen.ts` files should have fresh timestamps.

---

## Task 4: Diff old vs new generated types

**Files:**
- Read: `/tmp/junto-openapi-snapshot/types.gen.ts` (old)
- Read: `frontend/src/api/generated/types.gen.ts` (new)
- Read: `/tmp/junto-openapi-snapshot/sdk.gen.ts` (old)
- Read: `frontend/src/api/generated/sdk.gen.ts` (new)

- [ ] **Step 1: Diff types.gen.ts and record all changes**

```bash
diff /tmp/junto-openapi-snapshot/types.gen.ts frontend/src/api/generated/types.gen.ts
```

For each change, categorize it as:
- **New type** (entire type block added)
- **Removed type** (entire type block removed)
- **Modified type** (field added, removed, or changed within an existing type)
- **Enum change** (new/removed enum values)

Write these down — they feed directly into the report.

- [ ] **Step 2: Diff sdk.gen.ts and record all changes**

```bash
diff /tmp/junto-openapi-snapshot/sdk.gen.ts frontend/src/api/generated/sdk.gen.ts
```

For each change, categorize as:
- **New SDK function** (new endpoint exposed)
- **Removed SDK function** (endpoint removed)
- **Changed function signature** (different params or response types)

Write these down.

- [ ] **Step 3: Diff index.ts**

```bash
diff /tmp/junto-openapi-snapshot/index.ts frontend/src/api/generated/index.ts
```

Note any new or removed exports.

---

## Task 5: Run TypeScript type check

**Files:**
- Read: `frontend/src/` (all `.ts` and `.vue` files via vue-tsc)

- [ ] **Step 1: Run vue-tsc and capture all errors**

```bash
cd frontend && npx vue-tsc --noEmit 2>&1
```

Expected: either `0 errors` or a list of errors with file paths and line numbers.

For each error, record:
- File path
- Line number
- Error message
- Which generated type or SDK function it references

This is your definitive list of **breaking changes** (🔴 priority).

- [ ] **Step 2: If zero errors, explicitly confirm**

```bash
cd frontend && npx vue-tsc --noEmit 2>&1 | tail -5
```

If clean, note that there are no breaking TypeScript errors — the analysis will focus on silent regressions instead.

---

## Task 6: Analyze affected frontend files

**Files:**
- Read: `frontend/src/stores/auth.ts`
- Read: `frontend/src/stores/filters.ts`
- Read: `frontend/src/composables/useActivityTypes.ts`
- Read: `frontend/src/composables/usePagination.ts`
- Read: `frontend/src/composables/useCoordinates.ts`
- Read: `frontend/src/views/activities/ActivityFeedView.vue`
- Read: `frontend/src/views/activities/ActivityCreateView.vue`
- Read: `frontend/src/views/auth/LoginView.vue`
- Read: `frontend/src/views/auth/OnboardView.vue`
- Read: `frontend/src/views/profile/ProfileView.vue`

For each type or SDK change found in Task 4, find all frontend files that reference it.

- [ ] **Step 1: For each changed type, grep the frontend src**

For each type name that changed (e.g. `ActivityDetailResponse`, `MeResponse`, `ActivityTypeResponse`):

```bash
grep -rn "TypeNameHere" frontend/src/ --include="*.ts" --include="*.vue"
```

Record: file path, line number, how the type is used (property access, function argument, return type annotation).

- [ ] **Step 2: For each changed SDK function, grep the frontend src**

For each SDK function that changed (e.g. `getDetail`, `list`, `me`):

```bash
grep -rn "functionNameHere" frontend/src/ --include="*.ts" --include="*.vue"
```

Record: file path, line number, usage context.

- [ ] **Step 3: For each new type or SDK function, confirm it is NOT yet used**

New additions from the diff that are not referenced anywhere represent **silent regression opportunities** (🟡 priority) — the API exposes data the UI doesn't yet display.

```bash
grep -rn "NewTypeName\|newSdkFunction" frontend/src/ --include="*.ts" --include="*.vue"
```

If no results: confirm it's unused and note it.

- [ ] **Step 4: For each removed type or SDK function, confirm no stale references**

```bash
grep -rn "RemovedTypeName\|removedSdkFunction" frontend/src/ --include="*.ts" --include="*.vue"
```

If results: these are **dead code** (🟢 priority) to remove.

---

## Task 7: Write the analysis report

**Files:**
- Create: `docs/superpowers/specs/2026-04-24-openapi-sync-report.md`

- [ ] **Step 1: Write the report with all findings**

Using all data collected in Tasks 4–6, write the report. Use this structure exactly:

```markdown
# OpenAPI Sync Report — 2026-04-24

## 1. Endpoint Changes

| Endpoint | Change |
|----------|--------|
| `GET /api/example` | Added |
| `DELETE /api/example/{id}` | Removed |
| (list all changed endpoints from Task 2 Step 2 diff) |

## 2. Type Changes (types.gen.ts)

### Changed Types
For each modified type:
**`TypeName`**
- Added field: `fieldName: type`
- Removed field: `oldField`
- Enum change: `STATUS` now includes `NEW_VALUE`

### New Types
- `NewTypeName` — brief description of what it represents

### Removed Types
- `OldTypeName` — was used for X

## 3. SDK Changes (sdk.gen.ts)

### New Functions
- `functionName(options)` — wraps `GET /api/path`

### Removed Functions
- `oldFunction(options)` — was `GET /api/old-path`

### Changed Signatures
- `functionName` — param type changed from `OldType` to `NewType`

## 4. TypeScript Errors

(Paste full vue-tsc --noEmit output here, or "No TypeScript errors.")

## 5. Affected Frontend Files

For each affected file:

### `frontend/src/path/to/File.vue`
- **Uses:** `TypeName` at line N — field `x` no longer exists / new field `y` available
- **Priority:** 🔴 Breaking / 🟡 Silent regression / 🟢 Dead code

## 6. Priority Summary

### 🔴 Breaking Changes (fix before merge)
- [ ] File A:N — TypeScript error: ...

### 🟡 Silent Regressions (UI shows stale/incomplete data)
- [ ] File B — new field `x` available from API but not displayed

### 🟢 Dead Code (safe to remove)
- [ ] File C:N — references removed type `OldType`
```

Fill in all sections with the actual findings from Tasks 4–6. Do not leave any section empty — if there are no changes in a category, write "None."

- [ ] **Step 2: Commit the report**

```bash
git add docs/superpowers/specs/2026-04-24-openapi-sync-report.md docs/openapi.json frontend/src/api/generated/
git commit -m "feat: regenerate openapi spec and frontend types, add sync report"
```

---

## Task 8: Write the implementation spec

**Files:**
- Create: `docs/superpowers/specs/2026-04-24-openapi-sync-spec.md`

- [ ] **Step 1: Write the implementation spec**

Using the report from Task 7, write a spec for the frontend changes needed. Use this structure:

```markdown
# OpenAPI Sync — Frontend Implementation Spec

**Date:** 2026-04-24
**Source:** `docs/superpowers/specs/2026-04-24-openapi-sync-report.md`

## Scope

List which changes from the report will be implemented. Be explicit about what is out of scope and why (e.g. "new endpoint X is out of scope — no UI design yet").

## Change List

### `frontend/src/path/to/File.ts` (or .vue)

**Why:** [Reference the report finding — e.g. "TypeScript error at line N: field X removed"]
**Change:** 
- Remove reference to `oldField` on line N
- Replace with `newField` from updated `TypeName`

(Repeat for each file that needs changes)

## Out of Scope

- [Item]: [Reason]

## Testing Notes

After implementing all changes:
- [ ] `cd frontend && npx vue-tsc --noEmit` — must pass with 0 errors
- [ ] Manual: open ActivityFeedView and verify activities load
- [ ] Manual: open ActivityCreateView and verify form submits
- [ ] Manual: open ProfileView and verify user data renders
- [ ] Manual: run through login + onboard flow end to end
```

- [ ] **Step 2: Commit the spec**

```bash
git add docs/superpowers/specs/2026-04-24-openapi-sync-spec.md
git commit -m "docs: add openapi sync frontend implementation spec"
```

---

## Self-Review Checklist

After writing the plan, verify:

- [ ] Task 1 (snapshot) happens before Task 3 (regeneration) — yes, correct order
- [ ] Diff commands in Task 4 reference the snapshot correctly — `/tmp/junto-openapi-snapshot/` vs `frontend/src/api/generated/`
- [ ] vue-tsc command in Task 5 is run from `frontend/` directory — yes
- [ ] Report template in Task 7 covers all 6 sections from the design — yes
- [ ] Spec template in Task 8 includes testing notes — yes
- [ ] All commits staged correctly (includes both `docs/openapi.json` and `frontend/src/api/generated/`) — yes
