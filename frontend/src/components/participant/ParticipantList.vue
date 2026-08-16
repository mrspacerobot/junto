<template>
  <div class="space-y-4">
    <div class="flex items-center justify-between">
      <h4 class="text-sm font-bold text-slate-900 uppercase tracking-wider">Participants</h4>
      <span class="text-xs font-medium text-slate-500"
        >{{ approvedParticipants.length }} / {{ maxParticipants }}</span
      >
    </div>

    <div class="space-y-3">
      <div
        v-for="p in approvedParticipants"
        :key="p.id"
        class="flex items-center justify-between group"
      >
        <div class="flex items-center gap-3">
          <div
            class="w-8 h-8 bg-slate-100 rounded-full flex-shrink-0 border border-slate-200 overflow-hidden"
          >
            <img v-if="p.avatarUrl" :src="p.avatarUrl" class="w-full h-full object-cover" />
          </div>
          <span class="text-sm font-medium text-slate-700">{{ p.displayName }}</span>
          <span
            v-if="p.role === 'CREATOR'"
            class="text-[10px] font-bold bg-slate-900 text-white px-1.5 py-0.5 rounded"
            >HOST</span
          >
        </div>
      </div>
    </div>

    <template v-if="isCreator && pendingParticipants.length > 0">
      <div class="pt-6 mt-6 border-t border-slate-100">
        <h4 class="text-sm font-bold text-amber-700 uppercase tracking-wider mb-4">
          Pending Requests
        </h4>
        <div class="space-y-4">
          <div
            v-for="p in pendingParticipants"
            :key="p.id"
            class="bg-amber-50 rounded-xl p-3 flex items-center justify-between"
          >
            <div class="flex items-center gap-3">
              <div
                class="w-8 h-8 bg-white rounded-full flex-shrink-0 border border-amber-200"
              ></div>
              <span class="text-sm font-bold text-amber-900">{{ p.displayName }}</span>
            </div>
            <div class="flex gap-2">
              <button
                @click="$emit('decline', p.id)"
                class="p-1.5 text-rose-600 hover:bg-rose-100 rounded-lg transition-colors"
              >
                <X class="w-4 h-4" />
              </button>
              <button
                @click="$emit('approve', p.id)"
                class="p-1.5 text-emerald-600 hover:bg-emerald-100 rounded-lg transition-colors"
              >
                <Check class="w-4 h-4" />
              </button>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { Check, X } from 'lucide-vue-next';
import type { Participant } from '@/api/transforms';

const props = defineProps<{
  participants: Participant[];
  maxParticipants: number;
  isCreator: boolean;
}>();

defineEmits<{
  approve: [participantId: string];
  decline: [participantId: string];
}>();

const approvedParticipants = computed(() =>
  props.participants.filter((p) => p.status === 'APPROVED'),
);

const pendingParticipants = computed(() =>
  props.participants.filter((p) => p.status === 'PENDING'),
);
</script>
