# Spatial Queries Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable "Activities Near Me" by adding spatial filtering to the activity list endpoint, distance display in responses, and user location management endpoints.

**Architecture:** Extend the existing JPA Specification in `ActivityService.list()` with a PostGIS `ST_DWithin` predicate to filter activities by radius. Compute display distances in Java using Haversine. Add `PUT/DELETE /api/users/location` endpoints for stored user location. Location resolves per-request: explicit query params → stored user default → no spatial filtering.

**Tech Stack:** Spring Boot 4, JPA Criteria API, PostGIS `ST_DWithin`, Hibernate Spatial, TestContainers (PostGIS)

**Spec:** `docs/superpowers/specs/2026-04-25-spatial-queries-design.md`

---

### Task 1: Haversine Distance Utility

**Files:**
- Create: `backend/src/main/java/ch/junto/core/geo/Haversine.java`
- Create: `backend/src/test/java/ch/junto/core/geo/HaversineTest.java`

- [ ] **Step 1: Write the failing tests**

```java
package ch.junto.core.geo;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class HaversineTest {

    @Test
    void zurichToBern() {
        double distance = Haversine.distanceKm(47.3769, 8.5417, 46.9480, 7.4474);
        assertThat(distance).isBetween(95.0, 97.0);
    }

    @Test
    void samePoint() {
        double distance = Haversine.distanceKm(47.3769, 8.5417, 47.3769, 8.5417);
        assertThat(distance).isEqualTo(0.0);
    }

    @Test
    void antipodalPoints() {
        double distance = Haversine.distanceKm(0.0, 0.0, 0.0, 180.0);
        assertThat(distance).isBetween(20010.0, 20020.0);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "ch.junto.core.geo.HaversineTest" --info 2>&1 | tail -20`
Expected: FAIL — `Haversine` class does not exist

- [ ] **Step 3: Write minimal implementation**

```java
package ch.junto.core.geo;

public final class Haversine {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private Haversine() {}

    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(EARTH_RADIUS_KM * c * 10.0) / 10.0;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "ch.junto.core.geo.HaversineTest" --info 2>&1 | tail -20`
Expected: 3 tests PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/ch/junto/core/geo/Haversine.java backend/src/test/java/ch/junto/core/geo/HaversineTest.java
git commit -m "feat: add Haversine distance utility"
```

---

### Task 2: Register `ST_DWithin` as Hibernate Function

**Files:**
- Create: `backend/src/main/java/ch/junto/core/config/SpatialFunctionContributor.java`
- Modify: `backend/src/main/resources/META-INF/services/org.hibernate.boot.model.FunctionContributor`

This follows the exact same pattern as the existing `JsonbFunctionContributor` in `backend/src/main/java/ch/junto/core/config/JsonbFunctionContributor.java` which registers `jsonb_contains_op`.

- [ ] **Step 1: Create the function contributor**

```java
package ch.junto.core.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

/**
 * Registers PostGIS ST_DWithin as a named Hibernate function.
 *
 * Usage in Criteria API:
 *   cb.isTrue(cb.function("st_dwithin", Boolean.class, root.get("location"), cb.literal(point), cb.literal(meters)))
 *
 * Equivalent SQL: ST_DWithin(location, ST_SetSRID(ST_MakePoint(lng, lat), 4326)::geography, meters)
 */
public class SpatialFunctionContributor implements FunctionContributor {

