# Activity Chat Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add real-time activity-scoped chat using STOMP over WebSocket for broadcast and REST for persistence, with cursor pagination, validation, rate limiting, and system messages on join/leave.

**Architecture:** REST endpoints handle message persistence and history loading. STOMP over WebSocket broadcasts messages to subscribed participants in real-time. Spring Session cookies authenticate both REST and WebSocket. Spring ApplicationEvents decouple participant lifecycle from chat system messages.

**Tech Stack:** Spring Boot WebSocket + STOMP, SockJS, `@stomp/stompjs`, Caffeine cache (rate limiting), existing Spring Session auth

**Spec:** `docs/superpowers/specs/2026-04-25-activity-chat-design.md`

---

## File Map

### Backend — New files
- `backend/src/testFixtures/java/ch/junto/fixtures/ParticipantFixture.java` — test fixture for ActivityParticipant creation
- `backend/src/main/java/ch/junto/chat/dto/SendMessageRequest.java` — request body DTO with validation
- `backend/src/main/java/ch/junto/chat/event/ParticipantJoinedEvent.java` — Spring ApplicationEvent for join
- `backend/src/main/java/ch/junto/chat/event/ParticipantLeftEvent.java` — Spring ApplicationEvent for leave
- `backend/src/main/java/ch/junto/chat/event/ChatEventListener.java` — listens to participant events, posts system messages
- `backend/src/main/java/ch/junto/websocket/WebSocketConfig.java` — STOMP broker config
- `backend/src/main/java/ch/junto/websocket/WebSocketAuthInterceptor.java` — channel interceptor for subscription auth
- `backend/src/main/java/ch/junto/websocket/HttpSessionHandshakeInterceptor.java` — copies HTTP session to WebSocket
- `backend/src/main/java/ch/junto/core/exception/RateLimitException.java` — 429 exception
- `backend/src/test/java/ch/junto/chat/ChatIntegrationTest.java` — REST endpoint integration tests

### Backend — Modified files
- `backend/build.gradle` — add websocket + caffeine dependencies
- `backend/src/main/java/ch/junto/chat/ChatController.java` — rewrite: cursor pagination, request body DTO, validation
- `backend/src/main/java/ch/junto/chat/ChatService.java` — rewrite: cursor pagination, broadcast, rate limiting, content validation
- `backend/src/main/java/ch/junto/chat/ChatMessageRepository.java` — replace query with keyset cursor queries
- `backend/src/main/java/ch/junto/chat/ChatServiceTest.java` — expand unit tests
- `backend/src/main/java/ch/junto/participant/ParticipantService.java` — publish ApplicationEvents on join/leave
- `backend/src/main/java/ch/junto/auth/config/SecurityConfig.java` — permit `/ws/**` endpoint
- `backend/src/main/java/ch/junto/core/exception/GlobalExceptionHandler.java` — handle RateLimitException → 429

### Frontend — New files
- `frontend/src/composables/useChat.ts` — STOMP connection lifecycle, message history, send

### Frontend — Modified files
- `frontend/package.json` — add `@stomp/stompjs` dependency
- `frontend/vite.config.ts` — add `/ws` proxy with WebSocket upgrade
- `frontend/src/views/activities/ActivityDetailView.vue` — replace mock data with `useChat`

---

## Task 1: Add dependencies

**Files:**
- Modify: `backend/build.gradle:22-43`
- Modify: `frontend/package.json:14-19`
- Modify: `frontend/vite.config.ts:13-24`

- [ ] **Step 1: Add WebSocket and Caffeine to build.gradle**

Add after the existing `implementation` block in `backend/build.gradle`:

```gradle
implementation 'org.springframework.boot:spring-boot-starter-websocket'
implementation 'com.github.ben-manes.caffeine:caffeine:3.2.0'
```

- [ ] **Step 2: Add @stomp/stompjs to frontend**

Run:
```bash
cd frontend && npm install @stomp/stompjs
```

Expected: `@stomp/stompjs` appears in `package.json` dependencies.

- [ ] **Step 3: Add WebSocket proxy to vite.config.ts**

Add a `/ws` proxy entry to the server.proxy config:

```typescript
'/ws': {
  target: 'http://localhost:8080',
  changeOrigin: true,
  ws: true,
},
```

- [ ] **Step 4: Verify backend compiles**

Run:
```bash
cd backend && ./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add backend/build.gradle frontend/package.json frontend/package-lock.json frontend/vite.config.ts
git commit -m "chore: add websocket, caffeine, and stomp dependencies"
```

---

## Task 2: Update ChatController and ChatService for cursor pagination and validation

**Files:**
- Create: `backend/src/main/java/ch/junto/chat/dto/SendMessageRequest.java`
- Create: `backend/src/main/java/ch/junto/core/exception/RateLimitException.java`
- Modify: `backend/src/main/java/ch/junto/chat/ChatController.java`
- Modify: `backend/src/main/java/ch/junto/chat/ChatService.java`
- Modify: `backend/src/main/java/ch/junto/chat/ChatMessageRepository.java`
- Modify: `backend/src/main/java/ch/junto/core/exception/GlobalExceptionHandler.java`
- Test: `backend/src/test/java/ch/junto/chat/ChatServiceTest.java`

- [ ] **Step 1: Write unit tests for new ChatService behavior**

Replace `backend/src/test/java/ch/junto/chat/ChatServiceTest.java` entirely:

