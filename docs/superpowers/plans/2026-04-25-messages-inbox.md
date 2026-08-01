# Messages Inbox Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Messages screen — a split-pane inbox showing activity chat threads on the left and the selected chat on the right, matching the Junto design prototype.

**Architecture:** New backend endpoint (`GET /api/messages/threads`) returns activities the user has joined as approved participant, enriched with last message info. Frontend adds a `/messages` route with a `MessagesView` component that uses the existing `useChat` composable for real-time messaging. Desktop shows split pane; mobile shows thread list with tap-to-open chat.

**Tech Stack:** Spring Boot (backend), Vue 3 + Composition API + TypeScript + Pinia + Tailwind (frontend), STOMP over WebSocket (real-time), `@hey-api/client-fetch` (HTTP).

---

## File Structure

### Backend (create)
- `backend/src/main/java/ch/junto/message/MessageController.java` — `GET /api/messages/threads`
- `backend/src/main/java/ch/junto/message/MessageThreadService.java` — queries joined activities + last message
- `backend/src/main/java/ch/junto/message/dto/MessageThreadResponse.java` — thread list item DTO

### Backend (modify)
- `backend/src/main/java/ch/junto/chat/ChatMessageRepository.java` — add `LastMessageProjection` interface + batch query
- `backend/src/main/java/ch/junto/participant/ParticipantRepository.java` — add `findActivityIdsByUserIdAndStatus` projection query

### Frontend (create)
- `frontend/src/views/messages/MessagesView.vue` — main split-pane view
- `frontend/src/views/messages/ThreadList.vue` — left panel: search + thread rows
- `frontend/src/views/messages/ChatPanel.vue` — right panel: header + messages + input (design-prototype styled)
- `frontend/src/composables/useMessageThreads.ts` — fetches and manages thread list state

### Frontend (modify)
- `frontend/src/router/index.ts` — add `/messages` and `/messages/:activityId` routes
- `frontend/src/components/layout/Sidebar.vue` — fix Messages nav active state to use `startsWith` (like Feed does)
- `frontend/src/api/generated/sdk.gen.ts` — regenerate after OpenAPI update (or add manual wrapper)

---

### Task 1: Backend — Message thread DTO

**Files:**
- Create: `backend/src/main/java/ch/junto/message/dto/MessageThreadResponse.java`

- [ ] **Step 1: Create the DTO**

```java
package ch.junto.message.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record MessageThreadResponse(
    UUID activityId,
    String activityTitle,
    String activityTypeSlug,
    String activityTypeName,
    String imageUrl,
    int participantCount,
    List<String> participantAvatars,
    // Last message — null if no messages exist yet
    String lastMessageContent,
    String lastMessageSender,
    OffsetDateTime lastMessageAt
) {}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/ch/junto/message/dto/MessageThreadResponse.java
git commit -m "feat: add MessageThreadResponse DTO"
```

---

### Task 2: Backend — Repository query for last messages

**Files:**
- Modify: `backend/src/main/java/ch/junto/chat/ChatMessageRepository.java`

- [ ] **Step 1: Add interface projection and batch query for latest messages**

Add this interface projection inside `ChatMessageRepository` (or as a separate file):

```java
public interface LastMessageProjection {
    UUID getActivityId();
    String getLastContent();
    String getLastSender();
    OffsetDateTime getLastAt();
}
```

Add this method to `ChatMessageRepository`:

```java
@Query(value = """
    SELECT DISTINCT ON (cm.activity_id)
        cm.activity_id AS activityId,
        cm.content AS lastContent,
        u.display_name AS lastSender,
        cm.created_at AS lastAt
    FROM chat_messages cm
    LEFT JOIN users u ON u.id = cm.user_id
    WHERE cm.activity_id IN :activityIds
      AND cm.type = 'USER'
    ORDER BY cm.activity_id, cm.created_at DESC
    """, nativeQuery = true)
List<LastMessageProjection> findLatestMessagesByActivityIds(@Param("activityIds") List<UUID> activityIds);
```

