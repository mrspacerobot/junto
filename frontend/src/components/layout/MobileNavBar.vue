<template>
  <nav class="fixed bottom-0 inset-x-0 z-40 md:hidden safe-area-bottom"
       style="background: rgba(247,244,239,0.82); backdrop-filter: blur(40px) saturate(180%); -webkit-backdrop-filter: blur(40px) saturate(180%); border-top: 0.5px solid rgba(0,0,0,0.08);">
    <div class="flex items-center h-[72px] px-1 pb-2">
      <router-link
        v-for="item in navItems"
        :key="item.id"
        :to="item.to"
        class="flex-1 flex flex-col items-center justify-center gap-[3px] py-1"
        :class="[isActive(item.id) ? 'text-primary-700' : 'text-[#9D998F]']"
      >
        <component :is="item.icon" :size="22" />
        <span class="text-[10px]" :class="isActive(item.id) ? 'font-semibold' : 'font-normal'">{{ item.label }}</span>
      </router-link>

      <!-- Center: FAB -->
      <div class="flex-1 flex justify-center">
        <router-link
          to="/activities/new"
          class="flex items-center justify-center w-[46px] h-[46px] bg-primary-700 text-white rounded-full"
          style="box-shadow: 0 4px 14px rgba(74,101,22,0.4);"
        >
          <Plus :size="18" />
        </router-link>
      </div>

      <router-link
        v-for="item in navItemsRight"
        :key="item.id"
        :to="item.to"
        class="flex-1 flex flex-col items-center justify-center gap-[3px] py-1"
        :class="[isActive(item.id) ? 'text-primary-700' : 'text-[#9D998F]']"
      >
        <component :is="item.icon" :size="22" />
        <span class="text-[10px]" :class="isActive(item.id) ? 'font-semibold' : 'font-normal'">{{ item.label }}</span>
      </router-link>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import { Home, Compass, MessageCircle, Bookmark, Plus } from 'lucide-vue-next'

const $route = useRoute()

const navItems = [
  { id: 'feed', label: 'Home', to: '/activities', icon: Home },
  { id: 'explore', label: 'Explore', to: '/explore', icon: Compass },
]

const navItemsRight = [
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
</script>
