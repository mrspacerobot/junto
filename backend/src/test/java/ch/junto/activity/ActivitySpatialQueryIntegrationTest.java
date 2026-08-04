package ch.junto.activity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ch.junto.activity.entity.Activity;
import ch.junto.activity.enums.JoinPolicy;
import ch.junto.base.BaseIntegrationTest;
import ch.junto.fixtures.ActivityFixture;
import ch.junto.fixtures.UserFixture;
import ch.junto.helpers.DatabaseHelper;
import ch.junto.helpers.MockMvcTestHelper;
import ch.junto.user.UserRepository;
import ch.junto.user.entity.User;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

@DisplayName("Activity Spatial Query Integration")
class ActivitySpatialQueryIntegrationTest extends BaseIntegrationTest {

  @Autowired private DatabaseHelper dbHelper;
  @Autowired private UserRepository userRepository;
  @Autowired private ActivityRepository activityRepository;

  private User testUser;

  private static final double ZURICH_LAT = 47.3769;
  private static final double ZURICH_LNG = 8.5417;
  private static final double BERN_LAT = 46.9480;
  private static final double BERN_LNG = 7.4474;
  private static final double GENEVA_LAT = 46.2044;
  private static final double GENEVA_LNG = 6.1432;

  @BeforeEach
  void setUp() {
    testUser = dbHelper.save(UserFixture.newUser("spatial-" + UUID.randomUUID() + "@junto.app"));
  }

  private MockHttpSession buildSession(User user) {
    return MockMvcTestHelper.sessionFor(user);
  }

  private Activity createActivityAt(String title, double lat, double lng, OffsetDateTime startsAt) {
    return dbHelper.save(
        ActivityFixture.newActivityAt(testUser, "climbing", title, lat, lng, startsAt));
  }

  private Activity createActivityWithoutLocation(String title, OffsetDateTime startsAt) {
    return dbHelper.save(
        ActivityFixture.newActivityWithoutLocation(testUser, "climbing", title, startsAt));
  }

