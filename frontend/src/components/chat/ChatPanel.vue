<template>
  <div
    class="flex flex-col h-[500px] bg-slate-50 border border-slate-200 rounded-2xl overflow-hidden shadow-sm"
  >
    <div class="p-4 bg-white border-b border-slate-100 flex items-center justify-between">
      <h3 class="font-bold text-slate-900 flex items-center gap-2">
        <MessageSquare class="w-4 h-4 text-primary-500" />
        Activity Chat
      </h3>
      <div class="flex items-center gap-1.5">
        <div class="w-2 h-2 bg-emerald-500 rounded-full animate-pulse"></div>
        <span class="text-[10px] font-bold text-slate-400 uppercase tracking-widest">Live</span>
      </div>
    </div>

    <div ref="scrollArea" class="flex-grow overflow-y-auto p-4 scroll-smooth">
      <div
        v-if="messages.length === 0"
        class="h-full flex flex-col items-center justify-center opacity-30 select-none"
      >
        <MessageSquare class="w-12 h-12 mb-2" />
        <p class="text-xs font-bold uppercase tracking-widest">No messages yet</p>
      </div>
      <ChatMessage v-for="msg in messages" :key="msg.id" v-bind="msg" />
    </div>

    <ChatInput @send="$emit('send', $event)" />
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { MessageSquare } from 'lucide-vue-next';
import ChatMessage from './ChatMessage.vue';
import ChatInput from './ChatInput.vue';

interface RenderedChatMessage {
  id: string;
  content: string;
  displayName: string;
  avatarUrl?: string;
  isMe: boolean;
  timestamp: string;
  type: 'USER' | 'SYSTEM';
}

const props = defineProps<{
  messages: RenderedChatMessage[];
}>();

defineEmits<{ send: [content: string] }>();

const scrollArea = ref<HTMLElement | null>(null);

const scrollToBottom = () => {
  if (scrollArea.value) {
    scrollArea.value.scrollTop = scrollArea.value.scrollHeight;
  }
};

watch(
  () => props.messages.length,
  () => {
    setTimeout(scrollToBottom, 50);
  },
  { immediate: true },
);
</script>
