<template>
  <div class="flex items-center gap-2 p-4 bg-white border-t border-slate-100 sticky bottom-0">
    <input
      data-testid="chat-message-input"
      v-model="text"
      @keyup.enter="handleSend"
      type="text"
      placeholder="Type a message..."
      class="flex-grow bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-sm focus:ring-2 focus:ring-primary-500 outline-none transition-all"
    />
    <button
      @click="handleSend"
      :disabled="!text.trim()"
      class="p-3 bg-primary-600 text-white rounded-xl hover:bg-primary-700 disabled:opacity-50 transition-all shadow-md shadow-primary-100"
    >
      <Send class="w-5 h-5" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Send } from 'lucide-vue-next'

const emit = defineEmits(['send'])
const text = ref('')

const handleSend = () => {
  if (!text.value.trim()) return
  emit('send', text.value)
  text.value = ''
}
</script>
