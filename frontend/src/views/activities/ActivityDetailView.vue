<template>
  <PageContainer v-if="activity">
    <div class="max-w-6xl mx-auto">
      <div class="flex flex-col lg:flex-row gap-8">
        <!-- Main Content -->
        <div class="flex-grow space-y-8">
          <div class="bg-white rounded-2xl border border-slate-200 overflow-hidden shadow-sm">
            <div :class="['h-32 w-full', typeColor]"></div>
            <div class="p-8 -mt-12">
              <div class="flex justify-between items-start">
                <div
                  class="w-20 h-20 rounded-2xl bg-white p-4 shadow-lg flex items-center justify-center"
                >
                  <ActivityIcon class="w-10 h-10" :class="typeText" />
                </div>
                <div class="pt-12">
                  <span
                    class="px-3 py-1 bg-emerald-100 text-emerald-700 text-xs font-bold rounded-full uppercase tracking-wider"
                  >
                    {{ activity.status }}
                  </span>
                </div>
              </div>

              <div class="mt-6">
                <h1 class="text-4xl font-extrabold text-slate-900 leading-tight">
                  {{ activity.title }}
                </h1>
                <div class="flex items-center gap-4 mt-4 text-slate-500">
                  <div class="flex items-center gap-1.5">
                    <MapPin class="w-4 h-4" />
                    <span class="text-sm font-medium">{{ activity.city }}</span>
                  </div>
                  <div class="flex items-center gap-1.5">
                    <Calendar class="w-4 h-4" />
                    <span class="text-sm font-medium"
                      >{{ formatDate(activity.startsAt) }} @
                      {{ formatTime(activity.startsAt) }}</span
                    >
                  </div>
                </div>
              </div>

              <div class="mt-8 prose prose-slate">
                <p class="text-slate-600 leading-relaxed text-lg whitespace-pre-wrap">
                  {{ activity.description || 'No description provided.' }}
                </p>
              </div>
            </div>
          </div>

          <!-- Chat Panel -->
          <div v-if="isApproved">
            <ChatPanel :messages="chatMessages" @send="chatSend" />
          </div>
          <div
            v-else
            class="bg-slate-100 rounded-2xl p-12 text-center border-2 border-dashed border-slate-200"
          >
            <Lock class="w-8 h-8 text-slate-300 mx-auto mb-3" />
            <h3 class="text-slate-900 font-bold">Chat is locked</h3>
            <p class="text-slate-500 text-sm mt-1">
              Join this activity to participate in the conversation.
            </p>
          </div>
        </div>

        <!-- Sidebar -->
        <div class="w-full lg:w-80 space-y-6">
          <div class="bg-white rounded-2xl border border-slate-200 p-6 shadow-sm sticky top-24">
            <JoinButton
              :status="userStatus"
              :join-policy="activity.joinPolicy"
              :loading="joining"
              @click="handleJoin"
            />

            <p class="text-xs text-center text-slate-400 mb-8">
              Signup deadline: {{ formatDate(activity.effectiveDeadline) }}
            </p>

            <ParticipantList
              :participants="participants"
              :max-participants="activity.maxParticipants"
              :is-creator="isCreator"
              @approve="handleApprove"
              @decline="handleDecline"
            />
          </div>

          <div class="bg-slate-900 rounded-2xl p-6 text-white shadow-xl shadow-slate-200">
            <h4
              class="font-bold mb-4 flex items-center gap-2 text-xs uppercase tracking-widest text-slate-400"
            >
              <ShieldCheck class="w-4 h-4 text-primary-400" />
              Activity Host
            </h4>
            <div class="flex items-center gap-3">
              <div class="w-10 h-10 bg-slate-700 rounded-full border border-slate-600"></div>
              <div>
                <div class="font-bold text-sm">{{ activity.creatorDisplayName }}</div>
                <div class="text-[10px] text-primary-400 font-bold uppercase tracking-tight">
                  View Profile
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { Activity as ActivityIcon, MapPin, Calendar, ShieldCheck, Lock } from 'lucide-vue-next';
import PageContainer from '@/components/layout/PageContainer.vue';
import JoinButton from '@/components/participant/JoinButton.vue';
import ParticipantList from '@/components/participant/ParticipantList.vue';
import ChatPanel from '@/components/chat/ChatPanel.vue';
import { useAuthStore } from '@/stores/auth';
import { useChat } from '@/composables/useChat';
import {
  activityGetDetail,
  participantApprove,
  participantDecline,
  participantJoin,
  participantList,
} from '@/api/generated/sdk.gen';
import {
  toActivityDetail,
  toParticipant,
  type ActivityDetail,
  type Participant,
} from '@/api/transforms';

