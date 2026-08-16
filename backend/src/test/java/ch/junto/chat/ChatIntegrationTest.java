package ch.junto.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.junto.activity.entity.Activity;
import ch.junto.base.BaseIntegrationTest;
import ch.junto.chat.dto.SendMessageRequest;
import ch.junto.chat.entity.ChatMessage;
import ch.junto.chat.enums.MessageType;
import ch.junto.fixtures.ActivityFixture;
import ch.junto.fixtures.ActivityParticipantFixture;
import ch.junto.fixtures.ChatMessageFixture;
import ch.junto.fixtures.UserFixture;
import ch.junto.helpers.DatabaseHelper;
import ch.junto.helpers.MockMvcTestHelper;
import ch.junto.participant.enums.ParticipantRole;
import ch.junto.user.entity.User;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import tools.jackson.databind.ObjectMapper;

@DisplayName("Chat Integration")
class ChatIntegrationTest extends BaseIntegrationTest {

  @Autowired private ObjectMapper objectMapper;
  @Autowired private DatabaseHelper dbHelper;
  @Autowired private EntityManager entityManager;

  private User host;
  private User participant;
  private User stranger;
  private Activity activity;

  @BeforeEach
  void setUp() {
    host = dbHelper.save(UserFixture.newUser("host-" + UUID.randomUUID() + "@junto.app"));
    participant =
        dbHelper.save(UserFixture.newUser("participant-" + UUID.randomUUID() + "@junto.app"));
    stranger = dbHelper.save(UserFixture.newUser("stranger-" + UUID.randomUUID() + "@junto.app"));

    activity = dbHelper.save(ActivityFixture.newActivity(host, "climbing"));

    // Host is always APPROVED (creator)
    addApprovedParticipant(activity, host, ParticipantRole.CREATOR);
    // Participant is APPROVED
    addApprovedParticipant(activity, participant, ParticipantRole.PARTICIPANT);
  }

  private void addApprovedParticipant(Activity act, User user, ParticipantRole role) {
    dbHelper.save(
        ActivityParticipantFixture.participant(
            act, user, role, ch.junto.participant.enums.ParticipantStatus.APPROVED));
  }

  private String json(Object dto) {
    return MockMvcTestHelper.json(objectMapper, dto);
  }

  private MockHttpSession buildSession(User user) {
    return MockMvcTestHelper.sessionFor(user);
  }

  private String messagesUrl() {
    return "/api/activities/" + activity.getId() + "/messages";
  }

  @Nested
  @DisplayName("GET messages")
  class GetMessages {

