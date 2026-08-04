import { ref, onMounted, onUnmounted } from 'vue';
import { Client, type IMessage } from '@stomp/stompjs';
import { useAuthStore } from '@/stores/auth';

export interface ChatMessage {
  id: string;
  activityId: string;
  userId: string | null;
  displayName: string;
  avatarUrl: string | null;
  content: string;
  type: 'USER' | 'SYSTEM';
  createdAt: string;
}

export function reconcileSavedMessage(
  current: ChatMessage[],
  tempId: string,
  saved: ChatMessage,
): ChatMessage[] {
  const originalIndex = current.findIndex((message) => message.id === tempId);
  const withoutOptimisticAndEcho = current.filter(
    (message) => message.id !== tempId && message.id !== saved.id,
  );
  withoutOptimisticAndEcho.splice(
    originalIndex < 0
      ? withoutOptimisticAndEcho.length
      : Math.min(originalIndex, withoutOptimisticAndEcho.length),
    0,
    saved,
  );
  return withoutOptimisticAndEcho;
}

export function useChat(activityId: string) {
  const messages = ref<ChatMessage[]>([]);
  const connected = ref(false);
  const sendError = ref<string | null>(null);
  const auth = useAuthStore();

  let stompClient: Client | null = null;
  const pendingTempIds = new Set<string>();
  const bufferedOwnMessages = new Map<string, ChatMessage>();

  const topic = `/topic/activity/${activityId}`;
  const restBase = `/api/activities/${activityId}/messages`;

  const appendUnique = (message: ChatMessage) => {
    if (!messages.value.some((existing) => existing.id === message.id)) {
      messages.value.push(message);
    }
  };

  const flushBufferedOwnMessages = () => {
    if (pendingTempIds.size > 0) return;
    for (const message of bufferedOwnMessages.values()) appendUnique(message);
    bufferedOwnMessages.clear();
  };

  const receiveMessage = (message: ChatMessage) => {
    // The backend can broadcast before the HTTP response reaches this tab. Hold
    // the sender's echo until its optimistic row has been reconciled by ID.
    if (message.userId === auth.user?.id && pendingTempIds.size > 0) {
      bufferedOwnMessages.set(message.id, message);
      return;
    }
    appendUnique(message);
  };

  const fetchHistory = async () => {
    try {
      const response = await fetch(`${restBase}?limit=50`, { credentials: 'same-origin' });
      if (response.status === 401) {
        void stompClient?.deactivate();
        if (!window.location.pathname.startsWith('/auth/login')) {
          window.location.href = '/auth/login';
        }
        return;
      }
      if (response.ok) {
        messages.value = (await response.json()) as ChatMessage[];
      }
    } catch {
      sendError.value = 'Unable to load message history.';
    }
  };

  const connect = () => {
    if (!activityId) return;
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const brokerURL = `${protocol}//${window.location.host}/ws`;

    stompClient = new Client({
      brokerURL,
      reconnectDelay: 3000,
      onConnect: () => {
        connected.value = true;
        void fetchHistory();
        stompClient!.subscribe(topic, (frame: IMessage) => {
          receiveMessage(JSON.parse(frame.body) as ChatMessage);
        });
      },
      onDisconnect: () => {
        connected.value = false;
      },
      onWebSocketClose: () => {
        connected.value = false;
      },
      onStompError: (frame) => {
        sendError.value = frame.headers.message ?? 'Chat connection was rejected.';
        if (sendError.value.includes('authenticated') || sendError.value.includes('expired')) {
          void stompClient?.deactivate();
        }
      },
    });

    stompClient.activate();
  };

  const send = async (content: string): Promise<boolean> => {
    const trimmed = content.trim();
    if (!trimmed) return false;

    sendError.value = null;
    const tempId = `temp-${crypto.randomUUID()}`;
    const user = auth.user;
    pendingTempIds.add(tempId);
    messages.value.push({
      id: tempId,
      activityId,
      userId: user?.id ?? null,
      displayName: user?.displayName ?? 'You',
      avatarUrl: user?.avatarUrl ?? null,
      content: trimmed,
      type: 'USER',
      createdAt: new Date().toISOString(),
    });

    try {
      const xsrfToken = document.cookie
        .split('; ')
        .find((row) => row.startsWith('XSRF-TOKEN='))
        ?.split('=')[1];

      const response = await fetch(restBase, {
        method: 'POST',
        credentials: 'same-origin',
        headers: {
          'Content-Type': 'application/json',
          ...(xsrfToken ? { 'X-XSRF-TOKEN': xsrfToken } : {}),
        },
        body: JSON.stringify({ content: trimmed }),
      });

      if (!response.ok) {
        messages.value = messages.value.filter((message) => message.id !== tempId);
        sendError.value =
          response.status === 429
            ? 'You are sending messages too quickly. Try again shortly.'
            : 'Message could not be sent.';
        return false;
      }

      const saved = (await response.json()) as ChatMessage;
      messages.value = reconcileSavedMessage(messages.value, tempId, saved);
      bufferedOwnMessages.delete(saved.id);
      return true;
    } catch {
      messages.value = messages.value.filter((message) => message.id !== tempId);
      sendError.value = 'Message could not be sent. Check your connection.';
      return false;
    } finally {
      pendingTempIds.delete(tempId);
      flushBufferedOwnMessages();
    }
  };

  onMounted(connect);
  onUnmounted(() => {
    if (stompClient) {
      void stompClient.deactivate();
      stompClient = null;
    }
  });

  return { messages, connected, sendError, send };
}