const props = defineProps<{
  id: string;
}>();

const auth = useAuthStore();
const activity = ref<ActivityDetail | null>(null);
const participants = ref<Participant[]>([]);
const joining = ref(false);

const currentUserId = computed(() => auth.user?.id ?? '');

const isCreator = computed(() => activity.value?.creatorId === currentUserId.value);
const userParticipant = computed(() =>
  participants.value.find((p) => p.userId === currentUserId.value),
);
const isApproved = computed(() => userParticipant.value?.status === 'APPROVED');

const userStatus = computed(() => {
  if (!activity.value) return 'NONE';
  if (userParticipant.value) return userParticipant.value.status ?? 'NONE';
  const approvedCount = participants.value.filter((p) => p.status === 'APPROVED').length;
  if (approvedCount >= (activity.value.maxParticipants ?? 0)) return 'FULL';
  if (new Date() > new Date(activity.value.effectiveDeadline ?? '')) return 'CLOSED';
  return 'NONE';
});

// Chat — only connects when component mounts; STOMP subscription is rejected
// server-side if user is not a participant, which is fine.
const { messages: rawMessages, send: chatSend } = useChat(props.id);

// Map chat messages to ChatPanel/ChatMessage props
const chatMessages = computed(() =>
  rawMessages.value.map((m) => ({
    ...m,
    avatarUrl: m.avatarUrl ?? undefined,
    timestamp: m.createdAt,
    isMe: m.userId === currentUserId.value,
  })),
);

const typeColor = computed(() => {
  const colors: Record<string, string> = {
    climbing: 'bg-emerald-600',
    cycling: 'bg-blue-600',
    running: 'bg-orange-600',
    skiing: 'bg-sky-500',
    yoga: 'bg-indigo-500',
  };
  return colors[activity.value?.activityTypeSlug ?? ''] || 'bg-slate-600';
});

const typeText = computed(() => {
  const colors: Record<string, string> = {
    climbing: 'text-emerald-600',
    cycling: 'text-blue-600',
    running: 'text-orange-600',
    skiing: 'text-sky-500',
    yoga: 'text-indigo-500',
  };
  return colors[activity.value?.activityTypeSlug ?? ''] || 'text-slate-600';
});

const formatDate = (d?: string) =>
  d
    ? new Date(d).toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' })
    : '';
const formatTime = (d?: string) =>
  d ? new Date(d).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' }) : '';

const loadActivity = async () => {
  try {
    const { data } = await activityGetDetail({ path: { id: props.id } });
    activity.value = data ? toActivityDetail(data) : null;
  } catch {
    // 401 redirects handled by client interceptor
  }
};

const loadParticipants = async () => {
  try {
    const { data } = await participantList({ path: { activityId: props.id } });
    participants.value = (data ?? []).map(toParticipant);
  } catch {
    // Silently fail
  }
};

const handleJoin = async () => {
  if (userStatus.value !== 'NONE') return;
  joining.value = true;
  try {
    const { data } = await participantJoin({ path: { activityId: props.id } });
    if (data) participants.value.push(toParticipant(data));
    // Refresh participants to get consistent state
    await loadParticipants();
  } catch {
    // Silently fail
  } finally {
    joining.value = false;
  }
};

const handleApprove = async (participantId: string) => {
  try {
    await participantApprove({ path: { activityId: props.id, participantId } });
    const p = participants.value.find((p) => p.id === participantId);
    if (p) p.status = 'APPROVED';
  } catch {
    // Silently fail
  }
};

const handleDecline = async (participantId: string) => {
  try {
    await participantDecline({ path: { activityId: props.id, participantId } });
    const p = participants.value.find((p) => p.id === participantId);
    if (p) p.status = 'DECLINED';
  } catch {
    // Silently fail
  }
};

onMounted(() => {
  loadActivity();
  loadParticipants();
});
</script>
