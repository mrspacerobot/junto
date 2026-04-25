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
