package ch.junto.activity;

import ch.junto.activity.dto.ActivityCreateRequest;
import ch.junto.activity.dto.ActivityUpdateRequest;
import ch.junto.activity.entity.Activity;
import ch.junto.activity.entity.CustomFields;
import ch.junto.activity.enums.ActivityStatus;
import ch.junto.activity.enums.JoinPolicy;
import ch.junto.base.BaseIntegrationTest;
import ch.junto.fixtures.ActivityFixture;
import ch.junto.fixtures.UserFixture;
import ch.junto.helpers.DatabaseHelper;
import ch.junto.user.UserRepository;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Activity Integration")
class ActivityIntegrationTest extends BaseIntegrationTest {

    @Autowired private ObjectMapper objectMapper;
    @Autowired private DatabaseHelper dbHelper;
    @Autowired private UserRepository userRepository;
    @Autowired private ActivityRepository activityRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = dbHelper.save(UserFixture.newUser("user-" + UUID.randomUUID() + "@junto.app"));
    }

    private String json(Object dto) {
        try { return objectMapper.writeValueAsString(dto); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    private MockHttpSession buildSession(User user) {
        MockHttpSession session = new MockHttpSession();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    @Test @DisplayName("should create activity successfully")
    void shouldCreateActivity() throws Exception {
        CustomFields customFields = new CustomFields();
        customFields.put("style", "Bouldering");
        customFields.put("grade", "V5");
        customFields.put("environment", "Indoor");
        customFields.put("experienceLevel", "Beginner");
        customFields.put("needsEquipment", true);

        ActivityCreateRequest request = new ActivityCreateRequest(
            "Integration Test Climbing", "A test description", "climbing", "Zurich",
            47.3769, 8.5417, OffsetDateTime.now().plusDays(2), OffsetDateTime.now().plusDays(1),
            5, JoinPolicy.OPEN, customFields
        );

        mockMvc.perform(post("/api/activities")
                .session(buildSession(testUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Integration Test Climbing"))
            .andExpect(jsonPath("$.activityTypeSlug").value("climbing"))
            .andExpect(jsonPath("$.imageUrl").isNotEmpty());

        List<Activity> activities = activityRepository.findAll();
        assertThat(activities).hasSize(1);
        assertThat(activities.get(0).getTitle()).isEqualTo("Integration Test Climbing");
        assertThat(activities.get(0).getTypeSlug()).isEqualTo("climbing");
    }

    @Test @DisplayName("should reject creation if unauthorized")
    void shouldRejectUnauthenticatedCreation() throws Exception {
        ActivityCreateRequest request = new ActivityCreateRequest(
            "Test", "Desc", "climbing", "City", null, null,
            OffsetDateTime.now().plusDays(1), null, 10, JoinPolicy.OPEN, null
        );

        mockMvc.perform(post("/api/activities")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test @DisplayName("should reject creation with invalid payload")
    void shouldRejectInvalidCreation() throws Exception {
        ActivityCreateRequest request = new ActivityCreateRequest(
            "", "Desc", "climbing", "", null, null,
            OffsetDateTime.now().plusDays(1), null, 10, JoinPolicy.OPEN, null
        );

        mockMvc.perform(post("/api/activities")
                .session(buildSession(testUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("should reject creation with invalid custom fields")
    void shouldRejectInvalidCustomFields() throws Exception {
        CustomFields customFields = new CustomFields();
        customFields.put("style", "InvalidStyle");
        customFields.put("environment", "Outdoor");
        customFields.put("experienceLevel", "Intermediate");

        ActivityCreateRequest request = new ActivityCreateRequest(
            "Test Climbing", "Desc", "climbing", "Zurich",
            null, null, OffsetDateTime.now().plusDays(1), null,
            5, JoinPolicy.OPEN, customFields
        );

        mockMvc.perform(post("/api/activities")
                .session(buildSession(testUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("should get activity detail")
    void shouldGetActivityDetail() throws Exception {
        Activity activity = dbHelper.save(ActivityFixture.newActivity(testUser, "climbing"));

        mockMvc.perform(get("/api/activities/" + activity.getId())
                .session(buildSession(testUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(activity.getId().toString()))
            .andExpect(jsonPath("$.title").value(activity.getTitle()))
            .andExpect(jsonPath("$.imageUrl").isNotEmpty());
    }

    @Test @DisplayName("should return 404 for non-existent activity")
    void shouldReturn404ForMissingActivity() throws Exception {
        mockMvc.perform(get("/api/activities/" + UUID.randomUUID())
                .session(buildSession(testUser)))
            .andExpect(status().isNotFound());
    }

    @Test @DisplayName("should update activity successfully")
    void shouldUpdateActivity() throws Exception {
        Activity activity = dbHelper.save(ActivityFixture.newActivity(testUser, "climbing"));

        ActivityUpdateRequest request = new ActivityUpdateRequest(
            "Updated Title", "Updated Desc", "Geneva", null, null,
            OffsetDateTime.now().plusDays(3), null, null
        );

        mockMvc.perform(put("/api/activities/" + activity.getId())
                .session(buildSession(testUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Updated Title"));

        Activity updated = activityRepository.findById(activity.getId()).orElseThrow();
        assertThat(updated.getTitle()).isEqualTo("Updated Title");
        assertThat(updated.getCity()).isEqualTo("Geneva");
    }

    @Test @DisplayName("should forbid update by non-creator")
    void shouldForbidUpdateByNonCreator() throws Exception {
        Activity activity = dbHelper.save(ActivityFixture.newActivity(testUser, "climbing"));
        User otherUser = dbHelper.save(UserFixture.newUser("other-" + UUID.randomUUID() + "@junto.app"));

        ActivityUpdateRequest request = new ActivityUpdateRequest(
            "Hacked Title", "Desc", "City", null, null,
            OffsetDateTime.now().plusDays(1), null, null
        );

        mockMvc.perform(put("/api/activities/" + activity.getId())
                .session(buildSession(otherUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isForbidden());
    }

    @Test @DisplayName("should cancel activity")
    void shouldCancelActivity() throws Exception {
        Activity activity = dbHelper.save(ActivityFixture.newActivity(testUser, "climbing"));

        mockMvc.perform(post("/api/activities/" + activity.getId() + "/cancel")
                .session(buildSession(testUser)))
            .andExpect(status().isOk());

        Activity cancelled = activityRepository.findById(activity.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(ActivityStatus.CANCELLED);
    }

    @Test @DisplayName("should complete activity")
    void shouldCompleteActivity() throws Exception {
        Activity activity = dbHelper.save(ActivityFixture.newActivity(testUser, "climbing"));

        mockMvc.perform(post("/api/activities/" + activity.getId() + "/complete")
                .session(buildSession(testUser)))
            .andExpect(status().isOk());

        Activity completed = activityRepository.findById(activity.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(ActivityStatus.COMPLETED);
    }

    @Test @DisplayName("should return 409 when cancelling already-cancelled activity")
    void shouldRejectCancelOnCancelled() throws Exception {
        Activity activity = dbHelper.save(ActivityFixture.newActivity(testUser, "climbing"));
        activity.setStatus(ActivityStatus.CANCELLED);
        activityRepository.save(activity);

        mockMvc.perform(post("/api/activities/" + activity.getId() + "/cancel")
                .session(buildSession(testUser)))
            .andExpect(status().isConflict());
    }

    @Test @DisplayName("should return 409 when cancelling completed activity")
    void shouldRejectCancelOnCompleted() throws Exception {
        Activity activity = dbHelper.save(ActivityFixture.newActivity(testUser, "climbing"));
        activity.setStatus(ActivityStatus.COMPLETED);
        activityRepository.save(activity);

        mockMvc.perform(post("/api/activities/" + activity.getId() + "/cancel")
                .session(buildSession(testUser)))
            .andExpect(status().isConflict());
    }

    @Test @DisplayName("should return 409 when completing cancelled activity")
    void shouldRejectCompleteOnCancelled() throws Exception {
        Activity activity = dbHelper.save(ActivityFixture.newActivity(testUser, "climbing"));
        activity.setStatus(ActivityStatus.CANCELLED);
        activityRepository.save(activity);

        mockMvc.perform(post("/api/activities/" + activity.getId() + "/complete")
                .session(buildSession(testUser)))
            .andExpect(status().isConflict());
    }

    @Test @DisplayName("should list activity types with field definitions")
    void shouldListActivityTypes() throws Exception {
        mockMvc.perform(get("/api/activity-types")
                .session(buildSession(testUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[?(@.slug == 'climbing')]").exists())
            .andExpect(jsonPath("$[?(@.slug == 'climbing')].customFields").isNotEmpty())
            .andExpect(jsonPath("$[?(@.slug == 'climbing')].summaryFields").isNotEmpty())
            .andExpect(jsonPath("$[?(@.slug == 'climbing')].defaultImageUrl").isNotEmpty());
    }

    @Test @DisplayName("should filter activities by custom field value")
    void shouldFilterByCustomField() throws Exception {
        Activity bouldering = dbHelper.save(ActivityFixture.newActivity(testUser, "climbing"));
        // ActivityFixture.newActivity sets style=Bouldering for climbing

        mockMvc.perform(get("/api/activities")
                .session(buildSession(testUser))
                .param("typeSlug", "climbing")
                .param("customFields[style]", "Bouldering"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value(bouldering.getId().toString()));

        mockMvc.perform(get("/api/activities")
                .session(buildSession(testUser))
                .param("typeSlug", "climbing")
                .param("customFields[style]", "Sport"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test @DisplayName("should return 400 if customFields filter used without typeSlug")
    void shouldRejectCustomFieldFilterWithoutTypeSlug() throws Exception {
        mockMvc.perform(get("/api/activities")
                .session(buildSession(testUser))
                .param("customFields[style]", "Bouldering"))
            .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("should return 400 if customFields filter references unknown field")
    void shouldRejectUnknownCustomFieldFilter() throws Exception {
        mockMvc.perform(get("/api/activities")
                .session(buildSession(testUser))
                .param("typeSlug", "climbing")
                .param("customFields[nonexistent]", "Value"))
            .andExpect(status().isBadRequest());
    }
}
