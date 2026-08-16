package ch.junto.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.junto.activity.entity.Activity;
import ch.junto.base.BaseIntegrationTest;
import ch.junto.chat.entity.ChatMessage;
import ch.junto.chat.enums.MessageType;
import ch.junto.fixtures.ActivityFixture;
import ch.junto.fixtures.ActivityParticipantFixture;
import ch.junto.fixtures.ChatMessageFixture;
import ch.junto.fixtures.UserFixture;
import ch.junto.helpers.DatabaseHelper;
import ch.junto.helpers.MockMvcTestHelper;
import ch.junto.message.dto.MessageThreadResponse;
import ch.junto.participant.enums.ParticipantRole;
import ch.junto.participant.enums.ParticipantStatus;
import ch.junto.user.entity.User;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import tools.jackson.databind.ObjectMapper;

@DisplayName("Message Threads Integration")
class MessageThreadIntegrationTest extends BaseIntegrationTest {

  @Autowired private DatabaseHelper dbHelper;
  @Autowired private EntityManager entityManager;
  @Autowired private ObjectMapper objectMapper;

  private User user;
  private MockHttpSession session;

  @BeforeEach
  void setUp() {
    user = dbHelper.save(UserFixture.newUser("thread-" + UUID.randomUUID() + "@junto.app"));
    session = buildSession(user);
  }

