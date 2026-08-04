<template>
  <button
    @click="$emit('click')"
    :disabled="disabled || loading"
    :class="[
      'w-full py-4 font-bold rounded-xl transition-all shadow-lg mb-4 flex items-center justify-center gap-2',
      buttonClass,
    ]"
  >
    <component :is="icon" v-if="icon" class="w-5 h-5" />
    <span v-if="loading">Processing...</span>
    <span v-else>{{ label }}</span>
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { UserPlus, Clock, CheckCircle2, XCircle, Lock } from 'lucide-vue-next';

const props = defineProps<{
  status: 'NONE' | 'PENDING' | 'APPROVED' | 'DECLINED' | 'FULL' | 'CLOSED';
  joinPolicy: 'OPEN' | 'APPROVAL_REQUIRED';
  loading?: boolean;
  disabled?: boolean;
}>();

defineEmits(['click']);

const label = computed(() => {
  switch (props.status) {
    case 'APPROVED':
      return 'Joined';
    case 'PENDING':
      return 'Request Pending';
    case 'DECLINED':
      return 'Request Declined';
    case 'FULL':
      return 'Activity Full';
    case 'CLOSED':
      return 'Signup Closed';
    case 'NONE':
      return props.joinPolicy === 'OPEN' ? 'Join Activity' : 'Request to Join';
    default:
      return 'Join Activity';
  }
});

const icon = computed(() => {
  switch (props.status) {
    case 'APPROVED':
      return CheckCircle2;
    case 'PENDING':
      return Clock;
    case 'DECLINED':
      return XCircle;
    case 'FULL':
      return Lock;
    case 'CLOSED':
      return Lock;
    case 'NONE':
      return UserPlus;
    default:
      return null;
  }
});

const buttonClass = computed(() => {
  switch (props.status) {
    case 'APPROVED':
      return 'bg-emerald-100 text-emerald-700 shadow-emerald-50 cursor-default';
    case 'PENDING':
      return 'bg-amber-100 text-amber-700 shadow-amber-50 cursor-default';
    case 'DECLINED':
      return 'bg-rose-100 text-rose-700 shadow-rose-50 cursor-default';
    case 'FULL':
    case 'CLOSED':
      return 'bg-slate-100 text-slate-400 shadow-none cursor-not-allowed';
    case 'NONE':
      return 'bg-primary-600 text-white hover:bg-primary-700 shadow-primary-100';
    default:
      return 'bg-primary-600 text-white';
  }
});
</script>
