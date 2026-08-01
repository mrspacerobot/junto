<template>
  <div class="h-full flex flex-col">
    <!-- Header -->
    <div v-if="thread" class="px-5 py-3.5 border-b border-warm-300 flex items-center gap-3 flex-shrink-0 bg-white">
      <!-- Back button (mobile only) -->
      <button
        v-if="isMobile"
        @click="$emit('back')"
        class="text-slate-900 p-0 bg-transparent border-none cursor-pointer"
      >
        <ChevronLeft class="w-5 h-5" />
      </button>
      <div class="w-9 h-9 rounded-full flex-shrink-0 overflow-hidden"
           :style="{ background: typeGradient(thread.activityTypeSlug) }">
        <img v-if="thread.imageUrl" :src="thread.imageUrl" class="w-full h-full object-cover" />
        <div v-else class="w-full h-full flex items-center justify-center text-white text-xs font-bold uppercase">
          {{ thread.activityTypeSlug?.slice(0, 2) }}
        </div>
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
    <div v-else ref="scrollArea" class="flex-1 overflow-y-auto px-5 py-4 flex flex-col gap-2.5 bg-warm-50">
      <div v-if="messages.length === 0" class="flex-1 flex items-center justify-center text-[#9D998F] text-sm">
        No messages yet. Say hello!
      </div>
      <template v-for="msg in messages" :key="msg.id">
        <!-- System message -->
        <div v-if="msg.type === 'SYSTEM'" data-testid="chat-message" class="text-center text-xs text-[#9D998F] italic py-1">
          {{ msg.content }}
        </div>
        <!-- Own message -->
        <div v-else-if="msg.userId === currentUserId" data-testid="chat-message" class="flex justify-end gap-2 items-end">
          <div>
            <div class="bg-primary-700 text-white px-3.5 py-2.5 text-sm max-w-[260px] shadow-sm"
                 style="border-radius: 18px 18px 4px 18px;">
              {{ msg.content }}
            </div>
            <div class="text-[10px] text-[#9D998F] mt-1 text-right mr-1">{{ formatTime(msg.createdAt) }}</div>
          </div>
        </div>
        <!-- Other user message -->
        <div v-else data-testid="chat-message" class="flex gap-2 items-end">
          <div class="w-7 h-7 rounded-full flex-shrink-0 overflow-hidden flex items-center justify-center text-white text-[10px] font-semibold"
               :style="{ background: avatarColor(msg.displayName) }">
            <img v-if="msg.avatarUrl" :src="msg.avatarUrl" class="w-full h-full object-cover" />
            <span v-else>{{ msg.displayName?.charAt(0)?.toUpperCase() }}</span>
          </div>
          <div>
            <div class="text-[10px] font-medium text-[#9D998F] ml-1 mb-0.5">{{ msg.displayName }}</div>
            <div class="bg-white border border-warm-300 text-slate-700 px-3.5 py-2.5 text-sm max-w-[260px] shadow-sm"
                 style="border-radius: 18px 18px 18px 4px;">
              {{ msg.content }}
            </div>
            <div class="text-[10px] text-[#9D998F] mt-1 ml-1">{{ formatTime(msg.createdAt) }}</div>
          </div>
        </div>
      </template>
      <div />
    </div>

    <div v-if="thread && sendError" role="alert" class="px-4 py-2 bg-rose-50 text-xs text-rose-700">
      {{ sendError }}
    </div>

    <!-- Input -->
    <div v-if="thread" class="px-4 py-3 border-t border-warm-300 flex gap-2.5 items-center flex-shrink-0 bg-white">
      <input
        data-testid="chat-message-input"
        v-model="input"
        @keydown.enter="handleSend"
        type="text"
        placeholder="Write a message…"
        class="flex-1 bg-warm-50 border border-warm-300 rounded-full px-4 py-2.5 text-sm outline-none focus:border-primary-500 text-slate-900 placeholder-[#9D998F] font-sans"
      />
      <button
        @click="handleSend"
        :disabled="!input.trim()"
        class="w-9 h-9 rounded-full bg-primary-700 text-white flex items-center justify-center disabled:opacity-40 flex-shrink-0 border-none cursor-pointer"
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

// ChatPanel is keyed by activityId in MessagesView, so this only runs once per thread
const { messages, send, sendError } = useChat(props.thread?.activityId ?? '')

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

// Generate a consistent color from a display name for avatars
const nameColors = [
  '#8B6F47', '#5C7A4E', '#4A6B8A', '#8A5C6B', '#6B7A4A',
  '#7A5C8A', '#5C7A6B', '#8A6B5C', '#5C6B8A', '#7A6B5C',
]
function avatarColor(name: string) {
  if (!name) return '#94A3B8'
  let hash = 0
  for (let i = 0; i < name.length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash)
  }
  return nameColors[Math.abs(hash) % nameColors.length]
}

function formatTime(ts: string) {
  return new Date(ts).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
}
</script>
