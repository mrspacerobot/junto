package ch.junto.message;

import ch.junto.activity.entity.Activity;
import ch.junto.base.BaseIntegrationTest;
import ch.junto.fixtures.ActivityFixture;
import ch.junto.fixtures.UserFixture;
import ch.junto.helpers.DatabaseHelper;
import ch.junto.participant.entity.ActivityParticipant;
import ch.junto.participant.enums.ParticipantRole;
import ch.junto.participant.enums.ParticipantStatus;
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

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Message Threads Integration")
class MessageThreadIntegrationTest extends BaseIntegrationTest {

    @Autowired private DatabaseHelper dbHelper;

    private User user;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        user = dbHelper.save(UserFixture.newUser("thread-" + UUID.randomUUID() + "@junto.app"));
        session = buildSession(user);
    }

    @Test
    void getThreads_returnsEmptyWhenNotJoined() throws Exception {
        mockMvc.perform(get("/api/messages/threads").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getThreads_returnsThreadForApprovedParticipant() throws Exception {
        Activity activity = dbHelper.save(ActivityFixture.newActivity(user, "climbing"));
        addParticipant(activity, user, ParticipantRole.CREATOR, ParticipantStatus.APPROVED);

        mockMvc.perform(get("/api/messages/threads").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].activityId").value(activity.getId().toString()))
            .andExpect(jsonPath("$[0].activityTitle").value("Test Activity"))
            .andExpect(jsonPath("$[0].lastMessageContent").isEmpty());
    }

    @Test
    void getThreads_excludesPendingParticipations() throws Exception {
        User host = dbHelper.save(UserFixture.newUser("host-" + UUID.randomUUID() + "@junto.app"));
        Activity activity = dbHelper.save(ActivityFixture.newActivity(host, "climbing"));
        addParticipant(activity, host, ParticipantRole.CREATOR, ParticipantStatus.APPROVED);
        addParticipant(activity, user, ParticipantRole.PARTICIPANT, ParticipantStatus.PENDING);

        mockMvc.perform(get("/api/messages/threads").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getThreads_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/messages/threads"))
            .andExpect(status().isUnauthorized());
    }

    private MockHttpSession buildSession(User user) {
        MockHttpSession s = new MockHttpSession();
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        s.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx);
        return s;
    }

    private void addParticipant(Activity activity, User user, ParticipantRole role, ParticipantStatus status) {
        ActivityParticipant p = new ActivityParticipant();
        p.setActivity(activity);
        p.setUser(user);
        p.setRole(role);
        p.setStatus(status);
        dbHelper.save(p);
    }
}