**Why interface projection instead of `Object[]`:** Native queries return `java.sql.Timestamp` for timestamp columns, not `OffsetDateTime`. Casting `(OffsetDateTime) row[3]` would throw `ClassCastException`. Spring's interface projection handles the type conversion automatically.

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/ch/junto/chat/ChatMessageRepository.java
git commit -m "feat: add query for latest messages by activity IDs"
```

---

### Task 3: Backend — MessageThreadService

**Files:**
- Create: `backend/src/main/java/ch/junto/message/MessageThreadService.java`

- [ ] **Step 1: Create the service**

```java
package ch.junto.message;

import ch.junto.activity.entity.Activity;
import ch.junto.activity.ActivityRepository;
import ch.junto.chat.ChatMessageRepository;
import ch.junto.chat.ChatMessageRepository.LastMessageProjection;
import ch.junto.message.dto.MessageThreadResponse;
import ch.junto.participant.ParticipantRepository;
import ch.junto.participant.enums.ParticipantStatus;
import ch.junto.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class MessageThreadService {

    private final ParticipantRepository participantRepository;
    private final ActivityRepository activityRepository;
    private final ChatMessageRepository chatMessageRepository;

    public MessageThreadService(
            ParticipantRepository participantRepository,
            ActivityRepository activityRepository,
            ChatMessageRepository chatMessageRepository) {
        this.participantRepository = participantRepository;
        this.activityRepository = activityRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    public List<MessageThreadResponse> getThreads(User user) {
        // 1. Find all activity IDs where user is APPROVED participant
        List<UUID> activityIds = participantRepository
            .findActivityIdsByUserIdAndStatus(user.getId(), ParticipantStatus.APPROVED);

        if (activityIds.isEmpty()) {
            return List.of();
        }

        // 2. Load activities
        List<Activity> activities = activityRepository.findAllById(activityIds);
        Map<UUID, Activity> activityMap = activities.stream()
            .collect(Collectors.toMap(Activity::getId, a -> a));

        // 3. Get last message for each activity (uses interface projection)
        Map<UUID, LastMessageProjection> lastMessageMap = chatMessageRepository
            .findLatestMessagesByActivityIds(activityIds)
            .stream()
            .collect(Collectors.toMap(LastMessageProjection::getActivityId, m -> m));

        // 4. Get participant counts in batch
        Map<UUID, Long> countMap = new HashMap<>();
        for (UUID actId : activityIds) {
            countMap.put(actId, participantRepository.countByActivityIdAndStatus(actId, ParticipantStatus.APPROVED));
        }

        // 5. Build responses
        List<MessageThreadResponse> threads = new ArrayList<>();
        for (UUID actId : activityIds) {
            Activity a = activityMap.get(actId);
            if (a == null) continue;

            LastMessageProjection lastMsg = lastMessageMap.get(actId);

            threads.add(new MessageThreadResponse(
                actId,
                a.getTitle(),
                a.getTypeSlug(),
                a.getTypeSlug(), // typeName — resolved on frontend from activityTypes
                a.getImageUrl(),
                countMap.getOrDefault(actId, 0L).intValue(),
                List.of(), // participantAvatars — skip for v1
                lastMsg != null ? lastMsg.getLastContent() : null,
                lastMsg != null ? lastMsg.getLastSender() : null,
                lastMsg != null ? lastMsg.getLastAt() : null
            ));
        }

        // 6. Sort: threads with messages first, by last message time desc;
        //    threads without messages at the end
        threads.sort((a, b) -> {
            if (a.lastMessageAt() == null && b.lastMessageAt() == null) return 0;
            if (a.lastMessageAt() == null) return 1;
            if (b.lastMessageAt() == null) return -1;
            return b.lastMessageAt().compareTo(a.lastMessageAt());
        });

        return threads;
    }
}
```

**Notes:**
- Requires `findActivityIdsByUserIdAndStatus` in `ParticipantRepository` (see Task 4) — a projection query returning `List<UUID>` to avoid lazy-loading `Activity` entities just to extract IDs.
- Uses the existing `countByActivityIdAndStatus` (already in `ParticipantRepository`) for correct per-activity participant counts.
- Uses `LastMessageProjection` interface from Task 2 for type-safe native query results.

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/ch/junto/message/MessageThreadService.java
git commit -m "feat: add MessageThreadService for fetching chat threads"
```

---

### Task 4: Backend — ParticipantRepository query + MessageController

**Files:**
- Modify: `backend/src/main/java/ch/junto/participant/ParticipantRepository.java`
- Create: `backend/src/main/java/ch/junto/message/MessageController.java`

- [ ] **Step 1: Add query to ParticipantRepository**

Add to `ParticipantRepository.java`:

```java
@Query("SELECT p.activity.id FROM ActivityParticipant p WHERE p.user.id = :userId AND p.status = :status")
List<UUID> findActivityIdsByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") ParticipantStatus status);
```

**Why a JPQL projection instead of `findAllByUserIdAndStatus`:** The `Activity` relation is `FetchType.LAZY`. Loading full `ActivityParticipant` entities and then calling `p.getActivity().getId()` triggers N lazy-load queries. A projection query returns just the UUIDs in a single query.

- [ ] **Step 2: Create MessageController**

```java
package ch.junto.message;

import ch.junto.auth.CurrentUser;
import ch.junto.message.dto.MessageThreadResponse;
import ch.junto.user.entity.User;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageThreadService threadService;

    public MessageController(MessageThreadService threadService) {
        this.threadService = threadService;
    }

    @GetMapping("/threads")
    public List<MessageThreadResponse> getThreads(@CurrentUser User user) {
        return threadService.getThreads(user);
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/ch/junto/participant/ParticipantRepository.java \
       backend/src/main/java/ch/junto/message/MessageController.java
git commit -m "feat: add GET /api/messages/threads endpoint"
```

---

### Task 5: Backend — Integration test

**Files:**
- Create: `backend/src/test/java/ch/junto/message/MessageThreadIntegrationTest.java`

- [ ] **Step 1: Write the integration test**

Follow the existing test pattern from `ChatIntegrationTest`: extend `BaseIntegrationTest`, use `MockMvc`, `DatabaseHelper`, `UserFixture`, `ActivityFixture`, and `MockHttpSession` for auth.

```java
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
        session = authenticatedSession(user);
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
        addApprovedParticipant(activity, user, ParticipantRole.CREATOR);

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
        addApprovedParticipant(activity, host, ParticipantRole.CREATOR);
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

    // Helper: create authenticated MockHttpSession
    private MockHttpSession authenticatedSession(User user) {
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        MockHttpSession s = new MockHttpSession();
        s.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx);
        return s;
    }

    // Helper: add participant with APPROVED status
    private void addApprovedParticipant(Activity activity, User user, ParticipantRole role) {
        addParticipant(activity, user, role, ParticipantStatus.APPROVED);
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
```

**Note:** This follows the same pattern as `ChatIntegrationTest` — uses `BaseIntegrationTest` (from `testFixtures`), `MockMvc`, `DatabaseHelper`, `UserFixture`/`ActivityFixture`, and `MockHttpSession` for authentication. Does NOT use `TestRestTemplate` or `createAuthenticatedUser` (which don't exist in this project).

- [ ] **Step 2: Run test and verify**

Run: `cd backend && ./gradlew test --tests "ch.junto.message.*"`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/ch/junto/message/MessageThreadIntegrationTest.java
git commit -m "test: add integration test for message threads endpoint"
```

---

### Task 6: Frontend — Add routes

**Files:**
- Modify: `frontend/src/router/index.ts`

- [ ] **Step 1: Add messages routes**

In `frontend/src/router/index.ts`, add two children to the `'/'` route's `children` array, after the `activities/new` route:

```typescript
{
  path: 'messages',
  name: 'messages',
  component: () => import('@/views/messages/MessagesView.vue')
},
{
  path: 'messages/:activityId',
  name: 'messages-chat',
  component: () => import('@/views/messages/MessagesView.vue'),
  props: true
},
```

- [ ] **Step 2: Fix Sidebar active state for Messages**

In `frontend/src/components/layout/Sidebar.vue`, the Messages nav link uses `$route.path === '/messages'` which won't highlight when viewing `/messages/:activityId`. Change to `startsWith`:

```vue
:class="[$route.path.startsWith('/messages') ? 'bg-primary-50 text-primary-700' : 'text-[#6A6560] hover:bg-warm-50 hover:text-slate-900']"
```

Similarly in `frontend/src/components/layout/MobileNavBar.vue`:

```vue
:class="[$route.path.startsWith('/messages') ? 'text-primary-600' : 'text-slate-400']"
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/router/index.ts \
       frontend/src/components/layout/Sidebar.vue \
       frontend/src/components/layout/MobileNavBar.vue
git commit -m "feat: add /messages routes and fix nav active state"
```

---

### Task 7: Frontend — useMessageThreads composable

**Files:**
- Create: `frontend/src/composables/useMessageThreads.ts`

- [ ] **Step 1: Create the composable**

```typescript
import { ref, onMounted } from 'vue'

export interface MessageThread {
  activityId: string
  activityTitle: string
  activityTypeSlug: string
  activityTypeName: string
  imageUrl: string | null
  participantCount: number
  participantAvatars: string[]
  lastMessageContent: string | null
  lastMessageSender: string | null
  lastMessageAt: string | null
}

export function useMessageThreads() {
  const threads = ref<MessageThread[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchThreads() {
    loading.value = true
    error.value = null
    try {
      const res = await fetch('/api/messages/threads', { credentials: 'same-origin' })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      threads.value = await res.json()
    } catch (e: any) {
      error.value = e.message
    } finally {
      loading.value = false
    }
  }

  onMounted(fetchThreads)

  return { threads, loading, error, refresh: fetchThreads }
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/composables/useMessageThreads.ts
git commit -m "feat: add useMessageThreads composable"
```

---

### Task 8: Frontend — ThreadList component

**Files:**
- Create: `frontend/src/views/messages/ThreadList.vue`

This component renders the left panel thread list matching the design prototype. Key design elements:
- "Messages" heading in Playfair Display
- Search bar (round, warm border)
- Thread rows: activity image circle (left), title + preview + time (right), unread badge (green pill)
- Active thread: green left border + `primary-50` background tint
- On mobile: full-width list, tapping navigates to `/messages/:activityId`

- [ ] **Step 1: Create ThreadList.vue**

```vue
<template>
  <div class="h-full flex flex-col">
    <!-- Header -->
    <div class="px-4 pb-3">
      <h1 class="font-display text-2xl text-slate-900 mb-3">Messages</h1>
      <div class="flex items-center gap-2 bg-warm-50 border border-warm-300 rounded-full px-3.5 py-1.5">
        <Search class="w-3.5 h-3.5 text-[#9D998F]" />
        <input
          v-model="search"
          type="text"
          placeholder="Search messages"
          class="bg-transparent border-none outline-none text-sm text-slate-900 placeholder-[#9D998F] w-full"
        />
      </div>
    </div>

    <!-- Thread list -->
    <div class="flex-1 overflow-y-auto">
      <div v-if="loading" class="px-4 py-8 text-center text-sm text-[#9D998F]">Loading...</div>
      <div v-else-if="filteredThreads.length === 0" class="px-4 py-12 text-center text-sm text-[#9D998F]">
        No conversations yet
      </div>
      <div v-else>
        <div
          v-for="thread in filteredThreads"
          :key="thread.activityId"
          @click="$emit('select', thread)"
          :class="[
            'flex gap-3 px-4 py-3 cursor-pointer transition-colors',
            activeId === thread.activityId
              ? 'bg-primary-50 border-l-[3px] border-primary-700'
              : 'border-l-[3px] border-transparent hover:bg-warm-50'
          ]"
        >
          <!-- Activity image/icon circle -->
          <div class="w-10 h-10 rounded-xl flex-shrink-0 overflow-hidden"
               :style="{ background: typeGradient(thread.activityTypeSlug) }">
            <img v-if="thread.imageUrl" :src="thread.imageUrl" class="w-full h-full object-cover" />
            <div v-else class="w-full h-full flex items-center justify-center text-white text-xs font-bold uppercase">
              {{ thread.activityTypeSlug?.slice(0, 2) }}
            </div>
          </div>

          <!-- Content -->
          <div class="flex-1 min-w-0">
            <div class="flex justify-between items-center">
              <span class="text-[13px] font-semibold text-slate-900 truncate">
                {{ thread.activityTitle }}
              </span>
              <span class="text-[11px] text-[#9D998F] flex-shrink-0 ml-2">
                {{ formatRelativeTime(thread.lastMessageAt) }}
              </span>
            </div>
            <div class="flex justify-between items-center mt-0.5">
              <span class="text-xs text-[#6A6560] truncate max-w-[180px]">
                <template v-if="thread.lastMessageContent">
                  <span class="font-medium text-slate-700">{{ thread.lastMessageSender }}:</span>
                  {{ thread.lastMessageContent }}
                </template>
                <template v-else>No messages yet</template>
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Search } from 'lucide-vue-next'
import type { MessageThread } from '@/composables/useMessageThreads'

const props = defineProps<{
  threads: MessageThread[]
  activeId: string | null
  loading: boolean
}>()

defineEmits<{ select: [thread: MessageThread] }>()

const search = ref('')

const filteredThreads = computed(() => {
  if (!search.value.trim()) return props.threads
  const q = search.value.toLowerCase()
  return props.threads.filter(t =>
    t.activityTitle.toLowerCase().includes(q) ||
    (t.lastMessageContent?.toLowerCase().includes(q))
  )
})

const typeGradients: Record<string, string> = {
  cycling: 'linear-gradient(135deg, #7BAE7F, #4A7A52)',
  climbing: 'linear-gradient(135deg, #A89880, #7A6855)',
  skiing: 'linear-gradient(135deg, #B8D4E8, #7AA8C8)',
  running: 'linear-gradient(135deg, #C4855A, #9B6040)',
  hiking: 'linear-gradient(135deg, #6B9B7A, #4A7B5A)',
  yoga: 'linear-gradient(135deg, #A78BFA, #7C3AED)',
}

function typeGradient(slug: string) {
  return typeGradients[slug] || 'linear-gradient(135deg, #94A3B8, #64748B)'
}

function formatRelativeTime(dateStr: string | null) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMin = Math.floor(diffMs / 60000)
  if (diffMin < 1) return 'now'
  if (diffMin < 60) return `${diffMin}m`
  const diffHr = Math.floor(diffMin / 60)
  if (diffHr < 24) return `${diffHr}h`
  const diffDay = Math.floor(diffHr / 24)
  if (diffDay < 7) return `${diffDay}d`
  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
}
</script>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/views/messages/ThreadList.vue
git commit -m "feat: add ThreadList component for message inbox"
```

---

### Task 9: Frontend — Messages ChatPanel component

**Files:**
- Create: `frontend/src/views/messages/ChatPanel.vue`

This is the right-panel chat view, styled to match the design prototype (not the existing `components/chat/ChatPanel.vue` which uses slate colors). It uses the existing `useChat` composable for data.

Design elements:
- Header: activity image circle + title + participant count
- Messages: own messages in green (`bg-primary-700`), others in white with warm border
- Own messages: rounded bubble with bottom-right flat corner (`rounded-2xl rounded-br-sm`)
- Others: rounded bubble with bottom-left flat corner (`rounded-2xl rounded-bl-sm`)
- System messages: centered, muted, italic
- Timestamps below each bubble, small muted text
- Input bar: rounded pill input + green circle send button

- [ ] **Step 1: Create ChatPanel.vue**

```vue
<template>
  <div class="h-full flex flex-col bg-white">
    <!-- Header -->
    <div v-if="thread" class="px-5 py-3.5 border-b border-warm-300 flex items-center gap-3 flex-shrink-0">
      <!-- Back button (mobile only) -->
      <button
        v-if="isMobile"
        @click="$emit('back')"
        class="text-slate-900 p-0"
      >
        <ChevronLeft class="w-5 h-5" />
      </button>
      <div class="w-9 h-9 rounded-xl flex-shrink-0 overflow-hidden"
           :style="{ background: typeGradient(thread.activityTypeSlug) }">
        <img v-if="thread.imageUrl" :src="thread.imageUrl" class="w-full h-full object-cover" />
      </div>
      <div>
        <div class="text-sm font-semibold text-slate-900">{{ thread.activityTitle }}</div>
        <div class="text-xs text-[#9D998F]">{{ thread.participantCount }} participants</div>
      </div>
    </div>

    <!-- Empty state -->
    <div v-if="!thread" class="flex-1 flex items-center justify-center text-[#9D998F] text-sm">
      Select a conversation
    </div>

    <!-- Messages -->
    <div v-else ref="scrollArea" class="flex-1 overflow-y-auto px-5 py-4 flex flex-col gap-3">
      <div v-if="messages.length === 0" class="flex-1 flex items-center justify-center text-[#9D998F] text-sm">
        No messages yet. Say hello!
      </div>
      <template v-for="msg in messages" :key="msg.id">
        <!-- System message -->
        <div v-if="msg.type === 'SYSTEM'" class="text-center text-xs text-[#9D998F] italic py-1">
          {{ msg.content }}
        </div>
        <!-- Own message -->
        <div v-else-if="msg.userId === currentUserId" class="flex justify-end gap-2 items-end">
          <div>
            <div class="bg-primary-700 text-white px-3.5 py-2.5 rounded-2xl rounded-br-sm text-sm max-w-[260px] shadow-sm">
              {{ msg.content }}
            </div>
            <div class="text-[10px] text-[#9D998F] mt-1 text-right mr-1">{{ formatTime(msg.createdAt) }}</div>
          </div>
        </div>
        <!-- Other user message -->
        <div v-else class="flex gap-2 items-end">
          <div class="w-7 h-7 rounded-full bg-warm-200 border border-warm-300 flex-shrink-0 overflow-hidden">
            <img v-if="msg.avatarUrl" :src="msg.avatarUrl" class="w-full h-full object-cover" />
          </div>
          <div>
            <div class="text-[10px] font-medium text-[#9D998F] ml-1 mb-0.5">{{ msg.displayName }}</div>
            <div class="bg-white border border-warm-300 text-slate-700 px-3.5 py-2.5 rounded-2xl rounded-bl-sm text-sm max-w-[260px] shadow-sm">
              {{ msg.content }}
            </div>
            <div class="text-[10px] text-[#9D998F] mt-1 ml-1">{{ formatTime(msg.createdAt) }}</div>
          </div>
        </div>
      </template>
      <div ref="bottomRef" />
    </div>

    <!-- Input -->
    <div v-if="thread" class="px-4 py-3 border-t border-warm-300 flex gap-2.5 items-center flex-shrink-0">
      <input
        v-model="input"
        @keydown.enter="handleSend"
        type="text"
        placeholder="Write a message..."
        class="flex-1 bg-warm-50 border border-warm-300 rounded-full px-4 py-2.5 text-sm outline-none focus:border-primary-500 text-slate-900 placeholder-[#9D998F]"
      />
      <button
        @click="handleSend"
        :disabled="!input.trim()"
        class="w-9 h-9 rounded-full bg-primary-700 text-white flex items-center justify-center disabled:opacity-40 flex-shrink-0"
      >
        <Send class="w-4 h-4" />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, computed } from 'vue'