```java
package ch.junto.chat;

import ch.junto.activity.ActivityRepository;
import ch.junto.chat.dto.ChatMessageResponse;
import ch.junto.fixtures.ActivityFixture;
import ch.junto.fixtures.UserFixture;
import ch.junto.participant.ParticipantRepository;
import ch.junto.activity.entity.Activity;
import ch.junto.chat.entity.ChatMessage;
import ch.junto.participant.entity.ActivityParticipant;
import ch.junto.user.entity.User;
import ch.junto.chat.enums.MessageType;
import ch.junto.participant.enums.ParticipantStatus;
import ch.junto.core.exception.ForbiddenException;
import ch.junto.core.exception.NotFoundException;
import ch.junto.core.exception.RateLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private ActivityRepository activityRepository;
    @Mock private ParticipantRepository participantRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    private ChatService chatService;

    private User user;
    private Activity activity;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(chatMessageRepository, activityRepository, participantRepository, messagingTemplate);
        user = UserFixture.defaultUser();
        activity = ActivityFixture.defaultActivity(UserFixture.userWithEmail("host@ex.com"), "climbing");
    }

    @Test
    void sendMessage_approvedParticipant_savesAndBroadcasts() {
        ActivityParticipant participant = new ActivityParticipant();
        participant.setStatus(ParticipantStatus.APPROVED);

        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId())).thenReturn(Optional.of(participant));
        when(chatMessageRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ChatMessageResponse result = chatService.sendMessage(activity.getId(), "Hello", user);

        assertThat(result.content()).isEqualTo("Hello");
        verify(chatMessageRepository).save(any());
        verify(messagingTemplate).convertAndSend(eq("/topic/activity/" + activity.getId()), any(ChatMessageResponse.class));
    }

    @Test
    void sendMessage_pendingParticipant_throwsForbidden() {
        ActivityParticipant participant = new ActivityParticipant();
        participant.setStatus(ParticipantStatus.PENDING);

        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId())).thenReturn(Optional.of(participant));

        assertThatThrownBy(() -> chatService.sendMessage(activity.getId(), "Hello", user))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void sendMessage_nonParticipant_throwsForbidden() {
        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.sendMessage(activity.getId(), "Hello", user))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void sendMessage_activityNotFound_throwsNotFoundException() {
        when(activityRepository.findById(activity.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.sendMessage(activity.getId(), "Hello", user))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void sendMessage_blankContent_throwsIllegalArgument() {
        ActivityParticipant participant = new ActivityParticipant();
        participant.setStatus(ParticipantStatus.APPROVED);

        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId())).thenReturn(Optional.of(participant));

        assertThatThrownBy(() -> chatService.sendMessage(activity.getId(), "   ", user))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sendMessage_contentOver2000Chars_throwsIllegalArgument() {
        ActivityParticipant participant = new ActivityParticipant();
        participant.setStatus(ParticipantStatus.APPROVED);

        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId())).thenReturn(Optional.of(participant));

        String longContent = "a".repeat(2001);
        assertThatThrownBy(() -> chatService.sendMessage(activity.getId(), longContent, user))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sendMessage_contentExactly2000Chars_succeeds() {
        ActivityParticipant participant = new ActivityParticipant();
        participant.setStatus(ParticipantStatus.APPROVED);

        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId())).thenReturn(Optional.of(participant));
        when(chatMessageRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        String content = "a".repeat(2000);
        ChatMessageResponse result = chatService.sendMessage(activity.getId(), content, user);

        assertThat(result.content()).hasSize(2000);
    }

    @Test
    void sendSystemMessage_insertsAndBroadcasts() {
        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(chatMessageRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        chatService.sendSystemMessage(activity.getId(), "User joined the activity");

        verify(chatMessageRepository).save(argThat(msg ->
            msg.getType() == MessageType.SYSTEM && msg.getUser() == null
        ));
        verify(messagingTemplate).convertAndSend(eq("/topic/activity/" + activity.getId()), any(ChatMessageResponse.class));
    }

    @Test
    void getMessages_nonParticipant_throwsForbidden() {
        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.getMessages(activity.getId(), user, 50, null))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getMessages_withCursor_fetchesBeforeCursor() {
        ActivityParticipant participant = new ActivityParticipant();
        participant.setStatus(ParticipantStatus.APPROVED);

        UUID cursorId = UUID.randomUUID();
        ChatMessage cursorMsg = new ChatMessage();
        cursorMsg.setCreatedAt(OffsetDateTime.now());

        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId())).thenReturn(Optional.of(participant));
        when(chatMessageRepository.findById(cursorId)).thenReturn(Optional.of(cursorMsg));
        when(chatMessageRepository.findByActivityIdBeforeCursor(any(), any(), any(), anyInt())).thenReturn(List.of());

        List<ChatMessageResponse> result = chatService.getMessages(activity.getId(), user, 50, cursorId);

        assertThat(result).isEmpty();
        verify(chatMessageRepository).findByActivityIdBeforeCursor(eq(activity.getId()), eq(cursorMsg.getCreatedAt()), eq(cursorId), eq(50));
    }

    @Test
    void getMessages_noCursor_fetchesLatest() {
        ActivityParticipant participant = new ActivityParticipant();
        participant.setStatus(ParticipantStatus.APPROVED);

        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId())).thenReturn(Optional.of(participant));
        when(chatMessageRepository.findLatestByActivityId(any(), anyInt())).thenReturn(List.of());

        List<ChatMessageResponse> result = chatService.getMessages(activity.getId(), user, 50, null);

        assertThat(result).isEmpty();
        verify(chatMessageRepository).findLatestByActivityId(eq(activity.getId()), eq(50));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:
```bash
cd backend && ./gradlew test --tests "ch.junto.chat.ChatServiceTest" 2>&1 | tail -5
```

Expected: FAIL — `RateLimitException` doesn't exist yet, `ChatService` constructor mismatch, new repository methods missing.

- [ ] **Step 3: Create RateLimitException**

Create `backend/src/main/java/ch/junto/core/exception/RateLimitException.java`:

```java
package ch.junto.core.exception;

