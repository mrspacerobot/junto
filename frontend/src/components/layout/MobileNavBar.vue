<template>
  <nav class="fixed bottom-0 inset-x-0 z-40 bg-white border-t border-warm-300 md:hidden safe-area-bottom">
    <div class="flex items-center justify-around h-16">
      <router-link
        to="/activities"
        class="flex flex-col items-center justify-center gap-1 px-4 py-2"
        :class="[$route.path.startsWith('/activities') ? 'text-primary-600' : 'text-slate-400']"
      >
        <Rss class="w-5 h-5" />
        <span class="text-[10px] font-medium">Feed</span>
      </router-link>
      <router-link
        to="/explore"
        class="flex flex-col items-center justify-center gap-1 px-4 py-2"
        :class="[$route.path === '/explore' ? 'text-primary-600' : 'text-slate-400']"
      >
        <Compass class="w-5 h-5" />
        <span class="text-[10px] font-medium">Explore</span>
      </router-link>

      <!-- FAB -->
      <router-link
        to="/activities/new"
        class="flex items-center justify-center w-12 h-12 bg-primary-600 text-white rounded-full shadow-lg shadow-primary-200 -mt-4"
      >
        <Plus class="w-5 h-5" />
      </router-link>

      <router-link
        to="/messages"
        class="flex flex-col items-center justify-center gap-1 px-4 py-2"
        :class="[$route.path.startsWith('/messages') ? 'text-primary-600' : 'text-slate-400']"
      >
        <MessageCircle class="w-5 h-5" />
        <span class="text-[10px] font-medium">Messages</span>
      </router-link>
      <router-link
        to="/profile"
        class="flex flex-col items-center justify-center gap-1 px-4 py-2"
        :class="[$route.path === '/profile' ? 'text-primary-600' : 'text-slate-400']"
      >
        <User class="w-5 h-5" />
        <span class="text-[10px] font-medium">Profile</span>
      </router-link>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import { Rss, Compass, MessageCircle, Plus, User } from 'lucide-vue-next'

const $route = useRoute()
</script>