import { ChevronLeft, Send } from 'lucide-vue-next'
import { useChat } from '@/composables/useChat'
import { useAuthStore } from '@/stores/auth'
import type { MessageThread } from '@/composables/useMessageThreads'

const props = defineProps<{
  thread: MessageThread | null
  isMobile: boolean
}>()

defineEmits<{ back: [] }>()

const auth = useAuthStore()
const currentUserId = computed(() => auth.user?.id ?? '')
const input = ref('')
const scrollArea = ref<HTMLElement | null>(null)
const bottomRef = ref<HTMLElement | null>(null)

// ChatPanel is keyed by activityId in MessagesView, so this only runs once per thread
const { messages, send } = useChat(props.thread?.activityId ?? '')

function handleSend() {
  if (!input.value.trim()) return
  send(input.value)
  input.value = ''
}

// Auto-scroll to bottom on new messages
watch(() => messages.value.length, async () => {
  await nextTick()
  if (scrollArea.value) {
    scrollArea.value.scrollTop = scrollArea.value.scrollHeight
  }
})

const typeGradients: Record<string, string> = {
  cycling: 'linear-gradient(135deg, #7BAE7F, #4A7A52)',
  climbing: 'linear-gradient(135deg, #A89880, #7A6855)',
  skiing: 'linear-gradient(135deg, #B8D4E8, #7AA8C8)',
  running: 'linear-gradient(135deg, #C4855A, #9B6040)',
  hiking: 'linear-gradient(135deg, #6B9B7A, #4A7B5A)',
  yoga: 'linear-gradient(135deg, #A78BFA, #7C3AED)',
}
function typeGradient(slug: string) {
  return typeGradients[slug] || 'linear-gradient(135deg, #94A3B8, #64748B)'
}