public class RateLimitException extends ApiException {

    public RateLimitException(String message) {
        super(message, "RATE_LIMITED", 429);
    }
}
```

- [ ] **Step 4: Add RateLimitException handler to GlobalExceptionHandler**

The existing `GlobalExceptionHandler` already handles `ApiException` subclasses via `handleApiException`. Since `RateLimitException` extends `ApiException`, it will automatically return the correct 429 status. No changes needed to `GlobalExceptionHandler`.

Verify by checking the handler:
```bash
cd backend && grep -A5 "handleApiException" src/main/java/ch/junto/core/exception/GlobalExceptionHandler.java
```

Expected: The handler uses `ex.getStatus()` which will return 429 for RateLimitException.

- [ ] **Step 5: Create SendMessageRequest DTO**

Create `backend/src/main/java/ch/junto/chat/dto/SendMessageRequest.java`:

```java
package ch.junto.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
    @NotBlank(message = "Content must not be blank")
    @Size(max = 2000, message = "Content must not exceed 2000 characters")
    String content
) {}
```

- [ ] **Step 6: Rewrite ChatMessageRepository with keyset cursor queries**

Replace `backend/src/main/java/ch/junto/chat/ChatMessageRepository.java`:

```java
package ch.junto.chat;

import ch.junto.chat.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    @Query(value = """
        SELECT * FROM chat_messages
        WHERE activity_id = :activityId
          AND (created_at, id) < (:cursorCreatedAt, :cursorId)
        ORDER BY created_at DESC, id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ChatMessage> findByActivityIdBeforeCursor(
        @Param("activityId") UUID activityId,
        @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
        @Param("cursorId") UUID cursorId,
        @Param("limit") int limit
    );

    @Query(value = """
        SELECT * FROM chat_messages
        WHERE activity_id = :activityId
        ORDER BY created_at DESC, id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ChatMessage> findLatestByActivityId(
        @Param("activityId") UUID activityId,
        @Param("limit") int limit
    );
}
```

- [ ] **Step 7: Rewrite ChatService with broadcast, validation, and cursor pagination**

Replace `backend/src/main/java/ch/junto/chat/ChatService.java`:

```java
package ch.junto.chat;

import ch.junto.activity.ActivityRepository;
import ch.junto.chat.dto.ChatMessageResponse;
import ch.junto.chat.entity.ChatMessage;
import ch.junto.chat.enums.MessageType;
import ch.junto.core.exception.ForbiddenException;
import ch.junto.core.exception.NotFoundException;
import ch.junto.core.exception.RateLimitException;
import ch.junto.participant.ParticipantRepository;
import ch.junto.participant.enums.ParticipantStatus;
import ch.junto.activity.entity.Activity;
import ch.junto.user.entity.User;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Transactional
public class ChatService {

    private static final int MAX_CONTENT_LENGTH = 2000;
    private static final int RATE_LIMIT_MAX = 10;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofSeconds(10);

    private final ChatMessageRepository chatMessageRepository;
    private final ActivityRepository activityRepository;
    private final ParticipantRepository participantRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private final Cache<String, CopyOnWriteArrayList<Long>> rateLimitCache = Caffeine.newBuilder()
        .expireAfterWrite(RATE_LIMIT_WINDOW)
        .build();

    public ChatService(
        ChatMessageRepository chatMessageRepository,
        ActivityRepository activityRepository,
        ParticipantRepository participantRepository,
        SimpMessagingTemplate messagingTemplate
    ) {
        this.chatMessageRepository = chatMessageRepository;
        this.activityRepository = activityRepository;
        this.participantRepository = participantRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public ChatMessageResponse sendMessage(UUID activityId, String content, User user) {
        Activity activity = activityRepository.findById(activityId)
            .orElseThrow(() -> new NotFoundException("Activity not found"));

        requireApprovedParticipant(activityId, user.getId());

        String trimmed = content.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Content must not be blank");
        }
        if (trimmed.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("Content must not exceed " + MAX_CONTENT_LENGTH + " characters");
        }

        checkRateLimit(activityId, user.getId());

        ChatMessage message = new ChatMessage();
        message.setActivity(activity);
        message.setUser(user);
        message.setContent(trimmed);
        message.setType(MessageType.USER);
        message = chatMessageRepository.save(message);

        ChatMessageResponse response = mapToResponse(message);
        messagingTemplate.convertAndSend("/topic/activity/" + activityId, response);
        return response;
    }

