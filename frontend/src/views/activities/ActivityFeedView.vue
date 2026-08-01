<template>
  <div class="flex h-full overflow-hidden px-4 md:px-7 pt-4 md:pt-6 gap-6">
    <!-- Feed column -->
    <div class="flex-1 overflow-y-auto pb-24 md:pb-6 min-w-0 pr-1">
      <!-- Greeting & filter row -->
      <div class="flex items-start justify-between mb-5 gap-4">
        <div>
          <h1 class="font-display text-[30px] font-normal text-slate-900 tracking-tight leading-tight">{{ greeting }}</h1>
          <p class="text-[#6A6560] text-sm mt-1">Here's what people are planning.</p>
        </div>
        <!-- Filter dropdown (desktop) -->
        <div class="relative hidden md:block flex-shrink-0 mt-1">
          <button
            @click="showFilter = !showFilter"
            class="flex items-center gap-1.5 bg-white border border-warm-300 rounded-full px-3.5 py-1.5 text-[13px] text-slate-900 font-medium hover:bg-warm-50 transition-colors"
          >
            {{ activeFilterLabel }} plans
            <ChevronDown class="w-3 h-3 text-[#6A6560]" />
          </button>
          <div v-if="showFilter" class="absolute top-[110%] right-0 bg-white border border-warm-300 rounded-xl shadow-lg p-1.5 z-10 min-w-[140px]">
            <button
              v-for="type in filterOptions"
              :key="type.slug"
              @click="setFilter(type.slug === 'all' ? null : type.slug); showFilter = false"
              class="block w-full text-left px-3 py-2 rounded-lg text-[13px] transition-colors"
              :class="currentFilter === type.slug
                ? 'bg-accent-bg text-primary-700 font-semibold'
                : 'text-slate-900 hover:bg-warm-50'"
            >{{ type.name }} plans</button>
          </div>
        </div>
      </div>

      <!-- Category pills (mobile) -->
      <div class="flex gap-1.5 mb-5 overflow-x-auto scrollbar-hide pb-1 md:hidden">
        <button
          v-for="type in filterOptions"
          :key="type.slug"
          @click="setFilter(type.slug === 'all' ? null : type.slug)"
          class="px-3.5 py-[5px] rounded-full text-xs font-medium whitespace-nowrap border transition-colors"
          :class="currentFilter === type.slug
            ? 'bg-primary-700 text-white border-primary-700'
            : 'bg-warm-200 text-[#6A6560] border-warm-300'"
        >{{ type.name }}</button>
      </div>

      <!-- Activity list -->
      <div v-if="loading && items.length === 0" class="space-y-4">
        <div v-for="i in 3" :key="i" class="h-[140px] bg-warm-50 animate-pulse rounded-[14px] border border-warm-200"></div>
      </div>

      <div v-else-if="items.length === 0" class="text-center py-20 bg-white rounded-2xl border border-warm-200">
        <div class="bg-warm-100 w-14 h-14 rounded-full flex items-center justify-center mx-auto mb-5">
          <Search class="text-warm-400 w-7 h-7" />
        </div>
        <h3 class="text-base font-bold text-slate-900">No activities found</h3>
        <p class="text-[#6A6560] text-sm max-w-xs mx-auto mt-1.5 mb-5">Try adjusting your filters or be the first to create one!</p>
        <router-link to="/activities/new" class="inline-flex items-center justify-center bg-primary-700 text-white font-semibold text-sm px-7 py-2.5 rounded-xl hover:bg-primary-800 transition-colors">
          Create a plan
        </router-link>
      </div>

      <div v-else class="space-y-4">
        <ActivityCard
          v-for="activity in items"
          :key="activity.id"
          :activity="activity"
        />

        <div v-if="hasMore" class="flex justify-center pt-6">
          <button
            @click="load()"
            :disabled="loadingMore"
            class="flex items-center gap-1.5 text-[#6A6560] text-[13px] hover:text-slate-900 transition-colors disabled:opacity-50"
          >
            {{ loadingMore ? 'Loading...' : 'Load more plans' }}
            <ChevronDown v-if="!loadingMore" class="w-3 h-3" />
          </button>
        </div>
      </div>
    </div>

    <!-- Right sidebar (desktop only) -->
    <div class="hidden lg:flex flex-col w-[280px] flex-shrink-0 overflow-y-auto pb-6 gap-5">
      <!-- Explore near you -->
      <div>
        <h3 class="text-sm font-semibold text-slate-900 mb-2.5">Explore near you</h3>
        <div class="rounded-xl overflow-hidden border border-warm-300 h-[200px] relative">
          <MapPanel :activities="items" class="absolute inset-0 w-full h-full" />
        </div>
        <button class="text-xs text-[#6A6560] mt-2 hover:text-slate-900 transition-colors">View all on map →</button>
      </div>

      <!-- People you might join -->
      <div>
        <div class="flex items-center justify-between mb-2.5">
          <h3 class="text-sm font-semibold text-slate-900">People you might join</h3>
          <button class="text-xs text-[#6A6560] hover:text-slate-900 transition-colors">View all</button>
        </div>
        <div>
          <div v-for="person in peopleNearby" :key="person.name" class="flex items-center gap-2.5 py-2 border-b border-warm-200">
            <div class="w-9 h-9 rounded-full flex items-center justify-center text-xs font-bold text-white flex-shrink-0" :style="{ background: person.color }">
              {{ person.initials }}
            </div>
            <div class="flex-1 min-w-0">
              <div class="text-[13px] font-semibold text-slate-900">{{ person.name }}</div>
              <div class="text-xs text-[#6A6560] truncate">{{ person.activity }}</div>
              <div class="text-[11px] text-[#9D998F]">{{ person.when }}</div>
            </div>
            <button class="w-7 h-7 rounded-full bg-accent-bg border border-warm-300 flex items-center justify-center text-primary-700 hover:bg-primary-100 transition-colors flex-shrink-0">
              <Plus class="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      </div>

      <!-- Your upcoming plans -->
      <div>
        <div class="flex items-center justify-between mb-2.5">
          <h3 class="text-sm font-semibold text-slate-900">Your upcoming plans</h3>
          <button class="text-xs text-[#6A6560] hover:text-slate-900 transition-colors">View all</button>
        </div>
        <div v-if="items.length > 0">
          <div
            v-for="activity in items.slice(0, 2)"
            :key="activity.id"
            class="flex gap-2.5 py-2 border-b border-warm-200 cursor-pointer hover:bg-warm-50 rounded-lg transition-colors"
            @click="$router.push({ name: 'activity-detail', params: { id: activity.id } })"
          >
            <div class="w-[52px] h-[52px] rounded-lg flex-shrink-0 bg-gradient-to-br from-primary-100 to-primary-200 overflow-hidden">
              <img v-if="activity.imageUrl" :src="activity.imageUrl" class="w-full h-full object-cover" alt="" />
            </div>
            <div class="min-w-0">
              <div class="text-[13px] font-semibold text-slate-900 leading-snug line-clamp-2">{{ activity.title }}</div>
              <div class="text-[11px] text-[#9D998F] mt-0.5">{{ formatShortDate(activity.startsAt) }}</div>
            </div>
          </div>
        </div>
        <div v-else class="text-[13px] text-[#9D998F] py-2">No upcoming plans yet.</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { Search, ChevronDown, Plus } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import ActivityCard from '@/components/activity/ActivityCard.vue'