function formatTime(ts: string) {
  return new Date(ts).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
}
</script>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/views/messages/ChatPanel.vue
git commit -m "feat: add ChatPanel component for messages view"
```

---

### Task 10: Frontend — MessagesView (main container)

**Files:**
- Create: `frontend/src/views/messages/MessagesView.vue`

This is the main container that assembles the split-pane layout:
- Desktop: `ThreadList` (left, 280px) | `ChatPanel` (right, flex-1), separated by warm border
- Mobile: Shows `ThreadList` when no thread selected, `ChatPanel` when thread selected

- [ ] **Step 1: Create MessagesView.vue**

```vue
<template>
  <PageContainer>
    <div class="h-[calc(100vh-8rem)] flex gap-0 bg-white rounded-2xl border border-warm-200 shadow-card overflow-hidden">
      <!-- Thread list (always visible on desktop, hidden when chat open on mobile) -->
      <div
        :class="[
          'md:w-[280px] md:flex-shrink-0 md:border-r md:border-warm-300',
          'flex flex-col',
          isMobile && activeThread ? 'hidden' : 'w-full'
        ]"
      >
        <ThreadList
          :threads="threads"
          :active-id="activeThread?.activityId ?? null"
          :loading="loading"
          @select="handleSelect"
        />
      </div>

      <!-- Chat panel (always visible on desktop, shown when thread selected on mobile) -->
      <div
        :class="[
          'md:flex-1 flex flex-col',
          isMobile && !activeThread ? 'hidden' : 'w-full'
        ]"
      >
        <ChatPanel
          :key="activeThread?.activityId ?? ''"
          :thread="activeThread"
          :is-mobile="isMobile"
          @back="activeThread = null"
        />
      </div>
    </div>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageContainer from '@/components/layout/PageContainer.vue'