  @Test
  @DisplayName("default radius (25km) returns only nearby activity")
  void shouldFilterByDefaultRadius() throws Exception {
    OffsetDateTime future = OffsetDateTime.now().plusDays(1);
    createActivityAt("Zurich Climb", ZURICH_LAT, ZURICH_LNG, future);
    createActivityAt("Bern Climb", BERN_LAT, BERN_LNG, future.plusHours(1));

    mockMvc
        .perform(
            get("/api/activities")
                .session(buildSession(testUser))
                .param("lat", String.valueOf(ZURICH_LAT))
                .param("lng", String.valueOf(ZURICH_LNG)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].title").value("Zurich Climb"));
  }

  @Test
  @DisplayName("100km radius includes Zurich and Bern, not Geneva")
  void shouldFilterByLargeRadius() throws Exception {
    OffsetDateTime future = OffsetDateTime.now().plusDays(1);
    createActivityAt("Zurich Climb", ZURICH_LAT, ZURICH_LNG, future);
    createActivityAt("Bern Climb", BERN_LAT, BERN_LNG, future.plusHours(1));
    createActivityAt("Geneva Climb", GENEVA_LAT, GENEVA_LNG, future.plusHours(2));

    mockMvc
        .perform(
            get("/api/activities")
                .session(buildSession(testUser))
                .param("lat", String.valueOf(ZURICH_LAT))
                .param("lng", String.valueOf(ZURICH_LNG))
                .param("radiusKm", "100"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2));
  }

  @Test
  @DisplayName("distance field is populated and accurate")
  void shouldReturnAccurateDistance() throws Exception {
    OffsetDateTime future = OffsetDateTime.now().plusDays(1);
    createActivityAt("Bern Climb", BERN_LAT, BERN_LNG, future);

    mockMvc
        .perform(
            get("/api/activities")
                .session(buildSession(testUser))
                .param("lat", String.valueOf(ZURICH_LAT))
                .param("lng", String.valueOf(ZURICH_LNG))
                .param("radiusKm", "200"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].distance").isNumber())
        .andExpect(jsonPath("$.data[0].distance").value(org.hamcrest.Matchers.closeTo(96.0, 2.0)));
  }

  @Test
  @DisplayName("distance is null when no location provided")
  void shouldReturnNullDistanceWithoutLocation() throws Exception {
    OffsetDateTime future = OffsetDateTime.now().plusDays(1);
    createActivityAt("Zurich Climb", ZURICH_LAT, ZURICH_LNG, future);

    mockMvc
        .perform(get("/api/activities").session(buildSession(testUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].distance").isEmpty());
  }

  @Test
  @DisplayName("activities without location excluded from spatial results")
  void shouldExcludeActivitiesWithoutLocation() throws Exception {
    OffsetDateTime future = OffsetDateTime.now().plusDays(1);
    createActivityAt("Zurich Climb", ZURICH_LAT, ZURICH_LNG, future);
    createActivityWithoutLocation("No Location Climb", future.plusHours(1));

    // With spatial filter: only Zurich
    mockMvc
        .perform(
            get("/api/activities")
                .session(buildSession(testUser))
                .param("lat", String.valueOf(ZURICH_LAT))
                .param("lng", String.valueOf(ZURICH_LNG))
                .param("radiusKm", "200"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].title").value("Zurich Climb"));

    // Without spatial filter: both
    mockMvc
        .perform(get("/api/activities").session(buildSession(testUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2));
  }

  @Test
  @DisplayName("falls back to stored user location")
  void shouldFallbackToStoredUserLocation() throws Exception {
    testUser.setLocationLat(ZURICH_LAT);
    testUser.setLocationLng(ZURICH_LNG);
    userRepository.save(testUser);

    OffsetDateTime future = OffsetDateTime.now().plusDays(1);
    createActivityAt("Zurich Climb", ZURICH_LAT, ZURICH_LNG, future);
    createActivityAt("Geneva Climb", GENEVA_LAT, GENEVA_LNG, future.plusHours(1));

    mockMvc
        .perform(get("/api/activities").session(buildSession(testUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].title").value("Zurich Climb"));
  }

  @Test
  @DisplayName("query params override stored user location")
  void shouldOverrideStoredLocation() throws Exception {
    testUser.setLocationLat(GENEVA_LAT);
    testUser.setLocationLng(GENEVA_LNG);
    userRepository.save(testUser);

    OffsetDateTime future = OffsetDateTime.now().plusDays(1);
    createActivityAt("Zurich Climb", ZURICH_LAT, ZURICH_LNG, future);
    createActivityAt("Geneva Climb", GENEVA_LAT, GENEVA_LNG, future.plusHours(1));

    mockMvc
        .perform(
            get("/api/activities")
                .session(buildSession(testUser))
                .param("lat", String.valueOf(ZURICH_LAT))
                .param("lng", String.valueOf(ZURICH_LNG)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].title").value("Zurich Climb"));
  }

  @Test
  @DisplayName("sort remains startsAt ASC regardless of distance")
  void shouldSortByStartsAtNotDistance() throws Exception {
    OffsetDateTime future = OffsetDateTime.now().plusDays(1);
    createActivityAt("Bern Climb", BERN_LAT, BERN_LNG, future);
    createActivityAt("Zurich Climb", ZURICH_LAT, ZURICH_LNG, future.plusHours(2));

    mockMvc
        .perform(
            get("/api/activities")
                .session(buildSession(testUser))
                .param("lat", String.valueOf(ZURICH_LAT))
                .param("lng", String.valueOf(ZURICH_LNG))
                .param("radiusKm", "200"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].title").value("Bern Climb"))
        .andExpect(jsonPath("$.data[1].title").value("Zurich Climb"));
  }

  @Test
  @DisplayName("spatial filter composes with typeSlug filter")
  void shouldComposeWithTypeSlugFilter() throws Exception {
    OffsetDateTime future = OffsetDateTime.now().plusDays(1);
    createActivityAt("Zurich Climb", ZURICH_LAT, ZURICH_LNG, future);

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

    mockMvc
        .perform(
            get("/api/activities")
                .session(buildSession(testUser))
                .param("lat", String.valueOf(ZURICH_LAT))
                .param("lng", String.valueOf(ZURICH_LNG))
                .param("typeSlug", "climbing"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].title").value("Zurich Climb"));
  }

  @Test
  @DisplayName("partial lat/lng returns 400")
  void shouldRejectPartialLatLng() throws Exception {
    mockMvc
        .perform(get("/api/activities").session(buildSession(testUser)).param("lat", "47.3769"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("invalid lat returns 400")
  void shouldRejectInvalidLat() throws Exception {
    mockMvc
        .perform(
            get("/api/activities")
                .session(buildSession(testUser))
                .param("lat", "91")
                .param("lng", "8.5"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("radiusKm exceeding max returns 400")
  void shouldRejectExcessiveRadius() throws Exception {
    mockMvc
        .perform(
            get("/api/activities")
                .session(buildSession(testUser))
                .param("lat", "47.3769")
                .param("lng", "8.5417")
                .param("radiusKm", "300"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("radiusKm zero or negative returns 400")
  void shouldRejectNonPositiveRadius() throws Exception {
    mockMvc
        .perform(
            get("/api/activities")
                .session(buildSession(testUser))
                .param("lat", "47.3769")
                .param("lng", "8.5417")
                .param("radiusKm", "0"))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            get("/api/activities")
                .session(buildSession(testUser))
                .param("lat", "47.3769")
                .param("lng", "8.5417")
                .param("radiusKm", "-5"))
        .andExpect(status().isBadRequest());
  }
}