  @Test
  void getThreads_returnsEmptyWhenNotJoined() throws Exception {
    mockMvc
        .perform(get("/api/messages/threads").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void getThreads_returnsThreadForApprovedParticipant() throws Exception {
    Activity activity = dbHelper.save(ActivityFixture.newActivity(user, "climbing"));
    addParticipant(activity, user, ParticipantRole.CREATOR, ParticipantStatus.APPROVED);

    mockMvc
        .perform(get("/api/messages/threads").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].activityId").value(activity.getId().toString()))
        .andExpect(jsonPath("$[0].activityTitle").value("Test Activity"))
        .andExpect(jsonPath("$[0].lastMessageContent").isEmpty());
  }

  @Test
  void getThreads_populatesTypeImageParticipantCountAndAvatars() throws Exception {
    User host = dbHelper.save(UserFixture.newUser("host-" + UUID.randomUUID() + "@junto.app"));
    host.setAvatarUrl("https://example.test/host.png");
    user.setAvatarUrl("https://example.test/member.png");
    Activity activity = dbHelper.save(ActivityFixture.newActivity(host, "cycling"));
    addParticipant(activity, host, ParticipantRole.CREATOR, ParticipantStatus.APPROVED);
    addParticipant(activity, user, ParticipantRole.PARTICIPANT, ParticipantStatus.APPROVED);

    mockMvc
        .perform(get("/api/messages/threads").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].activityTypeSlug").value("cycling"))
        .andExpect(jsonPath("$[0].activityTypeName").value("Cycling"))
        .andExpect(jsonPath("$[0].imageUrl").isNotEmpty())
        .andExpect(jsonPath("$[0].participantCount").value(2))
        .andExpect(jsonPath("$[0].participantAvatars.length()").value(2));
  }

  @Test
  void getThreads_includesLastMessageWithoutTimestampConversionError() throws Exception {
    Activity activity = dbHelper.save(ActivityFixture.newActivity(user, "cycling"));
    addParticipant(activity, user, ParticipantRole.CREATOR, ParticipantStatus.APPROVED);

    ChatMessage message = new ChatMessage();
    message.setActivity(activity);
    message.setUser(user);
    message.setType(MessageType.USER);
    message.setContent("Seed-like message");
    dbHelper.save(message);

    mockMvc
        .perform(get("/api/messages/threads").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].activityId").value(activity.getId().toString()))
        .andExpect(jsonPath("$[0].lastMessageContent").value("Seed-like message"))
        .andExpect(jsonPath("$[0].lastMessageSender").value(user.getDisplayName()))
        .andExpect(jsonPath("$[0].lastMessageAt").isNotEmpty());
  }

  @Test
  void getThreads_returnsSameConversationForBothApprovedParticipants() throws Exception {
    User host = dbHelper.save(UserFixture.newUser("host-" + UUID.randomUUID() + "@junto.app"));
    Activity activity = dbHelper.save(ActivityFixture.newActivity(host, "climbing"));
    addParticipant(activity, host, ParticipantRole.CREATOR, ParticipantStatus.APPROVED);
    addParticipant(activity, user, ParticipantRole.PARTICIPANT, ParticipantStatus.APPROVED);
    saveMessage(activity, user, MessageType.USER, "See you at the trailhead", OffsetDateTime.now());

    List<MessageThreadResponse> userThreads = getThreads(session);
    List<MessageThreadResponse> hostThreads = getThreads(buildSession(host));

    assertThat(userThreads)
        .extracting(MessageThreadResponse::activityId, MessageThreadResponse::lastMessageContent)
        .containsExactly(tuple(activity.getId(), "See you at the trailhead"));
    assertThat(hostThreads)
        .extracting(MessageThreadResponse::activityId, MessageThreadResponse::lastMessageContent)
        .containsExactly(tuple(activity.getId(), "See you at the trailhead"));
  }

  @Test
  void getThreads_usesLatestUserMessageAndIgnoresNewerSystemMessage() throws Exception {
    Activity activity = dbHelper.save(ActivityFixture.newActivity(user, "cycling"));
    addParticipant(activity, user, ParticipantRole.CREATOR, ParticipantStatus.APPROVED);

    OffsetDateTime baseTime = OffsetDateTime.now().minusMinutes(5);
    saveMessage(activity, user, MessageType.USER, "Latest user message", baseTime);
    saveMessage(
        activity,
        null,
        MessageType.SYSTEM,
        "System event after user message",
        baseTime.plusMinutes(1));

    List<MessageThreadResponse> threads = getThreads(session);

    assertThat(threads).hasSize(1);
    assertThat(threads.get(0).lastMessageContent()).isEqualTo("Latest user message");
    assertThat(threads.get(0).lastMessageSender()).isEqualTo(user.getDisplayName());
  }

  @Test
  void getThreads_sortsByLatestUserMessageDescending() throws Exception {
    Activity olderActivity = dbHelper.save(ActivityFixture.newActivity(user, "climbing"));
    olderActivity.setTitle("Older thread");
    Activity newerActivity = dbHelper.save(ActivityFixture.newActivity(user, "cycling"));
    newerActivity.setTitle("Newer thread");
    addParticipant(olderActivity, user, ParticipantRole.CREATOR, ParticipantStatus.APPROVED);
    addParticipant(newerActivity, user, ParticipantRole.CREATOR, ParticipantStatus.APPROVED);

    OffsetDateTime baseTime = OffsetDateTime.now().minusMinutes(10);
    saveMessage(olderActivity, user, MessageType.USER, "Older message", baseTime);
    saveMessage(newerActivity, user, MessageType.USER, "Newer message", baseTime.plusMinutes(1));

    List<MessageThreadResponse> threads = getThreads(session);

    assertThat(threads)
        .extracting(MessageThreadResponse::activityId, MessageThreadResponse::lastMessageContent)
        .containsExactly(
            tuple(newerActivity.getId(), "Newer message"),
            tuple(olderActivity.getId(), "Older message"));
  }

  @Test
  void getThreads_excludesPendingParticipations() throws Exception {
    User host = dbHelper.save(UserFixture.newUser("host-" + UUID.randomUUID() + "@junto.app"));
    Activity activity = dbHelper.save(ActivityFixture.newActivity(host, "climbing"));
    addParticipant(activity, host, ParticipantRole.CREATOR, ParticipantStatus.APPROVED);
    addParticipant(activity, user, ParticipantRole.PARTICIPANT, ParticipantStatus.PENDING);

    mockMvc
        .perform(get("/api/messages/threads").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void getThreads_excludesPendingParticipantEvenWhenActivityHasUserMessages() throws Exception {
    User host = dbHelper.save(UserFixture.newUser("host-" + UUID.randomUUID() + "@junto.app"));
    Activity activity = dbHelper.save(ActivityFixture.newActivity(host, "climbing"));
    addParticipant(activity, host, ParticipantRole.CREATOR, ParticipantStatus.APPROVED);
    addParticipant(activity, user, ParticipantRole.PARTICIPANT, ParticipantStatus.PENDING);
    saveMessage(activity, host, MessageType.USER, "Approved host message", OffsetDateTime.now());

    mockMvc
        .perform(get("/api/messages/threads").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void getThreads_returns401WhenUnauthenticated() throws Exception {
    mockMvc.perform(get("/api/messages/threads")).andExpect(status().isUnauthorized());
  }

  private MockHttpSession buildSession(User user) {
    return MockMvcTestHelper.sessionFor(user);
  }

  private List<MessageThreadResponse> getThreads(MockHttpSession session) throws Exception {
    String body =
        mockMvc
            .perform(get("/api/messages/threads").session(session))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return Arrays.asList(objectMapper.readValue(body, MessageThreadResponse[].class));
  }

  private void addParticipant(
      Activity activity, User user, ParticipantRole role, ParticipantStatus status) {
    dbHelper.save(ActivityParticipantFixture.participant(activity, user, role, status));
  }

  private ChatMessage saveMessage(
      Activity activity, User sender, MessageType type, String content, OffsetDateTime createdAt) {
    ChatMessage message = ChatMessageFixture.message(activity, sender, type, content, createdAt);
    dbHelper.save(message);

    entityManager
        .createNativeQuery(
            """
            UPDATE chat_messages
            SET created_at = :createdAt, updated_at = :createdAt
            WHERE id = :id
            """)
        .setParameter("createdAt", createdAt)
        .setParameter("id", message.getId())
        .executeUpdate();
    entityManager.flush();
    return message;
  }
}
