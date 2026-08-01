# Spatial Queries — "Activities Near Me"

## Context

Users need to discover activities happening near their location. The project already has PostGIS enabled with a `GEOGRAPHY(POINT, 4326)` column on activities (GIST-indexed), and users already have `location_lat`/`location_lng` fields. What's missing is the query pipeline to filter by radius and return distance.

## Design Decisions

| # | Decision | Choice | Rationale |
|---|----------|--------|-----------|
| 1 | Location input | Stored user default + per-request override | Stateless per-request for map UIs; stored default for quick browsing |
| 2 | Radius | Flexible query param | Default 25km, max 200km. Frontend can offer UI tiers while API stays flexible |
| 3 | Distance in response | Yes — `distance` field in km | Essential for UI display ("3.2 km away"). Nearly free since we're already doing spatial filtering |
| 4 | Sort order | Keep `startsAt ASC`; distance is display-only | Activity type/custom filters are primary concern per user requirement. Distance sort can be added later via query param |
| 5 | Technical approach | Extend JPA Specification with PostGIS `ST_DWithin` | Composable with existing filters, single query path, uses GIST index |

## Architecture

### Location Resolution

```
Request arrives
  ├─ Query params have lat/lng? → use those
  ├─ Authenticated user has stored location? → use that
  └─ Neither? → no spatial filtering, distance is null
```

Both `lat` and `lng` must be provided together — if only one is present, return 400.

### Spatial Filtering

Uses PostGIS `ST_DWithin(geography, geography, meters)` via JPA CriteriaBuilder `cb.function()`. The `location` column is already `GEOGRAPHY(POINT, 4326)` with a GIST index, so `ST_DWithin` will use the index for efficient radius scans.

Activities without a `location` set are excluded from results when spatial filtering is active.

### Distance Computation

Computed in Java using Haversine formula after fetching results. Why not PostGIS `ST_Distance` in the query? JPA Criteria API doesn't support returning computed columns alongside entities. Haversine is simple, accurate for display purposes, and avoids Tuple projection complexity.

Distance is rounded to one decimal place (e.g. 3.2 km).

## API Changes

### GET /api/activities (modified)

New query parameters:
- `lat` (Double) — latitude, overrides user's stored location
- `lng` (Double) — longitude, overrides user's stored location
- `radiusKm` (Double) — search radius in km, default 25, max 200

Validation:
- `lat` must be in range [-90, 90], `lng` must be in range [-180, 180]
- `lat` and `lng` must both be present or both absent
- `radiusKm` without a resolved location (no query params, no stored user location) is ignored
- `radiusKm` must be > 0 and <= 200 when provided

Response (`ActivityListItemResponse`) gains:
- `distance` (Double, nullable) — distance in km from search origin, null when no location is resolved

### PUT /api/users/location (new)

Request body:
```json
{ "lat": 47.3769, "lng": 8.5417 }
```

Validation:
- `lat` must be in range [-90, 90], `lng` must be in range [-180, 180]
- Both fields are required

Returns updated `MeResponse`.

### DELETE /api/users/location (new)

Clears the user's stored location. Returns updated `MeResponse` with null location fields.

### GET /api/users/profile (modified)

`MeResponse` gains `locationLat` and `locationLng` fields (both nullable).

## Files to Modify

| File | Change |
|------|--------|
| `activity/dto/ActivityFilterParams.java` | Add `lat`, `lng`, `radiusKm` fields + `hasLocation()` + validation |
| `activity/dto/ActivityListItemResponse.java` | Add `Double distance` field |
| `activity/ActivityService.java` | Location resolution, `ST_DWithin` predicate, Haversine utility |
| `activity/ActivityController.java` | Pass optional `@CurrentUser` to `list()` |
| `user/UserController.java` | Add `PUT /location` and `DELETE /location` endpoints |
| `user/UserService.java` | `updateLocation()` and `clearLocation()` methods |
| `auth/dto/MeResponse.java` | Add `locationLat`, `locationLng` |

No database migrations needed — all spatial infrastructure is in place.

## Testing

### Integration tests (`ActivitySpatialQueryIntegrationTest extends BaseIntegrationTest`)

Uses real PostGIS via TestContainers. Seed activities at known coordinates:
- Zurich (47.3769, 8.5417)
- Bern (46.9480, 7.4474) — ~96 km from Zurich
- Geneva (46.2044, 6.1432) — ~224 km from Zurich
- Activity with no location set

| # | Test | Verify |
|---|------|--------|
| 1 | Filter by explicit lat/lng with default radius | Only Zurich activity returned (Bern/Geneva outside 25km) |
| 2 | Filter with large radius includes farther activities | 100km radius returns Zurich + Bern, not Geneva |
| 3 | Distance field is populated and accurate | Zurich→Bern distance is ~96 km (±2 km tolerance) |
| 4 | Distance is null when no location provided | List without lat/lng returns all activities, distance is null |
| 5 | Activities without location excluded from spatial results | No-location activity absent when filtering, present when not |
| 6 | Fallback to stored user location | Set user location to Zurich, query without lat/lng params, verify spatial filtering applies |
| 7 | Query params override stored user location | User stored in Geneva, pass Zurich lat/lng, verify Zurich-relative results |
| 8 | Sort remains startsAt ASC regardless of distance | Seed activities with different dates, verify order |
| 9 | Spatial filter composes with existing filters | Combine `typeSlug=climbing` + lat/lng, verify both filters apply |
| 10 | Partial lat/lng returns 400 | Pass `lat` without `lng` |
| 11 | Invalid lat/lng returns 400 | lat=91, lng=200, etc. |
| 12 | radiusKm exceeding max returns 400 | radiusKm=300 |
| 13 | radiusKm=0 or negative returns 400 | radiusKm=-5 |

### Integration tests (`UserLocationIntegrationTest extends BaseIntegrationTest`)

| # | Test | Verify |
|---|------|--------|
| 1 | PUT /location sets user location | MeResponse contains updated lat/lng |
| 2 | PUT /location with invalid coordinates returns 400 | lat=100 rejected |
| 3 | PUT /location unauthenticated returns 401 | No session |
| 4 | DELETE /location clears user location | MeResponse has null lat/lng |
| 5 | GET /me includes location fields | locationLat and locationLng present in response |

### Unit tests (`HaversineTest`)

| # | Test | Verify |
|---|------|--------|
| 1 | Zurich to Bern | ≈ 96 km (±0.5 km) |
| 2 | Same point | 0.0 km |
| 3 | Antipodal points | ≈ 20,015 km (half circumference) |