    @Override
    public void contributeFunctions(FunctionContributions functionContributions) {
        functionContributions.getFunctionRegistry().registerPattern(
            "st_dwithin",
            "ST_DWithin(?1, ?2, ?3)",
            functionContributions.getTypeConfiguration()
                .getBasicTypeRegistry()
                .resolve(StandardBasicTypes.BOOLEAN)
        );
    }
}
```

- [ ] **Step 2: Register in META-INF services**

Append to `backend/src/main/resources/META-INF/services/org.hibernate.boot.model.FunctionContributor`:

```
ch.junto.core.config.JsonbFunctionContributor
ch.junto.core.config.SpatialFunctionContributor
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/ch/junto/core/config/SpatialFunctionContributor.java backend/src/main/resources/META-INF/services/org.hibernate.boot.model.FunctionContributor
git commit -m "feat: register ST_DWithin as Hibernate function"
```

---

### Task 3: Add Spatial Params to `ActivityFilterParams`

**Files:**
- Modify: `backend/src/main/java/ch/junto/activity/dto/ActivityFilterParams.java`

- [ ] **Step 1: Add lat, lng, radiusKm fields with hasLocation() helper**

Add to `ActivityFilterParams.java` after the existing `customFields` field:

```java
    private Double lat;
    private Double lng;
    private Double radiusKm;

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }

    public Double getRadiusKm() { return radiusKm; }
    public void setRadiusKm(Double radiusKm) { this.radiusKm = radiusKm; }

    public boolean hasLocation() {
        return lat != null && lng != null;
    }
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/ch/junto/activity/dto/ActivityFilterParams.java
git commit -m "feat: add spatial params to ActivityFilterParams"
```

---

### Task 4: Add `distance` to `ActivityListItemResponse`

**Files:**
- Modify: `backend/src/main/java/ch/junto/activity/dto/ActivityListItemResponse.java`

- [ ] **Step 1: Add nullable distance field to the record**

Replace the record definition:

```java
public record ActivityListItemResponse(
    UUID id,
    String title,
    String activityTypeName,
    String activityTypeSlug,
    String city,
    OffsetDateTime startsAt,
    int maxParticipants,
    int currentParticipants,
    ActivityStatus status,
    String creatorDisplayName,
    String creatorAvatarUrl,
    CustomFields customFields,
    String imageUrl,
    List<String> participantAvatars,
    List<ActivitySummaryField> summary,
    Double distance
) {}
```

- [ ] **Step 2: Fix existing `mapToListItemResponse` in `ActivityService` to pass `null` as distance**

In `backend/src/main/java/ch/junto/activity/ActivityService.java`, update `mapToListItemResponse`:

```java
    private ActivityListItemResponse mapToListItemResponse(Activity a) {
        return mapToListItemResponse(a, null);
    }

    private ActivityListItemResponse mapToListItemResponse(Activity a, Double distance) {
        long currentParticipants = participantService.countApproved(a.getId());
        List<String> participantAvatars = participantService.getTopParticipantAvatars(a.getId(), 3);
        ActivityTypeDefinition type = typeRegistry.get(a.getTypeSlug());

        return new ActivityListItemResponse(
            a.getId(),
            a.getTitle(),
            type.getName(),
            type.getSlug(),
            a.getCity(),
            a.getStartsAt(),
            a.getMaxParticipants(),
            (int) currentParticipants,
            a.getStatus(),
            a.getCreator().getDisplayName(),
            a.getCreator().getAvatarUrl(),
            a.getCustomFields(),
            resolveImageUrl(a),
            participantAvatars,
            buildSummary(a),
            distance
        );
    }
```

- [ ] **Step 3: Run existing tests to verify no regressions**

Run: `cd backend && ./gradlew test --info 2>&1 | tail -30`
Expected: All existing tests still PASS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/ch/junto/activity/dto/ActivityListItemResponse.java backend/src/main/java/ch/junto/activity/ActivityService.java
git commit -m "feat: add distance field to ActivityListItemResponse"
```

---

### Task 5: Spatial Filtering + Distance in `ActivityService.list()`

**Files:**
- Modify: `backend/src/main/java/ch/junto/activity/ActivityService.java`
- Modify: `backend/src/main/java/ch/junto/activity/ActivityController.java`

This is the core change. The `list()` method gains a `User` parameter for location fallback, validates spatial params, adds `ST_DWithin` to the Specification, and computes Haversine distances on results.

- [ ] **Step 1: Update `list()` method signature to accept optional user and resolve location**

In `ActivityService.java`, add this import at the top:

```java
import ch.junto.core.geo.Haversine;
```

Change the `list()` method signature and add location resolution + validation at the top of the method, before the Specification lambda:

```java
    @Transactional(readOnly = true)
    public CursorPage<ActivityListItemResponse> list(ActivityFilterParams filters, CursorPageable pageable, User currentUser) {
        // Resolve search origin: query params → stored user location → null
        Double searchLat = filters.getLat();
        Double searchLng = filters.getLng();

        if (searchLat == null && searchLng == null && currentUser != null) {
            searchLat = currentUser.getLocationLat();
            searchLng = currentUser.getLocationLng();
        }

        // Validate: both or neither
        if ((searchLat == null) != (searchLng == null)) {
            throw new ApiException("VALIDATION_ERROR", "lat and lng must both be provided or both absent", 400);
        }

        if (searchLat != null) {
            if (searchLat < -90 || searchLat > 90) {
                throw new ApiException("VALIDATION_ERROR", "lat must be between -90 and 90", 400);
            }
            if (searchLng < -180 || searchLng > 180) {
                throw new ApiException("VALIDATION_ERROR", "lng must be between -180 and 180", 400);
            }
        }

        double radiusKm = 25.0;
        if (filters.getRadiusKm() != null) {
            if (filters.getRadiusKm() <= 0 || filters.getRadiusKm() > 200) {
                throw new ApiException("VALIDATION_ERROR", "radiusKm must be between 0 (exclusive) and 200", 400);
            }
            radiusKm = filters.getRadiusKm();
        }

        final Double finalLat = searchLat;
        final Double finalLng = searchLng;
        final double radiusMeters = radiusKm * 1000.0;
```

- [ ] **Step 2: Add `ST_DWithin` predicate inside the Specification lambda**

Inside the `list()` method's Specification lambda, add the spatial predicate after the existing custom field predicates and before the cursor logic. Insert after the `// Note: jsonb_extract_path_text` comment block:

```java
            // Spatial filter: only activities within radius of search origin
            if (finalLat != null) {
                String pointWkt = "SRID=4326;POINT(" + finalLng + " " + finalLat + ")";
                predicates.add(cb.isTrue(
                    cb.function("st_dwithin", Boolean.class,
                        root.get("location"),
                        cb.literal(pointWkt),
                        cb.literal(radiusMeters)
                    )
                ));
                // Exclude activities without a location
                predicates.add(cb.isNotNull(root.get("location")));
            }
```

- [ ] **Step 3: Replace the mapping at the bottom of `list()` to compute distances**

Replace the mapping section (the `List<ActivityListItemResponse> dtos = ...` block) at the bottom of `list()`:

```java
        final Double distLat = finalLat;
        final Double distLng = finalLng;
        List<ActivityListItemResponse> dtos = resultList.stream()
            .map(a -> {
                Double distance = null;
                if (distLat != null && a.getLocationLat() != null && a.getLocationLng() != null) {
                    distance = Haversine.distanceKm(distLat, distLng, a.getLocationLat(), a.getLocationLng());
                }
                return mapToListItemResponse(a, distance);
            })
            .collect(Collectors.toList());
```

- [ ] **Step 4: Update `ActivityController.list()` to pass current user**

In `ActivityController.java`, update the `list` method to accept an optional `@CurrentUser`:

```java
    @GetMapping
    @Operation(summary = "List activities", description = "List activities with optional filters and cursor-based pagination")
    @ApiResponse(responseCode = "200", description = "Page of activities")
    public CursorPage<ActivityListItemResponse> list(ActivityFilterParams filters, CursorPageable pageable, @CurrentUser User user) {
        return activityService.list(filters, pageable, user);
    }
```

Note: The `@CurrentUser` resolver already returns `null` for unauthenticated requests (see `CurrentUserArgumentResolver.resolveArgument()`), and the list endpoint requires authentication per `SecurityConfig` (`anyRequest().authenticated()`), so `user` will always be non-null in production. But the service handles `null` gracefully for testability.

- [ ] **Step 5: Run existing tests to check for compilation**

Run: `cd backend && ./gradlew test --info 2>&1 | tail -30`

