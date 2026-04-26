<template>
  <aside class="w-[220px] bg-white border-r border-warm-300 flex flex-col h-screen fixed left-0 top-0 z-40">
    <!-- Logo -->
    <div class="px-6 py-8">
      <router-link to="/activities" class="flex items-center">
        <JuntoLogo :size="0.8" class="text-primary-700" />
      </router-link>
    </div>

    <!-- Navigation -->
    <nav class="flex-1 px-4 space-y-1">
      <router-link
        to="/activities"
        class="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors"
        :class="[$route.path.startsWith('/activities') ? 'bg-primary-50 text-primary-700' : 'text-[#6A6560] hover:bg-warm-50 hover:text-slate-900']"
      >
        <Rss class="w-5 h-5" />
        Feed
      </router-link>
      <router-link
        to="/explore"
        class="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors"
        :class="[$route.path === '/explore' ? 'bg-primary-50 text-primary-700' : 'text-[#6A6560] hover:bg-warm-50 hover:text-slate-900']"
      >
        <Compass class="w-5 h-5" />
        Explore
      </router-link>
      <router-link
        to="/messages"
        class="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors"
        :class="[$route.path.startsWith('/messages') ? 'bg-primary-50 text-primary-700' : 'text-[#6A6560] hover:bg-warm-50 hover:text-slate-900']"
      >
        <MessageCircle class="w-5 h-5" />
        Messages
      </router-link>
    </nav>

    <!-- CTA -->
    <div class="px-4 pb-4">
      <router-link
        to="/activities/new"
        class="flex items-center justify-center gap-2 w-full px-4 py-2.5 bg-primary-600 text-white text-sm font-bold rounded-lg hover:bg-primary-700 transition-colors"
      >
        <Plus class="w-4 h-4" />
        Plan a trip
      </router-link>
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
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { Rss, Compass, MessageCircle, Plus } from 'lucide-vue-next'
import JuntoLogo from '@/components/JuntoLogo.vue'

const $route = useRoute()
const { user } = useAuthStore()

const initials = computed(() => {
  const name = user.value?.displayName
  if (!name) return '?'
  return name.split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2)
})
</script>
