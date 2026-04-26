package ch.junto.activity;

import ch.junto.activity.entity.Activity;
import ch.junto.activity.enums.ActivityStatus;
import ch.junto.activity.enums.JoinPolicy;
import ch.junto.activity.entity.CustomFields;
import ch.junto.base.TestContainerConfig;
import ch.junto.chat.ChatMessageRepository;
import ch.junto.participant.ParticipantRepository;
import ch.junto.participant.enums.ParticipantStatus;
import ch.junto.user.UserRepository;
import ch.junto.user.entity.User;
import ch.junto.user.enums.UserRole;
import ch.junto.user.enums.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Activity Concurrency")
class ActivityConcurrencyTest extends TestContainerConfig {

    @Autowired private MockMvc mockMvc;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ParticipantRepository participantRepository;
    @Autowired private ChatMessageRepository chatMessageRepository;

    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<UUID> createdActivityIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        createdActivityIds.forEach(id -> {
            chatMessageRepository.deleteAll(
                chatMessageRepository.findLatestByActivityId(id, Integer.MAX_VALUE));
            participantRepository.deleteAll(participantRepository.findAllByActivityId(id));
            activityRepository.deleteById(id);
        });
        createdUserIds.forEach(userRepository::deleteById);
    }

    private User createAndTrackUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName(email.split("@")[0]);
        user.setRole(UserRole.MEMBER);
        user.setStatus(UserStatus.ACTIVE);
        user.setOnboarded(true);
        user.setInviteCodesRemaining(5);
        user = userRepository.save(user);
        createdUserIds.add(user.getId());
        return user;
    }

    private Activity createAndTrackActivity(User creator, int maxParticipants) {
        Activity activity = new Activity();
        activity.setCreator(creator);
        activity.setTypeSlug("climbing");
        activity.setTitle("Concurrency Test");
        activity.setDescription("Test");
        activity.setCity("Zurich");
        activity.setStartsAt(OffsetDateTime.now().plusDays(1));
        activity.setMaxParticipants(maxParticipants);
        activity.setJoinPolicy(JoinPolicy.OPEN);
        activity.setStatus(ActivityStatus.ACTIVE);
        CustomFields fields = new CustomFields();
        fields.put("style", "Bouldering");
        fields.put("environment", "Indoor");
        fields.put("experienceLevel", "Beginner");
        activity.setCustomFields(fields);
        activity = activityRepository.save(activity);
        createdActivityIds.add(activity.getId());
        return activity;
    }

    private MockHttpSession buildSession(User user) {
        MockHttpSession session = new MockHttpSession();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    @Test @DisplayName("concurrent joins should not exceed maxParticipants")
    void concurrentJoinsShouldRespectCapacity() throws Exception {
        User creator = createAndTrackUser("creator-" + UUID.randomUUID() + "@junto.app");
        // maxParticipants=2, no creator participant (we're not going through ActivityService.create)
        Activity activity = createAndTrackActivity(creator, 2);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            User joiner = createAndTrackUser("joiner-" + i + "-" + UUID.randomUUID() + "@junto.app");
            MockHttpSession session = buildSession(joiner);
            UUID activityId = activity.getId();

            futures.add(executor.submit(() -> {
                ready.countDown();
                go.await();
                return mockMvc.perform(post("/api/activities/" + activityId + "/participants/join")
                        .session(session))
                    .andReturn().getResponse().getStatus();
            }));
        }

        ready.await();
        go.countDown();

        List<Integer> statuses = futures.stream().map(f -> {
            try { return f.get(10, TimeUnit.SECONDS); }
            catch (Exception e) { throw new RuntimeException(e); }
        }).toList();
        executor.shutdown();

        long successes = statuses.stream().filter(s -> s == 200).count();
        long conflicts = statuses.stream().filter(s -> s == 409).count();

        assertThat(successes).isEqualTo(2);
        assertThat(conflicts).isEqualTo(8);

        // Double-check at the DB level
        long approvedCount = participantRepository.countByActivityIdAndStatus(activity.getId(), ParticipantStatus.APPROVED);
        assertThat(approvedCount).isEqualTo(2);
    }
}