    public void sendSystemMessage(UUID activityId, String content) {
        Activity activity = activityRepository.findById(activityId)
            .orElseThrow(() -> new NotFoundException("Activity not found"));

        ChatMessage message = new ChatMessage();
        message.setActivity(activity);
        message.setUser(null);
        message.setContent(content);
        message.setType(MessageType.SYSTEM);
        message = chatMessageRepository.save(message);

        ChatMessageResponse response = mapToResponse(message);
        messagingTemplate.convertAndSend("/topic/activity/" + activityId, response);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(UUID activityId, User user, int limit, UUID beforeId) {
        requireApprovedParticipant(activityId, user.getId());

        int clampedLimit = Math.max(1, Math.min(limit, 100));

        List<ChatMessage> messages;
        if (beforeId != null) {
            ChatMessage cursor = chatMessageRepository.findById(beforeId)
                .orElseThrow(() -> new NotFoundException("Cursor message not found"));
            messages = chatMessageRepository.findByActivityIdBeforeCursor(
                activityId, cursor.getCreatedAt(), cursor.getId(), clampedLimit);
        } else {
            messages = chatMessageRepository.findLatestByActivityId(activityId, clampedLimit);
        }

        // Results come DESC from DB — reverse to ASC for the client
        Collections.reverse(messages);
        return messages.stream().map(this::mapToResponse).toList();
    }

    private void requireApprovedParticipant(UUID activityId, UUID userId) {
        participantRepository.findByActivityIdAndUserId(activityId, userId)
            .filter(p -> p.getStatus() == ParticipantStatus.APPROVED)
            .orElseThrow(() -> new ForbiddenException("Only approved participants can access chat"));
    }

    private void checkRateLimit(UUID activityId, UUID userId) {
        String key = userId + ":" + activityId;
        long now = System.currentTimeMillis();
        long windowStart = now - RATE_LIMIT_WINDOW.toMillis();

        CopyOnWriteArrayList<Long> timestamps = rateLimitCache.get(key, k -> new CopyOnWriteArrayList<>());
        timestamps.removeIf(ts -> ts < windowStart);

        if (timestamps.size() >= RATE_LIMIT_MAX) {
            throw new RateLimitException("Too many messages. Try again shortly.");
        }
        timestamps.add(now);
    }

    private ChatMessageResponse mapToResponse(ChatMessage m) {
        return new ChatMessageResponse(
            m.getId(),
            m.getActivity().getId(),
            m.getUser() != null ? m.getUser().getId() : null,
            m.getUser() != null ? m.getUser().getDisplayName() : "System",
            m.getUser() != null ? m.getUser().getAvatarUrl() : null,
            m.getContent(),
            m.getType(),
            m.getCreatedAt()
        );
    }
}
```

- [ ] **Step 8: Rewrite ChatController with cursor params and request DTO**

Replace `backend/src/main/java/ch/junto/chat/ChatController.java`:

```java
package ch.junto.chat;

import ch.junto.auth.CurrentUser;
import ch.junto.chat.dto.ChatMessageResponse;
import ch.junto.chat.dto.SendMessageRequest;
import ch.junto.user.entity.User;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/activities/{activityId}/messages")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatMessageResponse send(
        @PathVariable UUID activityId,
        @RequestBody @Valid SendMessageRequest request,
        @CurrentUser User user
    ) {
        return chatService.sendMessage(activityId, request.content(), user);
    }

    @GetMapping
    public List<ChatMessageResponse> list(
        @PathVariable UUID activityId,
        @RequestParam(defaultValue = "50") int limit,
        @RequestParam(required = false) UUID before,
        @CurrentUser User user
    ) {
        return chatService.getMessages(activityId, user, limit, before);
    }
}
```

Note: the endpoint path changes from `/api/activities/{activityId}/chat` to `/api/activities/{activityId}/messages` per the spec.

- [ ] **Step 9: Run unit tests**

Run:
```bash
cd backend && ./gradlew test --tests "ch.junto.chat.ChatServiceTest"
```

Expected: All 10 tests PASS.

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/java/ch/junto/chat/ backend/src/main/java/ch/junto/core/exception/RateLimitException.java backend/src/test/java/ch/junto/chat/ChatServiceTest.java
git commit -m "feat: rewrite chat service with cursor pagination, validation, rate limiting, and STOMP broadcast"
```

---

## Task 3: WebSocket configuration and channel security

**Files:**
- Create: `backend/src/main/java/ch/junto/websocket/WebSocketConfig.java`
- Create: `backend/src/main/java/ch/junto/websocket/WebSocketAuthInterceptor.java`
- Create: `backend/src/main/java/ch/junto/websocket/HttpSessionHandshakeInterceptor.java`
- Modify: `backend/src/main/java/ch/junto/auth/config/SecurityConfig.java`

- [ ] **Step 1: Create HttpSessionHandshakeInterceptor**

Create `backend/src/main/java/ch/junto/websocket/HttpSessionHandshakeInterceptor.java`:

```java
package ch.junto.websocket;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

public class HttpSessionHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            var session = servletRequest.getServletRequest().getSession(false);
            if (session != null) {
                attributes.put("HTTP_SESSION_ID", session.getId());
            }
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            attributes.put("AUTHENTICATED_USER", auth.getPrincipal());
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
    }
}
```

- [ ] **Step 2: Create WebSocketAuthInterceptor**

Create `backend/src/main/java/ch/junto/websocket/WebSocketAuthInterceptor.java`:

```java
package ch.junto.websocket;

import ch.junto.participant.ParticipantRepository;
import ch.junto.participant.enums.ParticipantStatus;
import ch.junto.user.entity.User;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Pattern ACTIVITY_TOPIC = Pattern.compile("^/topic/activity/([0-9a-f\\-]+)$");

    private final ParticipantRepository participantRepository;

    public WebSocketAuthInterceptor(ParticipantRepository participantRepository) {
        this.participantRepository = participantRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }

        String destination = accessor.getDestination();
        if (destination == null) {
            return message;
        }

        Matcher matcher = ACTIVITY_TOPIC.matcher(destination);
        if (!matcher.matches()) {
            return message;
        }

        UUID activityId = UUID.fromString(matcher.group(1));
        Object principal = accessor.getSessionAttributes().get("AUTHENTICATED_USER");
        if (!(principal instanceof User user)) {
            throw new IllegalStateException("Not authenticated");
        }

        boolean isApproved = participantRepository.findByActivityIdAndUserId(activityId, user.getId())
            .filter(p -> p.getStatus() == ParticipantStatus.APPROVED)
            .isPresent();

        if (!isApproved) {
            throw new IllegalStateException("Not an approved participant of this activity");
        }

        return message;
    }
}
```

