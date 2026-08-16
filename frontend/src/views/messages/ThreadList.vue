<template>
  <div class="h-full flex flex-col">
    <!-- Header -->
    <div class="px-4 pb-3 flex-shrink-0">
      <h1 class="font-display text-2xl text-slate-900 mb-3 pt-4">Messages</h1>
      <div
        class="flex items-center gap-2 bg-warm-50 border border-warm-300 rounded-full px-3.5 py-1.5"
      >
        <Search class="w-3.5 h-3.5 text-[#9D998F]" />
        <input
          v-model="search"
          type="text"
          placeholder="Search messages"
          class="bg-transparent border-none outline-none text-sm text-slate-900 placeholder-[#9D998F] w-full font-sans"
        />
      </div>
    </div>

    <!-- Thread list -->
    <div class="flex-1 overflow-y-auto">
      <div v-if="loading" class="px-4 py-8 text-center text-sm text-[#9D998F]">Loading...</div>
      <div
        v-else-if="filteredThreads.length === 0"
        class="px-4 py-12 text-center text-sm text-[#9D998F]"
      >
        No conversations yet
      </div>
      <div v-else>
        <div
          v-for="thread in filteredThreads"
          :key="thread.activityId"
          data-testid="message-thread-row"
          @click="$emit('select', thread)"
          :class="[
            'flex gap-2.5 px-4 py-3 cursor-pointer transition-colors',
            activeId === thread.activityId
              ? 'bg-accent-bg border-l-[3px] border-primary-700'
              : 'border-l-[3px] border-transparent hover:bg-warm-50',
          ]"
        >
          <!-- Activity image/icon -->
          <div class="relative flex-shrink-0">
            <div
              class="w-10 h-10 rounded-full overflow-hidden"
              :style="{ background: typeGradient(thread.activityTypeSlug) }"
            >
              <img
                v-if="thread.imageUrl"
                :src="thread.imageUrl"
                class="w-full h-full object-cover"
              />
              <div
                v-else
                class="w-full h-full flex items-center justify-center text-white text-xs font-bold uppercase"
              >
                {{ thread.activityTypeSlug?.slice(0, 2) }}
              </div>
            </div>
            <!-- Participant mini-avatars -->
            <div
              v-if="thread.participantAvatars?.length"
              class="absolute -bottom-0.5 -right-0.5 flex"
            >
              <div
                v-for="(avatar, i) in thread.participantAvatars.slice(0, 2)"
                :key="i"
                class="w-4 h-4 rounded-full border-[1.5px] border-white overflow-hidden bg-warm-200"
                :style="{ marginLeft: i > 0 ? '-4px' : '0', zIndex: 2 - i }"
              >
                <img v-if="avatar" :src="avatar" class="w-full h-full object-cover" />
              </div>
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
              <span
                v-if="thread.participantCount > 1"
                class="text-[10px] text-[#9D998F] flex-shrink-0 ml-2"
              >
                {{ thread.participantCount }}
                <Users class="w-2.5 h-2.5 inline -mt-px" />
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { Search, Users } from 'lucide-vue-next';
import type { MessageThread } from '@/composables/useMessageThreads';

const props = defineProps<{
  threads: MessageThread[];
  activeId: string | null;
  loading: boolean;
}>();

defineEmits<{ select: [thread: MessageThread] }>();

const search = ref('');

const filteredThreads = computed(() => {
  if (!search.value.trim()) return props.threads;
  const q = search.value.toLowerCase();
  return props.threads.filter(
    (t) =>
      t.activityTitle.toLowerCase().includes(q) || t.lastMessageContent?.toLowerCase().includes(q),
  );
});

const typeGradients: Record<string, string> = {
  cycling: 'linear-gradient(135deg, #7BAE7F, #4A7A52)',
  climbing: 'linear-gradient(135deg, #A89880, #7A6855)',
  skiing: 'linear-gradient(135deg, #B8D4E8, #7AA8C8)',
  running: 'linear-gradient(135deg, #C4855A, #9B6040)',
  hiking: 'linear-gradient(135deg, #6B9B7A, #4A7B5A)',
  yoga: 'linear-gradient(135deg, #A78BFA, #7C3AED)',
};

function typeGradient(slug: string) {
  return typeGradients[slug] || 'linear-gradient(135deg, #94A3B8, #64748B)';
}

function formatRelativeTime(dateStr: string | null) {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMin = Math.floor(diffMs / 60000);
  if (diffMin < 1) return 'now';
  if (diffMin < 60) return `${diffMin}m`;
  const diffHr = Math.floor(diffMin / 60);
  if (diffHr < 24) return `${diffHr}h`;
  const diffDay = Math.floor(diffHr / 24);
  if (diffDay < 7) return `${diffDay}d`;
  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}
</script>