import MapPanel from '@/components/map/MapPanel.vue'
import { useFiltersStore } from '@/stores/filters'
import { useActivityTypes } from '@/composables/useActivityTypes'
import { usePagination } from '@/composables/usePagination'

import { activityList } from '@/api/generated'
import { toActivityListItem } from '@/api/transforms'

const auth = useAuthStore()
const filters = useFiltersStore()
const { activityTypes } = useActivityTypes()
const showFilter = ref(false)

const greeting = computed(() => {
  const hour = new Date().getHours()
  const time = hour < 12 ? 'morning' : hour < 18 ? 'afternoon' : 'evening'
  const name = auth.user?.displayName?.split(' ')[0] || 'there'
  return `Good ${time}, ${name}.`
})

const filterOptions = computed(() => [
  { slug: 'all', name: 'All' },
  ...activityTypes.value
])

const currentFilter = computed(() => filters.typeSlug ?? 'all')

const activeFilterLabel = computed(() => {
  if (!currentFilter.value || currentFilter.value === 'all') return 'All'
  return activityTypes.value.find(t => t.slug === currentFilter.value)?.name ?? 'All'
})

// Placeholder nearby people — replace with real API data when available
const peopleNearby = [
  { name: 'Marco',  initials: 'M', color: '#6B7A4A', activity: 'Going trail running', when: 'Tomorrow, 07:30' },
  { name: 'Clara',  initials: 'C', color: '#7A5C8A', activity: 'Hiking in the Alps',  when: 'Sat, 09:00' },
  { name: 'Tom',    initials: 'T', color: '#5C7A6B', activity: 'Gravel ride',          when: 'Sun, 08:00' },
]

function setFilter(slug: string | null) {
  filters.setActivityType(slug)
  load(true)
}

function formatShortDate(dateStr: string) {
  return new Date(dateStr).toLocaleDateString(undefined, {
    weekday: 'short', day: 'numeric', month: 'short'
  })
}

const fetchActivities = async (cursor?: string) => {
  const response = await activityList({
    query: {
      city: filters.city || undefined,
      typeSlug: filters.typeSlug || undefined,
      fromDate: filters.fromDate || undefined,
      toDate: filters.toDate || undefined,
      joinable: filters.joinable || undefined,
      cursor,
      limit: 10
    }
  })

  return {
    data: (response.data?.data ?? []).map(toActivityListItem),
    nextCursor: response.data?.nextCursor
  }
}

const { items, loading, loadingMore, hasMore, load } = usePagination(fetchActivities)

onMounted(() => {
  load(true)
})
</script>
