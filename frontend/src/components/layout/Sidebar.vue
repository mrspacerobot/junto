<template>
  <aside class="w-[220px] bg-white border-r border-warm-300 flex flex-col h-screen fixed left-0 top-0 z-40">
    <!-- Logo -->
    <div class="px-5 pt-[22px] pb-[18px]">
      <router-link to="/activities" class="flex items-center">
        <span class="font-display text-[26px] italic font-normal text-primary-700 tracking-tight leading-none">junto</span>
      </router-link>
    </div>

    <!-- Create plan CTA -->
    <div class="px-3.5 pb-5">
      <router-link
        to="/activities/new"
        class="flex items-center justify-center gap-1.5 w-full py-2.5 bg-primary-700 text-white text-[13px] font-semibold rounded-[10px] hover:bg-primary-800 transition-colors"
      >
        <Plus class="w-3.5 h-3.5" />
        Create a plan
      </router-link>
    </div>

    <!-- Navigation -->
    <nav class="flex-1 px-2 space-y-0.5">
      <router-link
        v-for="item in navItems"
        :key="item.id"
        :to="item.to"
        class="flex items-center gap-2.5 w-full px-3 py-[9px] rounded-lg text-[13px] transition-colors relative"
        :class="[isActive(item.id)
          ? 'bg-accent-bg text-primary-700 font-semibold'
          : 'text-[#6A6560] font-normal hover:bg-warm-100']"
      >
        <component :is="item.icon" :size="18" :class="isActive(item.id) ? 'text-primary-700' : 'text-[#6A6560]'" />
        {{ item.label }}
      </router-link>
    </nav>

    <!-- Invite promo -->
    <div class="mx-3.5 mb-5 p-3.5 bg-accent-bg rounded-xl border border-[#D4E4C8]">
      <p class="text-sm font-semibold text-primary-700 leading-snug mb-1">Plans are better shared.</p>
      <button class="text-xs text-accent font-medium hover:underline">Invite friends →</button>
    </div>

    <!-- Profile -->
    <div class="px-4 pb-6">
      <router-link
        to="/profile"
        class="flex items-center gap-3 px-3 py-2.5 rounded-lg transition-colors"
        :class="[$route.path === '/profile' ? 'bg-warm-50' : 'hover:bg-warm-50']"
      >
        <div class="w-8 h-8 bg-primary-200 rounded-full flex items-center justify-center">
          <span class="text-primary-700 text-xs font-bold">{{ initials }}</span>
        </div>
        <div class="flex-1 min-w-0">
          <p class="text-sm font-medium text-slate-900 truncate">{{ user?.displayName }}</p>
          <p class="text-xs text-slate-500 truncate">{{ user?.email }}</p>
        </div>
      </router-link>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { Home, Compass, MessageCircle, Bookmark, Plus } from 'lucide-vue-next'

const $route = useRoute()
const { user } = storeToRefs(useAuthStore())

const navItems = [
  { id: 'feed', label: 'Home', to: '/activities', icon: Home },
  { id: 'explore', label: 'Explore', to: '/explore', icon: Compass },
  { id: 'messages', label: 'Messages', to: '/messages', icon: MessageCircle },
  { id: 'saved', label: 'Saved', to: '/saved', icon: Bookmark },
]

const isActive = (id: string) => {
  const path = $route.path
  if (id === 'feed') return path.startsWith('/activities')
  if (id === 'explore') return path === '/explore'
  if (id === 'messages') return path.startsWith('/messages')
  if (id === 'saved') return path === '/saved'
  return false
}

const initials = computed(() => {
  const name = user.value?.displayName
  if (!name) return '?'
  return name.split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2)
})
</script>