The `ActivityServiceTest` unit tests will fail because they call `activityService.list(filters, pageable)` with the old 2-arg signature. Those tests don't test `list()` directly though — check if any do. If so, update the call to pass `null` as the third argument.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/ch/junto/activity/ActivityService.java backend/src/main/java/ch/junto/activity/ActivityController.java
git commit -m "feat: add spatial filtering and distance to activity list"
```

---

### Task 6: `ST_DWithin` Pattern Fix — Use Raw SQL Point Literal

**Files:**
- Modify: `backend/src/main/java/ch/junto/core/config/SpatialFunctionContributor.java`

The `ST_DWithin` call needs the search point as a `geography` type. Since we pass the point as a WKT string literal, the registered function pattern must cast it. Update the pattern:

- [ ] **Step 1: Update the function pattern to cast the point argument**

```java
public class SpatialFunctionContributor implements FunctionContributor {

    @Override
    public void contributeFunctions(FunctionContributions functionContributions) {
        functionContributions.getFunctionRegistry().registerPattern(
            "st_dwithin",
            "ST_DWithin(?1, ?2::geography, ?3)",
            functionContributions.getTypeConfiguration()
                .getBasicTypeRegistry()
                .resolve(StandardBasicTypes.BOOLEAN)
        );
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/ch/junto/core/config/SpatialFunctionContributor.java
git commit -m "fix: cast point literal to geography in ST_DWithin pattern"
```

---

### Task 7: User Location Endpoints

**Files:**
- Modify: `backend/src/main/java/ch/junto/user/UserService.java`
- Modify: `backend/src/main/java/ch/junto/user/UserController.java`
- Modify: `backend/src/main/java/ch/junto/auth/dto/MeResponse.java`

- [ ] **Step 1: Add `locationLat` and `locationLng` to `MeResponse`**

```java
package ch.junto.auth.dto;

import ch.junto.user.entity.User;
import ch.junto.user.enums.UserRole;
import ch.junto.user.enums.UserStatus;

import java.util.UUID;

public record MeResponse(
    UUID id,
    String email,
    String displayName,
    String avatarUrl,
    String bio,
    String city,
    Double locationLat,
    Double locationLng,
    UserRole role,
    UserStatus status,
    boolean onboarded,
    int inviteCodesRemaining
) {
    public static MeResponse from(User user) {
        return new MeResponse(
            user.getId(),
            user.getEmail(),
            user.getDisplayName(),
            user.getAvatarUrl(),
            user.getBio(),
            user.getCity(),
            user.getLocationLat(),
            user.getLocationLng(),
            user.getRole(),
            user.getStatus(),
            user.isOnboarded(),
            user.getInviteCodesRemaining()
        );
    }
}
```

- [ ] **Step 2: Add `updateLocation` and `clearLocation` to `UserService`**

Add these methods to `UserService.java`:

```java
    @Transactional
    public User updateLocation(UUID userId, double lat, double lng) {
        User user = findByIdOrThrow(userId);
        user.setLocationLat(lat);
        user.setLocationLng(lng);
        return userRepository.save(user);
    }

    @Transactional
    public User clearLocation(UUID userId) {
        User user = findByIdOrThrow(userId);
        user.setLocationLat(null);
        user.setLocationLng(null);
        return userRepository.save(user);
    }
```

- [ ] **Step 3: Add location endpoints to `UserController`**

Add these to `UserController.java`:

```java
    public record UpdateLocationRequest(
        @jakarta.validation.constraints.NotNull Double lat,
        @jakarta.validation.constraints.NotNull Double lng
    ) {}

    @PutMapping("/location")
    public MeResponse updateLocation(@RequestBody @Valid UpdateLocationRequest request, @CurrentUser User user) {
        if (request.lat() < -90 || request.lat() > 90) {
            throw new ch.junto.core.exception.ApiException("VALIDATION_ERROR", "lat must be between -90 and 90", 400);
        }
        if (request.lng() < -180 || request.lng() > 180) {
            throw new ch.junto.core.exception.ApiException("VALIDATION_ERROR", "lng must be between -180 and 180", 400);
        }
        return MeResponse.from(userService.updateLocation(user.getId(), request.lat(), request.lng()));
    }

    @DeleteMapping("/location")
    public MeResponse clearLocation(@CurrentUser User user) {
        return MeResponse.from(userService.clearLocation(user.getId()));
    }
```

- [ ] **Step 4: Run existing tests**

Run: `cd backend && ./gradlew test --info 2>&1 | tail -30`
Expected: All tests PASS (the `MeResponse.from()` call already receives the right data since `User` entity already has `locationLat`/`locationLng` fields)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/ch/junto/auth/dto/MeResponse.java backend/src/main/java/ch/junto/user/UserService.java backend/src/main/java/ch/junto/user/UserController.java
git commit -m "feat: add user location endpoints and location in MeResponse"
```

---

### Task 8: User Location Integration Tests

**Files:**
- Create: `backend/src/test/java/ch/junto/user/UserLocationIntegrationTest.java`

- [ ] **Step 1: Write integration tests**

```java
package ch.junto.user;

import ch.junto.base.BaseIntegrationTest;
import ch.junto.fixtures.UserFixture;
import ch.junto.helpers.DatabaseHelper;
import ch.junto.user.entity.User;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("User Location Integration")
class UserLocationIntegrationTest extends BaseIntegrationTest {

    @Autowired private ObjectMapper objectMapper;
    @Autowired private DatabaseHelper dbHelper;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = dbHelper.save(UserFixture.newUser("loc-" + UUID.randomUUID() + "@junto.app"));
    }

    private MockHttpSession buildSession(User user) {
        MockHttpSession session = new MockHttpSession();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    @Test @DisplayName("PUT /location sets user location")
    void shouldSetUserLocation() throws Exception {
        mockMvc.perform(put("/api/users/location")
                .session(buildSession(testUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lat\": 47.3769, \"lng\": 8.5417}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.locationLat").value(47.3769))
            .andExpect(jsonPath("$.locationLng").value(8.5417));
    }

    @Test @DisplayName("PUT /location with invalid coordinates returns 400")
    void shouldRejectInvalidCoordinates() throws Exception {
        mockMvc.perform(put("/api/users/location")
                .session(buildSession(testUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lat\": 100.0, \"lng\": 8.5417}"))
            .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("PUT /location unauthenticated returns 401")
    void shouldRejectUnauthenticatedLocation() throws Exception {
        mockMvc.perform(put("/api/users/location")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lat\": 47.3769, \"lng\": 8.5417}"))
            .andExpect(status().isUnauthorized());
    }

    @Test @DisplayName("DELETE /location clears user location")
    void shouldClearUserLocation() throws Exception {
        // Set location first
        mockMvc.perform(put("/api/users/location")
                .session(buildSession(testUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lat\": 47.3769, \"lng\": 8.5417}"))
            .andExpect(status().isOk());

        // Clear it
        mockMvc.perform(delete("/api/users/location")
                .session(buildSession(testUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.locationLat").isEmpty())
            .andExpect(jsonPath("$.locationLng").isEmpty());
    }

    @Test @DisplayName("GET /profile includes location fields")
    void shouldIncludeLocationInProfile() throws Exception {
        // Set location
        mockMvc.perform(put("/api/users/location")
                .session(buildSession(testUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lat\": 47.3769, \"lng\": 8.5417}"))
            .andExpect(status().isOk());

        // Verify profile includes it
        mockMvc.perform(get("/api/users/profile")
                .session(buildSession(testUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.locationLat").value(47.3769))
            .andExpect(jsonPath("$.locationLng").value(8.5417));
    }
}
```

- [ ] **Step 2: Run the tests**

Run: `cd backend && ./gradlew test --tests "ch.junto.user.UserLocationIntegrationTest" --info 2>&1 | tail -30`
Expected: 5 tests PASS

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/ch/junto/user/UserLocationIntegrationTest.java
git commit -m "test: add user location integration tests"
```

---

### Task 9: Spatial Query Integration Tests

**Files:**
- Create: `backend/src/test/java/ch/junto/activity/ActivitySpatialQueryIntegrationTest.java`

These tests use real PostGIS via TestContainers. They seed activities at known Swiss city coordinates and verify filtering, distance computation, and validation.

Known distances (Haversine):
- Zurich (47.3769, 8.5417) → Bern (46.9480, 7.4474): ~96 km
- Zurich → Geneva (46.2044, 6.1432): ~224 km

- [ ] **Step 1: Write the integration test class**

```java
package ch.junto.activity;

import ch.junto.activity.entity.Activity;
import ch.junto.activity.enums.JoinPolicy;
import ch.junto.base.BaseIntegrationTest;
import ch.junto.fixtures.UserFixture;
import ch.junto.helpers.DatabaseHelper;
import ch.junto.user.UserRepository;
import ch.junto.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Activity Spatial Query Integration")
class ActivitySpatialQueryIntegrationTest extends BaseIntegrationTest {

    @Autowired private DatabaseHelper dbHelper;
    @Autowired private UserRepository userRepository;
    @Autowired private ActivityRepository activityRepository;

    private User testUser;

    // Zurich
    private static final double ZURICH_LAT = 47.3769;
    private static final double ZURICH_LNG = 8.5417;
    // Bern (~96km from Zurich)
    private static final double BERN_LAT = 46.9480;
    private static final double BERN_LNG = 7.4474;
    // Geneva (~224km from Zurich)
    private static final double GENEVA_LAT = 46.2044;
    private static final double GENEVA_LNG = 6.1432;

    @BeforeEach
    void setUp() {
        testUser = dbHelper.save(UserFixture.newUser("spatial-" + UUID.randomUUID() + "@junto.app"));
    }

    private MockHttpSession buildSession(User user) {
        MockHttpSession session = new MockHttpSession();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private Activity createActivityAt(String title, double lat, double lng, OffsetDateTime startsAt) {
        Activity a = new Activity();
        a.setCreator(testUser);
        a.setTypeSlug("climbing");
        a.setTitle(title);
        a.setDescription("Test");
        a.setCity("Test City");
        a.setLocationLat(lat);
        a.setLocationLng(lng);
        a.setStartsAt(startsAt);
        a.setMaxParticipants(10);
        a.setJoinPolicy(JoinPolicy.OPEN);
        a.getCustomFields().put("style", "Bouldering");
        a.getCustomFields().put("environment", "Indoor");
        a.getCustomFields().put("experienceLevel", "Beginner");
        return dbHelper.save(a);
    }

    private Activity createActivityWithoutLocation(String title, OffsetDateTime startsAt) {
        Activity a = new Activity();
        a.setCreator(testUser);
        a.setTypeSlug("climbing");
        a.setTitle(title);
        a.setDescription("Test");
        a.setCity("Test City");
        a.setStartsAt(startsAt);
        a.setMaxParticipants(10);
        a.setJoinPolicy(JoinPolicy.OPEN);
        a.getCustomFields().put("style", "Bouldering");
        a.getCustomFields().put("environment", "Indoor");
        a.getCustomFields().put("experienceLevel", "Beginner");
        return dbHelper.save(a);
    }

    @Test @DisplayName("default radius (25km) returns only nearby activity")
    void shouldFilterByDefaultRadius() throws Exception {
        OffsetDateTime future = OffsetDateTime.now().plusDays(1);
        createActivityAt("Zurich Climb", ZURICH_LAT, ZURICH_LNG, future);
        createActivityAt("Bern Climb", BERN_LAT, BERN_LNG, future.plusHours(1));

        mockMvc.perform(get("/api/activities")
                .session(buildSession(testUser))
                .param("lat", String.valueOf(ZURICH_LAT))
                .param("lng", String.valueOf(ZURICH_LNG)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].title").value("Zurich Climb"));
    }

    @Test @DisplayName("100km radius includes Zurich and Bern, not Geneva")
    void shouldFilterByLargeRadius() throws Exception {
        OffsetDateTime future = OffsetDateTime.now().plusDays(1);
        createActivityAt("Zurich Climb", ZURICH_LAT, ZURICH_LNG, future);
        createActivityAt("Bern Climb", BERN_LAT, BERN_LNG, future.plusHours(1));
        createActivityAt("Geneva Climb", GENEVA_LAT, GENEVA_LNG, future.plusHours(2));

        mockMvc.perform(get("/api/activities")
                .session(buildSession(testUser))
                .param("lat", String.valueOf(ZURICH_LAT))
                .param("lng", String.valueOf(ZURICH_LNG))
                .param("radiusKm", "100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test @DisplayName("distance field is populated and accurate")
    void shouldReturnAccurateDistance() throws Exception {
        OffsetDateTime future = OffsetDateTime.now().plusDays(1);
        createActivityAt("Bern Climb", BERN_LAT, BERN_LNG, future);

        mockMvc.perform(get("/api/activities")
                .session(buildSession(testUser))
                .param("lat", String.valueOf(ZURICH_LAT))
                .param("lng", String.valueOf(ZURICH_LNG))
                .param("radiusKm", "200"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].distance").isNumber())
            .andExpect(jsonPath("$.data[0].distance").value(org.hamcrest.Matchers.closeTo(96.0, 2.0)));
    }

    @Test @DisplayName("distance is null when no location provided")
    void shouldReturnNullDistanceWithoutLocation() throws Exception {
        OffsetDateTime future = OffsetDateTime.now().plusDays(1);
        createActivityAt("Zurich Climb", ZURICH_LAT, ZURICH_LNG, future);

        mockMvc.perform(get("/api/activities")
                .session(buildSession(testUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].distance").isEmpty());
    }

    @Test @DisplayName("activities without location excluded from spatial results")
    void shouldExcludeActivitiesWithoutLocation() throws Exception {
        OffsetDateTime future = OffsetDateTime.now().plusDays(1);
        createActivityAt("Zurich Climb", ZURICH_LAT, ZURICH_LNG, future);
        createActivityWithoutLocation("No Location Climb", future.plusHours(1));

        // With spatial filter: only Zurich
        mockMvc.perform(get("/api/activities")
                .session(buildSession(testUser))
                .param("lat", String.valueOf(ZURICH_LAT))
                .param("lng", String.valueOf(ZURICH_LNG))
                .param("radiusKm", "200"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].title").value("Zurich Climb"));

        // Without spatial filter: both
        mockMvc.perform(get("/api/activities")
                .session(buildSession(testUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test @DisplayName("falls back to stored user location")
    void shouldFallbackToStoredUserLocation() throws Exception {
        testUser.setLocationLat(ZURICH_LAT);
        testUser.setLocationLng(ZURICH_LNG);
        userRepository.save(testUser);

        OffsetDateTime future = OffsetDateTime.now().plusDays(1);
        createActivityAt("Zurich Climb", ZURICH_LAT, ZURICH_LNG, future);
        createActivityAt("Geneva Climb", GENEVA_LAT, GENEVA_LNG, future.plusHours(1));

        // No lat/lng params — should use stored user location with default 25km radius
        mockMvc.perform(get("/api/activities")
                .session(buildSession(testUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].title").value("Zurich Climb"));
    }

    @Test @DisplayName("query params override stored user location")
    void shouldOverrideStoredLocation() throws Exception {
        // User is stored in Geneva
        testUser.setLocationLat(GENEVA_LAT);
        testUser.setLocationLng(GENEVA_LNG);
        userRepository.save(testUser);

        OffsetDateTime future = OffsetDateTime.now().plusDays(1);
        createActivityAt("Zurich Climb", ZURICH_LAT, ZURICH_LNG, future);
        createActivityAt("Geneva Climb", GENEVA_LAT, GENEVA_LNG, future.plusHours(1));

        // Override with Zurich coordinates
        mockMvc.perform(get("/api/activities")
                .session(buildSession(testUser))
                .param("lat", String.valueOf(ZURICH_LAT))
                .param("lng", String.valueOf(ZURICH_LNG)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].title").value("Zurich Climb"));
    }

    @Test @DisplayName("sort remains startsAt ASC regardless of distance")
    void shouldSortByStartsAtNotDistance() throws Exception {
        OffsetDateTime future = OffsetDateTime.now().plusDays(1);
        // Bern is farther but starts sooner
        createActivityAt("Bern Climb", BERN_LAT, BERN_LNG, future);
        createActivityAt("Zurich Climb", ZURICH_LAT, ZURICH_LNG, future.plusHours(2));

        mockMvc.perform(get("/api/activities")
                .session(buildSession(testUser))
                .param("lat", String.valueOf(ZURICH_LAT))
                .param("lng", String.valueOf(ZURICH_LNG))
                .param("radiusKm", "200"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].title").value("Bern Climb"))
            .andExpect(jsonPath("$.data[1].title").value("Zurich Climb"));
    }

    @Test @DisplayName("spatial filter composes with typeSlug filter")
    void shouldComposeWithTypeSlugFilter() throws Exception {
        OffsetDateTime future = OffsetDateTime.now().plusDays(1);
        createActivityAt("Zurich Climb", ZURICH_LAT, ZURICH_LNG, future);

        // Create a cycling activity at same location
        Activity cycling = new Activity();
        cycling.setCreator(testUser);
        cycling.setTypeSlug("cycling");
        cycling.setTitle("Zurich Ride");
        cycling.setDescription("Test");
        cycling.setCity("Zurich");
        cycling.setLocationLat(ZURICH_LAT);
        cycling.setLocationLng(ZURICH_LNG);
        cycling.setStartsAt(future.plusHours(1));
        cycling.setMaxParticipants(10);
        cycling.setJoinPolicy(JoinPolicy.OPEN);
        dbHelper.save(cycling);

        mockMvc.perform(get("/api/activities")
                .session(buildSession(testUser))
                .param("lat", String.valueOf(ZURICH_LAT))
                .param("lng", String.valueOf(ZURICH_LNG))
                .param("typeSlug", "climbing"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].title").value("Zurich Climb"));
    }

    @Test @DisplayName("partial lat/lng returns 400")
    void shouldRejectPartialLatLng() throws Exception {
        mockMvc.perform(get("/api/activities")
                .session(buildSession(testUser))
                .param("lat", "47.3769"))
            .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("invalid lat returns 400")
    void shouldRejectInvalidLat() throws Exception {
        mockMvc.perform(get("/api/activities")
                .session(buildSession(testUser))
                .param("lat", "91")
                .param("lng", "8.5"))
            .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("radiusKm exceeding max returns 400")
    void shouldRejectExcessiveRadius() throws Exception {
        mockMvc.perform(get("/api/activities")
                .session(buildSession(testUser))
                .param("lat", "47.3769")
                .param("lng", "8.5417")
                .param("radiusKm", "300"))
            .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("radiusKm zero or negative returns 400")
    void shouldRejectNonPositiveRadius() throws Exception {
        mockMvc.perform(get("/api/activities")
                .session(buildSession(testUser))
                .param("lat", "47.3769")
                .param("lng", "8.5417")
                .param("radiusKm", "0"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/activities")
                .session(buildSession(testUser))
                .param("lat", "47.3769")
                .param("lng", "8.5417")
                .param("radiusKm", "-5"))
            .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run the tests**

Run: `cd backend && ./gradlew test --tests "ch.junto.activity.ActivitySpatialQueryIntegrationTest" --info 2>&1 | tail -40`
Expected: 13 tests PASS

- [ ] **Step 3: Run the full test suite**

Run: `cd backend && ./gradlew test --info 2>&1 | tail -30`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/ch/junto/activity/ActivitySpatialQueryIntegrationTest.java
git commit -m "test: add spatial query integration tests"
```

---

### Task 10: Final Verification

- [ ] **Step 1: Run full test suite**

Run: `cd backend && ./gradlew test --info 2>&1 | tail -40`
Expected: All tests PASS — no regressions

- [ ] **Step 2: Verify the spec checklist**

Confirm each spec requirement is covered:
- ✅ `lat`/`lng`/`radiusKm` query params on GET /api/activities
- ✅ Location resolution: query params → stored user location → no filtering
- ✅ `distance` field in `ActivityListItemResponse` (null when no location)
- ✅ `ST_DWithin` spatial filtering with GIST index
- ✅ Haversine distance computation, rounded to 1 decimal
- ✅ Activities without location excluded from spatial results
- ✅ Sort remains `startsAt ASC`
- ✅ Validation: paired lat/lng, bounds, radiusKm range
- ✅ `PUT /api/users/location` with validation
- ✅ `DELETE /api/users/location`
- ✅ `MeResponse` includes `locationLat`/`locationLng`
