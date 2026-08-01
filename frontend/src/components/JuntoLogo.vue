<template>
  <component
    :is="tag"
    :class="[
      'inline-flex items-center',
      iconOnly ? '' : 'gap-[10px]',
      tag === 'a' ? 'no-underline' : ''
    ]"
    v-bind="tag === 'a' ? { href: '/' } : {}"
  >
    <!-- People together mark -->
    <svg
      :width="iconSize"
      :height="iconSize * 0.75"
      viewBox="0 0 32 24"
      fill="none"
      class="shrink-0"
    >
      <circle cx="10" cy="7" r="4.5" :fill="color" opacity="0.9" />
      <path d="M1 22 C1 15 5 13 10 13 C15 13 19 15 19 22" :fill="color" opacity="0.9" />
      <circle cx="22" cy="7" r="4.5" :fill="color" opacity="0.55" />
      <path d="M13 22 C13 15 17 13 22 13 C27 13 31 15 31 22" :fill="color" opacity="0.55" />
    </svg>

    <!-- Wordmark -->
    <span
      v-if="!iconOnly"
      :style="wordmarkStyle"
    >junto</span>
  </component>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  size?: number
  dark?: boolean
  iconOnly?: boolean
  tag?: string
}>(), {
  size: 1,
  dark: false,
  iconOnly: false,
  tag: 'span',
})

const color = computed(() => props.dark ? '#ffffff' : 'currentColor')

const iconSize = computed(() => Math.round(24 * props.size))
const fontSize = computed(() => Math.round(30 * props.size))

const wordmarkStyle = computed(() => ({
  fontFamily: "'Playfair Display', serif",
  fontSize: `${fontSize.value}px`,
  fontStyle: 'italic',
  fontWeight: 400,
  color: color.value,
  letterSpacing: '-0.03em',
  lineHeight: 1,
}))
</script>
