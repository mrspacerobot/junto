# ExploreView Map Panel — Design Spec

**Date:** 2026-04-26
**Branch:** feat/activity-lifecycle

## Goal

Replace the decorative SVG map in `ExploreView` with a real interactive Leaflet map that shows activity pins from live API data.

## Scope

- **Location:** `ExploreView.vue` only (the main map area)
- **Library:** Leaflet (direct, no Vue wrapper)
- **Tiles:** CartoDB Positron (light, muted, free, no API key)

## API Data

The backend returns `locationLat`, `locationLng`, and `activityTypeIconName` on:
- `ActivityListItemResponse` (used by ExploreView's activity list) — fields added as part of this implementation
- `ActivityDetailResponse` (already had lat/lng)

## Components

### `MapPanel.vue` (rewrite)

Single component handling the full Leaflet map. Props: `activities` array, optional `center` `[number, number]`.

- Initializes Leaflet map with CartoDB Positron tiles
- Renders `L.divIcon` markers for each activity that has coordinates
- Icon SVG resolved from build-time codegen map (`@/generated/icon-map`) keyed by `activityTypeIconName`
- Auto-fits map bounds to show all visible pins (with padding)
- Re-fits bounds when `activities` prop changes
- Custom zoom controls styled to match existing warm-border white buttons
- Popup on pin click: activity title, date/time, participant count, city — click navigates to detail
- Gradient placeholder when no activities have coordinates
- `rounded-xl overflow-hidden` wrapper (same as current)

### Icon Codegen Pipeline

Icons are resolved at build time, not runtime:

1. **`frontend/src/config/icon-names.json`** — Maps backend `iconName` → Lucide SVG filename (bridges naming mismatches like `"running"` → `"activity"`)
2. **`frontend/scripts/generate-icon-map.ts`** — Reads config, reads SVGs from `lucide-static`, generates TypeScript map
3. **`frontend/src/generated/icon-map.ts`** — Auto-generated, exports `iconMap` and `DEFAULT_ICON`

When a new activity type is added: add entry to `icon-names.json`, run `npm run generate`.

## Interactions

- **Zoom & pan:** Default Leaflet behavior + custom styled zoom buttons
- **Pin click:** Opens popup with activity summary
- **Filter integration:** Map pins update when filter pills change (same `items` array drives both list and map)
- **Popup click:** Navigates to `/activities/:id`

## Styling

- Map container: same `rounded-xl overflow-hidden border border-warm-300` as current
- Tiles: CartoDB Positron (light, warm gray/green tones)
- Markers: green circle (#3d5a1e) with white icon from codegen map
- Zoom controls: white background, warm border (#D0CDC6), warm text color (#6A6560)
- Popups: white card, rounded corners (12px), subtle shadow

## Dependencies

- `leaflet` — map rendering
- `@types/leaflet` (dev) — TypeScript types
- `lucide-static` (dev) — SVG source files for build-time codegen
- `tsx` (dev) — TypeScript execution for codegen script

## Files modified

- `backend/src/main/java/ch/junto/activity/dto/ActivityListItemResponse.java` — added `locationLat`, `locationLng`, `activityTypeIconName`
- `backend/src/main/java/ch/junto/activity/ActivityService.java` — pass new fields in mapping
- `frontend/src/components/map/MapPanel.vue` — rewrite with Leaflet
- `frontend/src/views/explore/ExploreView.vue` — wire MapPanel, remove decorative SVGs
- `frontend/package.json` — new dependencies and scripts

## Files created

- `frontend/src/config/icon-names.json` — icon name mapping config
- `frontend/scripts/generate-icon-map.ts` — codegen script
- `frontend/src/generated/icon-map.ts` — auto-generated icon map

## Out of scope

- ActivityFeedView sidebar map (stays as placeholder for now)
- Full-page dedicated map view
- Map clustering (not needed at current scale)
- User location / geolocation
- Map search / geocoding
