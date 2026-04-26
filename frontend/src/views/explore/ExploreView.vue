<template>
  <div class="h-full overflow-hidden flex flex-col">
    <!-- Header (desktop only, mobile gets it from TopNav) -->
    <div class="hidden md:block px-7 pt-6">
      <h1 class="font-display text-[26px] font-normal text-slate-900 tracking-tight">Explore</h1>
      <p class="text-sm text-[#6A6560] mt-0.5">Discover plans near you</p>
    </div>

    <!-- Map -->
    <div class="px-4 md:px-7 pt-4 md:pt-4 flex-shrink-0">
      <div class="rounded-xl overflow-hidden border border-warm-300 h-[200px] md:h-[260px] relative">
        <MapPanel :activities="items" />
      </div>
    </div>

    <!-- Filter pills -->
    <div class="flex gap-2 px-4 md:px-7 py-3.5 overflow-x-auto scrollbar-hide flex-shrink-0">
      <button
        v-for="type in filterTypes"
        :key="type.slug"
        @click="setFilter(type.slug)"
        class="px-4 py-1.5 rounded-full text-[13px] font-medium whitespace-nowrap transition-colors border"
        :class="activeFilter === type.slug
          ? 'bg-primary-700 text-white border-primary-700'
          : 'bg-white text-[#6A6560] border-warm-300 hover:bg-warm-50'"
      >{{ type.name }}</button>
    </div>

    <!-- Plan grid -->
    <div class="flex-1 overflow-y-auto px-4 md:px-7 pb-24 md:pb-6">
      <div v-if="loading && items.length === 0" class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div v-for="i in 4" :key="i" class="h-52 bg-warm-50 animate-pulse rounded-2xl border border-warm-200"></div>
      </div>

      <div v-else-if="items.length === 0" class="text-center py-16">
        <div class="bg-warm-100 w-14 h-14 rounded-full flex items-center justify-center mx-auto mb-4">
          <Search class="text-warm-400 w-6 h-6" />
        </div>
        <h3 class="text-base font-bold text-slate-900">No plans found</h3>
        <p class="text-sm text-[#6A6560] mt-1">Try a different filter or create a new plan.</p>
      </div>

      <div v-else class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <ActivityCard
          v-for="activity in items"
          :key="activity.id"
          :activity="activity"
        />
      </div>

      <div v-if="hasMore" class="flex justify-center pt-6 pb-4">
        <button
          @click="load()"
          :disabled="loadingMore"
          class="px-6 py-2.5 bg-white border border-warm-300 text-[#6A6560] font-medium text-sm rounded-full hover:bg-warm-50 transition-colors disabled:opacity-50"
        >
          {{ loadingMore ? 'Loading...' : 'Load more' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Search } from 'lucide-vue-next'
import ActivityCard from '@/components/activity/ActivityCard.vue'
import { useActivityTypes } from '@/composables/useActivityTypes'
import { usePagination } from '@/composables/usePagination'
import { list1 } from '@/api/generated'

const { activityTypes } = useActivityTypes()
const activeFilter = ref<string | null>('all')

const filterTypes = computed(() => [
  { slug: 'all', name: 'All' },
  ...activityTypes.value
])


function setFilter(slug: string | null) {
  activeFilter.value = slug
  load(true)
}

const fetchActivities = async (cursor?: string) => {
  const typeFilter = activeFilter.value === 'all' ? undefined : activeFilter.value
  const response = await list1({
    query: {
      activityTypeId: typeFilter || undefined,
      cursor,
      limit: 12
    }
  })
  return {
    data: response.data?.data || [],
    nextCursor: response.data?.nextCursor
  }
}

const { items, loading, loadingMore, hasMore, load } = usePagination(fetchActivities)

onMounted(() => {
  load(true)
})
</script>
