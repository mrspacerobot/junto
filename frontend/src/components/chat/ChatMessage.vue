<template>
  <div
    data-testid="chat-message"
    :class="['flex w-full mb-4', isMe ? 'justify-end' : 'justify-start']"
  >
    <div v-if="type === 'SYSTEM'" class="w-full flex justify-center my-2">
      <span
        class="px-3 py-1 bg-slate-100 text-slate-500 text-[10px] font-bold rounded-full uppercase tracking-widest"
      >
        {{ content }}
      </span>
    </div>
    <div v-else class="flex gap-3 max-w-[80%]">
      <div
        v-if="!isMe"
        class="w-8 h-8 bg-slate-100 rounded-full flex-shrink-0 border border-slate-200 overflow-hidden"
      >
        <img v-if="avatarUrl" :src="avatarUrl" class="w-full h-full object-cover" />
      </div>
      <div class="flex flex-col">
        <span
          v-if="!isMe"
          class="text-[10px] font-bold text-slate-500 ml-1 mb-1 uppercase tracking-tight"
          >{{ displayName }}</span
        >
        <div
          :class="[
            'px-4 py-2.5 rounded-2xl text-sm shadow-sm',
            isMe
              ? 'bg-primary-600 text-white rounded-tr-none'
              : 'bg-white border border-slate-100 text-slate-700 rounded-tl-none',
          ]"
        >
          {{ content }}
        </div>
        <span :class="['text-[9px] mt-1 text-slate-400', isMe ? 'text-right mr-1' : 'ml-1']">
          {{ formatTime(timestamp) }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  content: string;
  displayName: string;
  avatarUrl?: string;
  isMe: boolean;
  timestamp: string;
  type: 'USER' | 'SYSTEM';
}>();

const formatTime = (ts: string) => {
  return new Date(ts).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
};
</script>