- [ ] **Step 3: Create WebSocketConfig**

Create `backend/src/main/java/ch/junto/websocket/WebSocketConfig.java`:

```java
package ch.junto.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor authInterceptor;

    public WebSocketConfig(WebSocketAuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
            .addInterceptors(new HttpSessionHandshakeInterceptor())
            .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
```

- [ ] **Step 4: Permit /ws endpoint in SecurityConfig**

Add `/ws/**` to the permitted request matchers in `SecurityConfig.java`. In the `authorizeHttpRequests` block, add this line after the existing `.permitAll()` entries:

```java
.requestMatchers("/ws/**").permitAll()
```

So the block becomes:
```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/auth/request-code", "/api/auth/verify", "/api/auth/verify-token").permitAll()
    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
    .requestMatchers("/ws/**").permitAll()
    .anyRequest().authenticated()
)
```

- [ ] **Step 5: Verify compilation**

Run:
```bash
cd backend && ./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/ch/junto/websocket/ backend/src/main/java/ch/junto/auth/config/SecurityConfig.java
git commit -m "feat: add WebSocket STOMP config with session auth and subscription security"
```

---

## Task 4: Participant events and system messages

**Files:**
- Create: `backend/src/main/java/ch/junto/chat/event/ParticipantJoinedEvent.java`
- Create: `backend/src/main/java/ch/junto/chat/event/ParticipantLeftEvent.java`
- Create: `backend/src/main/java/ch/junto/chat/event/ChatEventListener.java`
- Modify: `backend/src/main/java/ch/junto/participant/ParticipantService.java`
- Test: `backend/src/test/java/ch/junto/chat/ChatServiceTest.java` (already covers sendSystemMessage)

- [ ] **Step 1: Create ParticipantJoinedEvent**

Create `backend/src/main/java/ch/junto/chat/event/ParticipantJoinedEvent.java`:

```java
package ch.junto.chat.event;

import java.util.UUID;

public record ParticipantJoinedEvent(UUID activityId, String displayName) {}
```

- [ ] **Step 2: Create ParticipantLeftEvent**

Create `backend/src/main/java/ch/junto/chat/event/ParticipantLeftEvent.java`:

```java
package ch.junto.chat.event;

import java.util.UUID;

public record ParticipantLeftEvent(UUID activityId, String displayName) {}
```

- [ ] **Step 3: Create ChatEventListener**

Create `backend/src/main/java/ch/junto/chat/event/ChatEventListener.java`:

```java
package ch.junto.chat.event;

import ch.junto.chat.ChatService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ChatEventListener {

    private final ChatService chatService;

    public ChatEventListener(ChatService chatService) {
        this.chatService = chatService;
    }

    @EventListener
    public void onParticipantJoined(ParticipantJoinedEvent event) {
        chatService.sendSystemMessage(event.activityId(), event.displayName() + " joined the activity");
    }

    @EventListener
    public void onParticipantLeft(ParticipantLeftEvent event) {
        chatService.sendSystemMessage(event.activityId(), event.displayName() + " left the activity");
    }
}
```

- [ ] **Step 4: Publish events from ParticipantService**

Add `ApplicationEventPublisher` to `ParticipantService`. Add the field and constructor parameter:

```java
private final ApplicationEventPublisher eventPublisher;

public ParticipantService(
    ParticipantRepository participantRepository,
    ActivityRepository activityRepository,
    ApplicationEventPublisher eventPublisher
) {
    this.participantRepository = participantRepository;
    this.activityRepository = activityRepository;
    this.eventPublisher = eventPublisher;
}
```

Add these imports:
```java
import org.springframework.context.ApplicationEventPublisher;
import ch.junto.chat.event.ParticipantJoinedEvent;
import ch.junto.chat.event.ParticipantLeftEvent;
```

In the `join()` method, after `participant = participantRepository.save(participant);`, add:
```java
if (participant.getStatus() == ParticipantStatus.APPROVED) {
    eventPublisher.publishEvent(new ParticipantJoinedEvent(activityId, user.getDisplayName()));
}
```

In the `approve()` method, after `participantRepository.save(participant);`, add:
```java
eventPublisher.publishEvent(new ParticipantJoinedEvent(
    participant.getActivity().getId(), participant.getUser().getDisplayName()));
```

In the `leave()` method, after `participantRepository.delete(participant);`, add:
```java
eventPublisher.publishEvent(new ParticipantLeftEvent(activityId, user.getDisplayName()));
```

- [ ] **Step 5: Verify compilation and existing tests pass**

Run:
```bash
cd backend && ./gradlew test
```

Expected: All tests PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/ch/junto/chat/event/ backend/src/main/java/ch/junto/participant/ParticipantService.java
git commit -m "feat: publish participant events and generate system chat messages on join/leave"
```

---

## Task 5: ParticipantFixture and chat integration tests

**Files:**
- Create: `backend/src/testFixtures/java/ch/junto/fixtures/ParticipantFixture.java`
- Create: `backend/src/test/java/ch/junto/chat/ChatIntegrationTest.java`

- [ ] **Step 1: Create ParticipantFixture**

Create `backend/src/testFixtures/java/ch/junto/fixtures/ParticipantFixture.java`:

```java
package ch.junto.fixtures;

import ch.junto.activity.entity.Activity;
import ch.junto.participant.entity.ActivityParticipant;
import ch.junto.participant.enums.ParticipantRole;
import ch.junto.participant.enums.ParticipantStatus;
import ch.junto.user.entity.User;

public final class ParticipantFixture {

