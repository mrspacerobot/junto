<template>
  <header class="h-[52px] border-b border-warm-300 flex items-center gap-0 px-7 flex-shrink-0 bg-white">
    <!-- Tabs -->
    <div class="flex gap-1 flex-1">
      <router-link
        v-for="tab in tabs"
        :key="tab.id"
        :to="tab.to"
        class="px-4 h-[52px] flex items-center text-sm font-medium transition-colors border-b-2"
        :class="isActive(tab.id)
          ? 'border-primary-700 text-primary-700 font-semibold'
          : 'border-transparent text-[#6A6560] hover:text-slate-900'"
      >{{ tab.label }}</router-link>
    </div>

    <!-- Search -->
    <div class="flex items-center gap-2 bg-warm-100 border border-warm-300 rounded-full px-3.5 py-1.5 w-[220px]">
      <Search class="w-3.5 h-3.5 text-[#9D998F] flex-shrink-0" />
      <input
        v-model="search"
        placeholder="Search people or plans"
        class="border-none bg-transparent outline-none text-[13px] text-slate-900 w-full font-sans placeholder:text-[#9D998F]"
      />
    </div>

    <!-- Right: bell + avatar -->
    <div class="flex items-center gap-2.5 ml-4">
      <button class="p-1 text-[#6A6560] hover:text-slate-900 transition-colors">
        <Bell class="w-[18px] h-[18px]" />
      </button>
      <router-link to="/profile" class="flex items-center gap-1.5">
        <div class="w-8 h-8 bg-primary-200 rounded-full flex items-center justify-center">
          <span class="text-primary-700 text-[11px] font-bold">{{ initials }}</span>
        </div>
        <ChevronDown class="w-3 h-3 text-[#6A6560]" />
      </router-link>
    </div>
  </header>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { Search, Bell, ChevronDown } from 'lucide-vue-next'

const $route = useRoute()
const { user } = storeToRefs(useAuthStore())
const search = ref('')

const tabs = [
  { id: 'feed', label: 'Feed', to: '/activities' },
  { id: 'explore', label: 'Explore', to: '/explore' },
  { id: 'messages', label: 'Messages', to: '/messages' },
]

const isActive = (id: string) => {
  const path = $route.path
  if (id === 'feed') return path.startsWith('/activities')
  if (id === 'explore') return path === '/explore'
  if (id === 'messages') return path.startsWith('/messages')
  return false
}

const initials = computed(() => {
  const name = user.value?.displayName
  if (!name) return '?'
  return name.split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2)
})
</script>
