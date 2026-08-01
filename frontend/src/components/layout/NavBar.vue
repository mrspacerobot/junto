<template>
  <nav class="bg-white border-b border-warm-300 sticky top-0 z-50">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
      <div class="flex justify-between h-16">
        <div class="flex">
          <router-link to="/activities" class="flex-shrink-0 flex items-center">
            <JuntoLogo :size="0.7" class="text-slate-900" />
          </router-link>

          <div class="hidden sm:ml-8 sm:flex sm:space-x-8">
            <router-link
              to="/activities"
              class="inline-flex items-center px-1 pt-1 border-b-2 text-sm font-medium"
              :class="[$route.path.startsWith('/activities') ? 'border-primary-500 text-slate-900' : 'border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700']"
            >
              Feed
            </router-link>
          </div>
        </div>

        <div class="flex items-center gap-4">
          <router-link
            to="/activities/new"
            class="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white bg-primary-600 hover:bg-primary-700"
          >
            Create Activity
          </router-link>

          <div class="ml-3 relative">
            <!-- Profile placeholder -->
            <div class="w-8 h-8 bg-slate-200 rounded-full"></div>
          </div>
        </div>
      </div>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import JuntoLogo from '@/components/JuntoLogo.vue'
const $route = useRoute()
</script>