    private ParticipantFixture() {}

    /** Returns an APPROVED CREATOR participant (for Integration Tests / DatabaseHelper) */
    public static ActivityParticipant creator(Activity activity, User user) {
        ActivityParticipant p = new ActivityParticipant();
        p.setActivity(activity);
        p.setUser(user);
        p.setRole(ParticipantRole.CREATOR);
        p.setStatus(ParticipantStatus.APPROVED);
        return p;
    }

    /** Returns an APPROVED PARTICIPANT (for Integration Tests / DatabaseHelper) */
    public static ActivityParticipant approved(Activity activity, User user) {
        ActivityParticipant p = new ActivityParticipant();
        p.setActivity(activity);
        p.setUser(user);
        p.setRole(ParticipantRole.PARTICIPANT);
        p.setStatus(ParticipantStatus.APPROVED);
        return p;
    }

    /** Returns a PENDING PARTICIPANT (for Integration Tests / DatabaseHelper) */
    public static ActivityParticipant pending(Activity activity, User user) {
        ActivityParticipant p = new ActivityParticipant();
        p.setActivity(activity);
        p.setUser(user);
        p.setRole(ParticipantRole.PARTICIPANT);
        p.setStatus(ParticipantStatus.PENDING);
        return p;
    }
}
```

- [ ] **Step 2: Write integration tests**

Create `backend/src/test/java/ch/junto/chat/ChatIntegrationTest.java`:

```java
package ch.junto.chat;

import ch.junto.activity.ActivityRepository;
import ch.junto.activity.entity.Activity;
import ch.junto.base.BaseIntegrationTest;
import ch.junto.fixtures.ActivityFixture;
import ch.junto.fixtures.ParticipantFixture;
import ch.junto.fixtures.UserFixture;
import ch.junto.helpers.DatabaseHelper;
import ch.junto.participant.ParticipantRepository;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Chat Integration")
class ChatIntegrationTest extends BaseIntegrationTest {

    @Autowired private ObjectMapper objectMapper;
    @Autowired private DatabaseHelper dbHelper;
    @Autowired private UserRepository userRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private ParticipantRepository participantRepository;
    @Autowired private ChatMessageRepository chatMessageRepository;

    private User creator;
    private User participant;
    private User outsider;
    private Activity activity;

    @BeforeEach
    void setUp() {
        creator = dbHelper.save(UserFixture.newUser("creator-" + UUID.randomUUID() + "@junto.app"));
        participant = dbHelper.save(UserFixture.newUser("participant-" + UUID.randomUUID() + "@junto.app"));
        outsider = dbHelper.save(UserFixture.newUser("outsider-" + UUID.randomUUID() + "@junto.app"));

        activity = dbHelper.save(ActivityFixture.newActivity(creator, "climbing"));
        dbHelper.save(ParticipantFixture.creator(activity, creator));
        dbHelper.save(ParticipantFixture.approved(activity, participant));
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

    @Test @DisplayName("POST message persists and is visible in GET")
    void shouldSendAndRetrieveMessage() throws Exception {
        mockMvc.perform(post("/api/activities/" + activity.getId() + "/messages")
                .session(buildSession(participant))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("content", "Hello everyone!"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").value("Hello everyone!"))
            .andExpect(jsonPath("$.type").value("USER"))
            .andExpect(jsonPath("$.displayName").value(participant.getDisplayName()));

        mockMvc.perform(get("/api/activities/" + activity.getId() + "/messages")
                .session(buildSession(participant)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].content").value("Hello everyone!"));
    }

    @Test @DisplayName("non-participant gets 403 on POST")
    void shouldRejectNonParticipantPost() throws Exception {
        mockMvc.perform(post("/api/activities/" + activity.getId() + "/messages")
                .session(buildSession(outsider))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("content", "Sneaky message"))))
            .andExpect(status().isForbidden());
    }

    @Test @DisplayName("non-participant gets 403 on GET")
    void shouldRejectNonParticipantGet() throws Exception {
        mockMvc.perform(get("/api/activities/" + activity.getId() + "/messages")
                .session(buildSession(outsider)))
            .andExpect(status().isForbidden());
    }

    @Test @DisplayName("message over 2000 chars returns 400")
    void shouldRejectOversizedMessage() throws Exception {
        String longContent = "a".repeat(2001);

        mockMvc.perform(post("/api/activities/" + activity.getId() + "/messages")
                .session(buildSession(participant))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("content", longContent))))
            .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("blank message returns 400")
    void shouldRejectBlankMessage() throws Exception {
        mockMvc.perform(post("/api/activities/" + activity.getId() + "/messages")
                .session(buildSession(participant))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("content", "   "))))
            .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("GET returns messages in ascending order")
    void shouldReturnMessagesInAscOrder() throws Exception {
        MockHttpSession session = buildSession(creator);

        mockMvc.perform(post("/api/activities/" + activity.getId() + "/messages")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("content", "First"))))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/activities/" + activity.getId() + "/messages")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("content", "Second"))))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/activities/" + activity.getId() + "/messages")
                .session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].content").value("First"))
            .andExpect(jsonPath("$[1].content").value("Second"));
    }

    @Test @DisplayName("cursor pagination returns older messages")
    void shouldPaginateWithCursor() throws Exception {
        MockHttpSession session = buildSession(creator);

        // Send 3 messages
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/api/activities/" + activity.getId() + "/messages")
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("content", "Message " + i))))
                .andExpect(status().isOk());
        }

        // Get all messages to find the last one's ID
        String allMessages = mockMvc.perform(get("/api/activities/" + activity.getId() + "/messages")
                .session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(3)))
            .andReturn().getResponse().getContentAsString();

        // Extract the ID of the last message (Message 3) as cursor
        String lastId = objectMapper.readTree(allMessages).get(2).get("id").asText();

        // Paginate before last message — should get Message 1 and Message 2
        mockMvc.perform(get("/api/activities/" + activity.getId() + "/messages")
                .session(session)
                .param("before", lastId)
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].content").value("Message 1"))
            .andExpect(jsonPath("$[1].content").value("Message 2"));
    }
}
```

- [ ] **Step 3: Run integration tests**

Run:
```bash
cd backend && ./gradlew test --tests "ch.junto.chat.ChatIntegrationTest"
```

Expected: All 7 tests PASS.

- [ ] **Step 4: Run all tests to check for regressions**

Run:
```bash
cd backend && ./gradlew test
```

Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/testFixtures/java/ch/junto/fixtures/ParticipantFixture.java backend/src/test/java/ch/junto/chat/ChatIntegrationTest.java
git commit -m "test: add ParticipantFixture and chat integration tests"
```

