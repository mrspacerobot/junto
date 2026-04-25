# OpenAPI Sync — Frontend Implementation Spec

**Date:** 2026-04-24
**Source:** `docs/superpowers/specs/2026-04-24-openapi-sync-report.md`

## Scope

This task captures the frontend alignment needed after OpenAPI schema regeneration. The backend API has been updated with new response fields, and the generated TypeScript types have been regenerated to reflect this. The frontend is already functionally in sync at runtime, but this spec ensures type definitions are properly aligned and documents a quality improvement opportunity.

## Change List

**No functional changes are required.** The frontend is already consuming the new fields at runtime:
- `ActivityListItemResponse` in `types.gen.ts` now includes three optional fields: `customFields`, `imageUrl`, and `participantAvatars`
- `ActivityCard.vue` is already using these fields (props typed as `any`)

**Status: Zero breaking changes, zero silent regressions**

## Quality Improvement (Optional but Recommended)

`frontend/src/components/activity/ActivityCard.vue` currently props the activity parameter as `any`. Now that `ActivityListItemResponse` is regenerated with all required fields, the prop type can be tightened from `any` to `ActivityListItemResponse` from the generated types.

**Benefits:**
- Catch future API drift at compile time via TypeScript strict mode
- Document exactly what fields the card expects
- Improve IDE autocomplete and refactoring support

**Implementation:**

In `frontend/src/components/activity/ActivityCard.vue`:

1. Add import at the top of the script block:
   ```typescript
   import type { ActivityListItemResponse } from '../../api/generated/types.gen'
   ```

2. Change the prop definition from:
   ```typescript
   activity: any
   ```
   to:
   ```typescript
   activity: ActivityListItemResponse
   ```

3. Verify compilation:
   ```bash
   cd /Users/dylan/projects/junto/frontend
   npx vue-tsc --noEmit
   ```
   - Must pass with 0 errors and 0 warnings

This is a low-risk, high-value improvement that takes ~2 minutes to implement and verify.

## Out of Scope

- Backend API changes (completed in separate task)
- Changes to non-Activity component types
- Runtime behavior modifications
- Additional feature development beyond type alignment

## Testing Notes

After implementing the quality improvement:

- [ ] `cd frontend && npx vue-tsc --noEmit` — must pass with 0 errors
- [ ] Manual: open ActivityFeedView and verify activities load and cards render correctly (image, avatars, custom fields all visible if present)
- [ ] Manual: open ActivityCreateView and verify form submits
- [ ] Manual: open ProfileView and verify user data renders
- [ ] Manual: run through login + onboard flow end to end

## Verification Checklist

- [ ] File written: `docs/superpowers/specs/2026-04-24-openapi-sync-spec.md`
- [ ] Committed with message: `docs: add openapi sync frontend implementation spec`
- [ ] Git status clean (no uncommitted spec changes)
