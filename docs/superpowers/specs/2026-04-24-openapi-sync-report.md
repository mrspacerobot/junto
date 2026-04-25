# OpenAPI Sync Report — 2026-04-24

## 1. Endpoint Changes

None. The spec still has 24 endpoints, identical set before and after regeneration.

## 2. Type Changes (types.gen.ts)

**Changes to `ActivityListItemResponse`:** 3 optional fields ADDED (purely additive):
- `customFields?: { empty?: boolean; [key: string]: { [key: string]: unknown } | boolean | undefined }`
- `imageUrl?: string`
- `participantAvatars?: Array<string>`

No types added or removed. No enum changes. All changes are additive only.

## 3. SDK Changes (sdk.gen.ts)

None.

## 4. TypeScript Errors

None. `vue-tsc --noEmit` is clean.

## 5. Affected Frontend Files

**`frontend/src/components/activity/ActivityCard.vue`** — already uses all 3 new fields at runtime (prop typed as `any`):
- `imageUrl` at line 89: `:src="activity.imageUrl"`
- `participantAvatars` at lines 60, 66, 67: `v-for`, length check, overflow count calculation
- `customFields` at lines 151, 155: guards and field value lookup

No stale references found anywhere. No breaking changes.

## 6. Priority Summary

**No 🔴 breaking changes.** All endpoint and SDK signatures remain unchanged.

**No 🟡 silent regressions.** All 3 new optional fields in `ActivityListItemResponse` are already consumed at runtime by `ActivityCard.vue`, so the component will immediately benefit from proper type safety.

**No 🟢 dead code.** All generated types and SDK functions have active consumers.

**Status:** Safe to ship. The updated spec aligns perfectly with existing component implementation.
