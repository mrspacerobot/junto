<template>
  <span class="text-[11px] font-semibold px-2 py-0.5 rounded-full whitespace-nowrap"
        :class="colorClasses[level] || colorClasses.Intermediate">
    {{ level }}
  </span>
</template>

<script setup lang="ts">
const props = defineProps<{ level: string }>()

const colorClasses: Record<string, string> = {
  Easy: 'bg-[#E8F5E2] text-[#3A7A20]',
  Beginner: 'bg-[#E8F5E2] text-[#3A7A20]',
  Intermediate: 'bg-[#FFF4E0] text-[#9B6010]',
  Advanced: 'bg-[#FFE8E0] text-[#9B3010]',
}
</script>
