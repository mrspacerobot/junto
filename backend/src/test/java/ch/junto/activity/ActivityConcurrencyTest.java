package ch.junto.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import ch.junto.activity.entity.Activity;
import ch.junto.base.TestContainerConfig;
import ch.junto.chat.ChatMessageRepository;
import ch.junto.fixtures.ActivityFixture;
import ch.junto.fixtures.ActivityParticipantFixture;
import ch.junto.fixtures.UserFixture;
import ch.junto.helpers.MockMvcTestHelper;
import ch.junto.participant.ParticipantRepository;
import ch.junto.participant.enums.ParticipantRole;
import ch.junto.participant.enums.ParticipantStatus;
import ch.junto.user.UserRepository;
import ch.junto.user.entity.User;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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
    createdActivityIds.forEach(
        id -> {
          chatMessageRepository.deleteAll(
              chatMessageRepository.findLatestByActivityId(id, Integer.MAX_VALUE));
          participantRepository.deleteAll(participantRepository.findAllByActivityId(id));
          activityRepository.deleteById(id);
        });
    createdUserIds.forEach(userRepository::deleteById);
  }

  private User createAndTrackUser(String email) {
    User user = userRepository.save(UserFixture.newMember(email));
    createdUserIds.add(user.getId());
    return user;
  }

  private Activity createAndTrackActivity(User creator, int maxParticipants) {
    Activity activity = ActivityFixture.newClimbingActivity(creator);
    activity.setTitle("Concurrency Test");
    activity.setDescription("Test");
    activity.setCity("Zurich");
    activity.setMaxParticipants(maxParticipants);
    activity = activityRepository.save(activity);
    createdActivityIds.add(activity.getId());
    return activity;
  }

  private MockHttpSession buildSession(User user) {
    return MockMvcTestHelper.sessionFor(user);
  }

  @Test
  @DisplayName("join racing a capacity reduction preserves the capacity invariant")
  void joinRacingCapacityReductionPreservesInvariant() throws Exception {
    User creator = createAndTrackUser("creator-reduce-" + UUID.randomUUID() + "@junto.app");
    User joiner = createAndTrackUser("joiner-reduce-" + UUID.randomUUID() + "@junto.app");
    Activity activity = createAndTrackActivity(creator, 2);
    participantRepository.save(
        ActivityParticipantFixture.participant(
            activity, creator, ParticipantRole.CREATOR, ParticipantStatus.APPROVED));

    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch go = new CountDownLatch(1);
    Future<Integer> join =
        executor.submit(
            () -> {
              ready.countDown();
              go.await();
              return mockMvc
                  .perform(
                      post("/api/activities/" + activity.getId() + "/participants/join")
                          .session(buildSession(joiner)))
                  .andReturn()
                  .getResponse()
                  .getStatus();
            });
    Future<Integer> reduce =
        executor.submit(
            () -> {
              ready.countDown();
              go.await();
              String body =
                  """
                {"title":"Concurrency Test","description":"Test","city":"Zurich",
                 "locationLat":null,"locationLng":null,"startsAt":"%s",
                 "signupDeadline":null,"maxParticipants":1,"joinPolicy":"OPEN","customFields":{}}
                """
                      .formatted(activity.getStartsAt());
              return mockMvc
                  .perform(
                      put("/api/activities/" + activity.getId())
                          .session(buildSession(creator))
                          .contentType("application/json")
                          .content(body))
                  .andReturn()
                  .getResponse()
                  .getStatus();
            });

    ready.await();
    go.countDown();
    assertThat(List.of(join.get(10, TimeUnit.SECONDS), reduce.get(10, TimeUnit.SECONDS)))
        .containsExactlyInAnyOrder(200, 409);
    executor.shutdown();

    Activity reloaded = activityRepository.findById(activity.getId()).orElseThrow();
    long approved =
        participantRepository.countByActivityIdAndStatus(
            activity.getId(), ParticipantStatus.APPROVED);
    assertThat(approved).isLessThanOrEqualTo(reloaded.getMaxParticipants());
  }

  @Test
  @DisplayName("concurrent terminal transitions cannot overwrite each other")
  void concurrentTerminalTransitionsAreSerialized() throws Exception {
    User creator = createAndTrackUser("creator-transition-" + UUID.randomUUID() + "@junto.app");
    Activity activity = createAndTrackActivity(creator, 4);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch go = new CountDownLatch(1);

    Future<Integer> cancel =
        executor.submit(
            () -> {
              ready.countDown();
              go.await();
              return mockMvc
                  .perform(
                      post("/api/activities/" + activity.getId() + "/cancel")
                          .session(buildSession(creator)))
                  .andReturn()
                  .getResponse()
                  .getStatus();
            });
    Future<Integer> complete =
        executor.submit(
            () -> {
              ready.countDown();
              go.await();
              return mockMvc
                  .perform(
                      post("/api/activities/" + activity.getId() + "/complete")
                          .session(buildSession(creator)))
                  .andReturn()
                  .getResponse()
                  .getStatus();
            });

    ready.await();
    go.countDown();
    assertThat(List.of(cancel.get(10, TimeUnit.SECONDS), complete.get(10, TimeUnit.SECONDS)))
        .containsExactlyInAnyOrder(200, 409);
    executor.shutdown();
  }

  @Test
  @DisplayName("concurrent joins should not exceed maxParticipants")
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

      futures.add(
          executor.submit(
              () -> {
                ready.countDown();
                go.await();
                return mockMvc
                    .perform(
                        post("/api/activities/" + activityId + "/participants/join")
                            .session(session))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              }));
    }

    ready.await();
    go.countDown();

    List<Integer> statuses =
        futures.stream()
            .map(
                f -> {
                  try {
                    return f.get(10, TimeUnit.SECONDS);
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                })
            .toList();
    executor.shutdown();

    long successes = statuses.stream().filter(s -> s == 200).count();
    long conflicts = statuses.stream().filter(s -> s == 409).count();

    assertThat(successes).isEqualTo(2);
    assertThat(conflicts).isEqualTo(8);

    // Double-check at the DB level
    long approvedCount =
        participantRepository.countByActivityIdAndStatus(
            activity.getId(), ParticipantStatus.APPROVED);
    assertThat(approvedCount).isEqualTo(2);
  }
}
