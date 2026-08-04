<template>
  <div class="flex h-full overflow-hidden bg-warm-100">
    <!-- Thread list (always visible on desktop, hidden when chat open on mobile) -->
    <div
      :class="[
        'md:w-[280px] md:flex-shrink-0 md:border-r md:border-warm-300',
        'flex flex-col overflow-hidden',
        isMobile && activeThread ? 'hidden' : 'w-full',
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
        'md:flex-1 flex flex-col overflow-hidden',
        isMobile && !activeThread ? 'hidden' : 'w-full',
      ]"
    >
      <ChatPanel
        :key="activeThread?.activityId ?? ''"
        :thread="activeThread"
        :is-mobile="isMobile"
        @back="handleBack"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import ThreadList from './ThreadList.vue';
import ChatPanel from './ChatPanel.vue';
import { useMessageThreads, type MessageThread } from '@/composables/useMessageThreads';

const route = useRoute();
const router = useRouter();
const { threads, loading } = useMessageThreads();
const activeThread = ref<MessageThread | null>(null);

const viewportWidth = ref(window.innerWidth);
const isMobile = computed(() => viewportWidth.value < 768);
const updateViewport = () => {
  viewportWidth.value = window.innerWidth;
};

function syncFromRoute() {
  const rawActivityId = route.params.activityId;
  const activityId = typeof rawActivityId === 'string' ? rawActivityId : undefined;
  activeThread.value = activityId
    ? (threads.value.find((thread) => thread.activityId === activityId) ?? null)
    : null;
}

watch([() => route.params.activityId, threads], syncFromRoute, { immediate: true });
onMounted(() => window.addEventListener('resize', updateViewport));
onUnmounted(() => window.removeEventListener('resize', updateViewport));

function handleSelect(thread: MessageThread) {
  activeThread.value = thread;
  router.push(`/messages/${thread.activityId}`);
}

function handleBack() {
  activeThread.value = null;
  router.push('/messages');
}
</script>