    @Test
    @DisplayName("should return message history for approved participant")
    void shouldReturnHistory() throws Exception {
      // Seed two messages
      sendMessage(participant, "First");
      sendMessage(host, "Second");

      mockMvc
          .perform(get(messagesUrl()).session(buildSession(participant)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(2))
          .andExpect(jsonPath("$[0].content").value("First"))
          .andExpect(jsonPath("$[1].content").value("Second"));
    }

    @Test
    @DisplayName("should return 403 for non-participant")
    void shouldReturn403ForNonParticipant() throws Exception {
      mockMvc
          .perform(get(messagesUrl()).session(buildSession(stranger)))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should return 401 for unauthenticated user")
    void shouldReturn401ForUnauthenticated() throws Exception {
      mockMvc.perform(get(messagesUrl())).andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @DisplayName("POST messages")
  class PostMessages {

    @Test
    @DisplayName("should persist and be visible in GET")
    void shouldPersistAndBeVisible() throws Exception {
      mockMvc
          .perform(
              post(messagesUrl())
                  .session(buildSession(participant))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json(new SendMessageRequest("Hello from test"))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content").value("Hello from test"))
          .andExpect(jsonPath("$.type").value("USER"))
          .andExpect(jsonPath("$.userId").value(participant.getId().toString()));

      // Verify it appears in GET
      mockMvc
          .perform(get(messagesUrl()).session(buildSession(host)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].content").value("Hello from test"));
    }

    @Test
    @DisplayName("should return 403 for non-participant")
    void shouldReturn403ForNonParticipant() throws Exception {
      mockMvc
          .perform(
              post(messagesUrl())
                  .session(buildSession(stranger))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json(new SendMessageRequest("Hack"))))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should reject blank content with 400")
    void shouldRejectBlankContent() throws Exception {
      mockMvc
          .perform(
              post(messagesUrl())
                  .session(buildSession(participant))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"content\":\"   \"}"))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should reject content over 2000 chars with 400")
    void shouldRejectOverLimitContent() throws Exception {
      String longContent = "a".repeat(2001);
      mockMvc
          .perform(
              post(messagesUrl())
                  .session(buildSession(participant))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json(new SendMessageRequest(longContent))))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should accept content at exactly 2000 chars")
    void shouldAcceptExactly2000Chars() throws Exception {
      String content = "a".repeat(2000);
      mockMvc
          .perform(
              post(messagesUrl())
                  .session(buildSession(participant))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json(new SendMessageRequest(content))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content").value(content));
    }
  }

  @Nested
  @DisplayName("System messages")
  class SystemMessages {

    @Test
    @DisplayName("should appear after join via participant endpoint")
    void shouldAppearAfterJoin() throws Exception {
      // Join as stranger (OPEN join policy)
      mockMvc
          .perform(
              post("/api/activities/" + activity.getId() + "/participants/join")
                  .session(buildSession(stranger)))
          .andExpect(status().isOk());

      // Check messages — should contain a SYSTEM message
      mockMvc
          .perform(get(messagesUrl()).session(buildSession(host)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[?(@.type == 'SYSTEM')]").exists())
          .andExpect(jsonPath("$[?(@.type == 'SYSTEM')].content").isNotEmpty());
    }
  }

  @Nested
  @DisplayName("Cursor pagination")
  class CursorPagination {

    @Test
    @DisplayName("should page correctly when messages share a timestamp")
    void shouldPageWithSharedTimestamps() throws Exception {
      // Seed 5 messages quickly — they may share the same timestamp
      for (int i = 1; i <= 5; i++) {
        sendMessage(participant, "Msg " + i);
      }

      // Get first page of 3
      String response =
          mockMvc
              .perform(get(messagesUrl()).session(buildSession(participant)).param("limit", "3"))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      List<?> firstPage = objectMapper.readValue(response, List.class);
      assertThat(firstPage).hasSize(3);

      // Get the ID of the oldest message in the first page (index 0, since ASC)
      @SuppressWarnings("unchecked")
      String oldestId = (String) ((java.util.Map<String, Object>) firstPage.get(0)).get("id");

      // Get second page using cursor — 2 messages older than the first page's oldest
      mockMvc
          .perform(
              get(messagesUrl())
                  .session(buildSession(participant))
                  .param("limit", "3")
                  .param("before", oldestId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(2));

      // Third page should be empty
      // Get the oldest ID from the second page response
      String secondResponse =
          mockMvc
              .perform(
                  get(messagesUrl())
                      .session(buildSession(participant))
                      .param("limit", "3")
                      .param("before", oldestId))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      List<?> secondPage = objectMapper.readValue(secondResponse, List.class);
      @SuppressWarnings("unchecked")
      String secondOldestId =
          (String) ((java.util.Map<String, Object>) secondPage.get(0)).get("id");

      mockMvc
          .perform(
              get(messagesUrl())
                  .session(buildSession(participant))
                  .param("limit", "3")
                  .param("before", secondOldestId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("should return all messages when no cursor provided")
    void shouldReturnAllWithoutCursor() throws Exception {
      for (int i = 1; i <= 3; i++) {
        sendMessage(participant, "Msg " + i);
      }

      mockMvc
          .perform(get(messagesUrl()).session(buildSession(participant)).param("limit", "50"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @DisplayName("should return 404 when cursor belongs to another activity")
    void shouldRejectCursorFromAnotherActivity() throws Exception {
      Activity otherActivity = dbHelper.save(ActivityFixture.newActivity(participant, "cycling"));
      addApprovedParticipant(otherActivity, participant, ParticipantRole.CREATOR);
      ChatMessage cursor =
          saveMessage(
              otherActivity,
              participant,
              "Other activity message",
              OffsetDateTime.now().minusMinutes(1));

      mockMvc
          .perform(
              get(messagesUrl())
                  .session(buildSession(participant))
                  .param("before", cursor.getId().toString()))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("Rate limiting")
  class RateLimiting {

    @Test
    @DisplayName("should return 429 after burst of 10 messages")
    void shouldReturn429AfterBurst() throws Exception {
      // Send 10 messages (the limit)
      for (int i = 0; i < 10; i++) {
        mockMvc
            .perform(
                post(messagesUrl())
                    .session(buildSession(participant))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new SendMessageRequest("Msg " + i))))
            .andExpect(status().isOk());
      }

      // 11th should be rate limited
      mockMvc
          .perform(
              post(messagesUrl())
                  .session(buildSession(participant))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json(new SendMessageRequest("One too many"))))
          .andExpect(status().isTooManyRequests());
    }
  }

  private void sendMessage(User sender, String content) throws Exception {
    mockMvc
        .perform(
            post(messagesUrl())
                .session(buildSession(sender))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new SendMessageRequest(content))))
        .andExpect(status().isOk());
  }

  private ChatMessage saveMessage(
      Activity targetActivity, User sender, String content, OffsetDateTime createdAt) {
    ChatMessage message =
        ChatMessageFixture.message(targetActivity, sender, MessageType.USER, content, createdAt);
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