---

## Task 6: Frontend useChat composable

**Files:**
- Create: `frontend/src/composables/useChat.ts`

- [ ] **Step 1: Create useChat composable**

Create `frontend/src/composables/useChat.ts`:

```typescript
import { ref, onMounted, onUnmounted, type Ref } from 'vue'
import { Client } from '@stomp/stompjs'

export interface ChatMessageData {
  id: string
  activityId: string
  userId: string | null
  displayName: string
  avatarUrl: string | null
  content: string
  type: 'USER' | 'SYSTEM'
  createdAt: string
  isMe: boolean
}

interface RawChatMessage {
  id: string
  activityId: string
  userId: string | null
  displayName: string
  avatarUrl: string | null
  content: string
  type: 'USER' | 'SYSTEM'
  createdAt: string
}

export function useChat(activityId: string, currentUserId: string) {
  const messages: Ref<ChatMessageData[]> = ref([])
  const connected = ref(false)
  const loading = ref(true)

  let stompClient: Client | null = null

  const toMessage = (raw: RawChatMessage): ChatMessageData => ({
    ...raw,
    isMe: raw.userId === currentUserId,
  })

  const fetchHistory = async () => {
    loading.value = true
    try {
      const res = await fetch(`/api/activities/${activityId}/messages?limit=50`)
      if (!res.ok) return
      const data: RawChatMessage[] = await res.json()
      messages.value = data.map(toMessage)
    } finally {
      loading.value = false
    }
  }

  const send = async (content: string) => {
    const trimmed = content.trim()
    if (!trimmed) return

    const xsrfToken = document.cookie
      .split('; ')
      .find((row) => row.startsWith('XSRF-TOKEN='))
      ?.split('=')[1]

    const headers: Record<string, string> = { 'Content-Type': 'application/json' }
    if (xsrfToken) headers['X-XSRF-TOKEN'] = xsrfToken

    const res = await fetch(`/api/activities/${activityId}/messages`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ content: trimmed }),
    })

    if (!res.ok) {
      console.error('Failed to send message:', res.status)
    }
    // Message will arrive via STOMP broadcast — no local push needed
  }

  const connectStomp = () => {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const wsUrl = `${protocol}//${window.location.host}/ws`

    stompClient = new Client({
      brokerURL: wsUrl,
      reconnectDelay: 3000,
      onConnect: () => {
        connected.value = true
        stompClient?.subscribe(`/topic/activity/${activityId}`, (frame) => {
          const msg: RawChatMessage = JSON.parse(frame.body)
          // Deduplicate — the message might already be in the list from history fetch
          if (!messages.value.some((m) => m.id === msg.id)) {
            messages.value.push(toMessage(msg))
          }
        })
      },
      onDisconnect: () => {
        connected.value = false
      },
      onStompError: (frame) => {
        console.error('STOMP error:', frame.headers['message'])
        connected.value = false
      },
      onWebSocketClose: () => {
        connected.value = false
      },
    })

    stompClient.activate()
  }

  onMounted(async () => {
    await fetchHistory()
    connectStomp()
  })

  onUnmounted(() => {
    if (stompClient) {
      stompClient.deactivate()
      stompClient = null
    }
  })

  return { messages, connected, loading, send }
}
```

- [ ] **Step 2: Verify TypeScript compiles**

Run:
```bash
cd frontend && npx vue-tsc --noEmit 2>&1 | tail -10
```

Expected: No errors related to `useChat.ts`.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/composables/useChat.ts
git commit -m "feat: add useChat composable with STOMP connection and REST history"
```

---

## Task 7: Wire useChat into ActivityDetailView

**Files:**
- Modify: `frontend/src/views/activities/ActivityDetailView.vue`

- [ ] **Step 1: Replace mock data with real chat integration**

Replace the entire `<script setup>` section of `ActivityDetailView.vue`. The key changes:
- Import and use `useChat` composable
- Remove local `messages` ref and mock data
- Remove `handleSendMessage` (replaced by `useChat.send`)
- Fetch real activity data and participants from API
- Use real `currentUserId` from auth

