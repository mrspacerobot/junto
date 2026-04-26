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