import ThreadList from './ThreadList.vue'
import ChatPanel from './ChatPanel.vue'
import { useMessageThreads, type MessageThread } from '@/composables/useMessageThreads'

const route = useRoute()
const router = useRouter()
const { threads, loading } = useMessageThreads()
const activeThread = ref<MessageThread | null>(null)

const isMobile = computed(() => window.innerWidth < 768)

// If route has activityId param, set active thread
function syncFromRoute() {
  const activityId = (route.params as any).activityId as string | undefined
  if (activityId) {
    const thread = threads.value.find(t => t.activityId === activityId) ?? null
    if (thread) {
      activeThread.value = thread
    }
  }
}

watch(() => route.params, syncFromRoute)
onMounted(syncFromRoute)

function handleSelect(thread: MessageThread) {
  activeThread.value = thread
  if (isMobile.value) {
    router.push(`/messages/${thread.activityId}`)
  }
}
</script>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/views/messages/MessagesView.vue
git commit -m "feat: add MessagesView with split-pane layout"
```

---

### Task 11: Manual verification

- [ ] **Step 1: Start backend**

Run: `cd backend && ./gradlew bootRun`

- [ ] **Step 2: Start frontend**

Run: `cd frontend && npm run dev`

- [ ] **Step 3: Verify desktop split pane**

Open `/messages` in browser. Check:
- Left panel shows thread list with "Messages" heading, search bar, thread rows
- Thread rows show activity image, title, last message preview, time
- Clicking a thread highlights it and opens chat on the right
- Sending a message works (green bubble, appears immediately)
- Receiving messages via STOMP works (other user sends, bubble appears)
- Thread list is sorted by last message time

- [ ] **Step 4: Verify mobile behavior**

Resize to mobile width or use device emulation:
- Thread list takes full width
- Tapping a thread opens full-screen chat with back button
- Back button returns to thread list
- Chat input and send button work

- [ ] **Step 5: Verify edge cases**
- No joined activities: empty state message
- Activity with no messages: "No messages yet" in chat panel
- No thread selected: "Select a conversation" placeholder