```vue
<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import {
  Activity as ActivityIcon,
  MapPin,
  Calendar,
  ShieldCheck,
  Lock
} from 'lucide-vue-next'
import PageContainer from '@/components/layout/PageContainer.vue'
import JoinButton from '@/components/participant/JoinButton.vue'
import ParticipantList from '@/components/participant/ParticipantList.vue'
import ChatPanel from '@/components/chat/ChatPanel.vue'
import { useChat } from '@/composables/useChat'
import { getDetail, listParticipants, join as joinActivity, approve as approveParticipant, decline as declineParticipant } from '@/api/generated'

const props = defineProps<{
  id: string
}>()

const activity = ref<any>(null)
const participants = ref<any[]>([])
const joining = ref(false)
const currentUserId = ref<string>('')

// Fetch current user ID from /api/users/me
const fetchCurrentUser = async () => {
  try {
    const res = await fetch('/api/users/me')
    if (res.ok) {
      const data = await res.json()
      currentUserId.value = data.id
    }
  } catch { /* handled by 401 interceptor */ }
}

const isCreator = computed(() => activity.value?.creatorId === currentUserId.value)
const userParticipant = computed(() => participants.value.find(p => p.userId === currentUserId.value))
const isApproved = computed(() => userParticipant.value?.status === 'APPROVED')

const userStatus = computed(() => {
  if (!activity.value) return 'NONE'
  if (userParticipant.value) return userParticipant.value.status
  const approvedCount = participants.value.filter(p => p.status === 'APPROVED').length
  if (approvedCount >= activity.value.maxParticipants) return 'FULL'
  if (new Date() > new Date(activity.value.effectiveDeadline)) return 'CLOSED'
  return 'NONE'
})

const typeColor = computed(() => {
  const colors: Record<string, string> = { climbing: 'bg-emerald-600', cycling: 'bg-blue-600', running: 'bg-orange-600', skiing: 'bg-sky-500', yoga: 'bg-indigo-500' }
  return colors[activity.value?.activityTypeSlug] || 'bg-slate-600'
})

const typeText = computed(() => {
  const colors: Record<string, string> = { climbing: 'text-emerald-600', cycling: 'text-blue-600', running: 'text-orange-600', skiing: 'text-sky-500', yoga: 'text-indigo-500' }
  return colors[activity.value?.activityTypeSlug] || 'text-slate-600'
})

const formatDate = (d: string) => d ? new Date(d).toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' }) : ''
const formatTime = (d: string) => d ? new Date(d).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' }) : ''

// Chat — initialized reactively once we know the user and they're approved
const chatState = ref<ReturnType<typeof useChat> | null>(null)

const handleJoin = async () => {
  if (userStatus.value !== 'NONE') return
  joining.value = true
  try {
    const res = await joinActivity({ path: { activityId: props.id } })
    if (res.data) {
      participants.value.push(res.data)
    }
  } finally {
    joining.value = false
  }
}

const handleApprove = async (id: string) => {
  await approveParticipant({ path: { activityId: props.id, participantId: id } })
  const p = participants.value.find(p => p.id === id)
  if (p) p.status = 'APPROVED'
}

const handleDecline = async (id: string) => {
  await declineParticipant({ path: { activityId: props.id, participantId: id } })
  const p = participants.value.find(p => p.id === id)
  if (p) p.status = 'DECLINED'
}

onMounted(async () => {
  await fetchCurrentUser()

  const [activityRes, participantsRes] = await Promise.all([
    getDetail({ path: { id: props.id } }),
    listParticipants({ path: { activityId: props.id } }),
  ])

  if (activityRes.data) activity.value = activityRes.data
  if (participantsRes.data) participants.value = participantsRes.data
})

// Initialize chat when user becomes an approved participant
watch(isApproved, (approved) => {
  if (approved && currentUserId.value && !chatState.value) {
    chatState.value = useChat(props.id, currentUserId.value)
  }
}, { immediate: true })
</script>
```

- [ ] **Step 2: Update the template to use chatState**

In the template, update the chat panel section. Replace:
```vue
<ChatPanel :messages="messages" @send="handleSendMessage" />
```
with:
```vue
<ChatPanel :messages="chatState?.messages.value ?? []" @send="chatState?.send" />
```

- [ ] **Step 3: Verify TypeScript compiles**

Run:
```bash
cd frontend && npx vue-tsc --noEmit 2>&1 | tail -10
```

Expected: No errors. (Note: the generated API functions like `getDetail`, `listParticipants`, `joinActivity` etc. may not exist yet if the OpenAPI spec hasn't been regenerated. If so, use raw `fetch` calls instead — follow the same pattern as `useChat`'s `send` method. The generated client can be updated later when the OpenAPI spec is regenerated.)

- [ ] **Step 4: Commit**

```bash
git add frontend/src/views/activities/ActivityDetailView.vue
git commit -m "feat: wire real-time chat into ActivityDetailView replacing mock data"
```

---

## Task 8: Manual verification

- [ ] **Step 1: Start the backend**

Run:
```bash
cd backend && ./gradlew bootRun
```

Expected: Application starts on port 8080 with no errors.

- [ ] **Step 2: Start the frontend**

Run:
```bash
cd frontend && npm run dev
```

Expected: Vite dev server starts.

- [ ] **Step 3: Test chat as approved participant**

1. Navigate to an activity detail page as an approved participant
2. Verify chat history loads (or shows "No messages yet")
3. Send a message — verify it appears instantly
4. Check browser console for STOMP connection logs (no errors)

- [ ] **Step 4: Test chat as non-participant**

1. Navigate to an activity where you're not a participant
2. Verify the "Chat is locked" state appears instead of the chat panel

- [ ] **Step 5: Test real-time broadcast**

1. Open the same activity in two browser tabs/windows (as different approved participants)
2. Send a message in one tab
3. Verify it appears in the other tab in real-time

- [ ] **Step 6: Test system messages**

1. Have a user join an activity with OPEN join policy
2. Verify a system message "{name} joined the activity" appears in the chat

- [ ] **Step 7: Final commit (if any fixes needed)**

```bash
git add -A
git commit -m "fix: address issues found during manual verification"
```
