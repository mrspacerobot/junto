<template>
  <div class="p-4 sm:p-6 lg:p-8 pb-24 md:pb-8 min-h-full">
    <h1 class="font-display text-[26px] font-normal text-slate-900 tracking-tight mb-6">Saved</h1>

    <!-- Empty state -->
    <div
      v-if="items.length === 0 && !loading"
      class="text-center py-16 border-2 border-dashed border-warm-300 rounded-2xl"
    >
      <div class="text-[32px] mb-3">🔖</div>
      <h3 class="text-base font-semibold text-[#6A6560]">No saved plans yet</h3>
      <p class="text-sm text-[#9D998F] mt-1">Bookmark plans you'd like to join later</p>
    </div>

    <!-- Loading -->
    <div v-else-if="loading && items.length === 0" class="space-y-4">
      <div
        v-for="i in 3"
        :key="i"
        class="h-52 bg-warm-50 animate-pulse rounded-2xl border border-warm-200"
      ></div>
    </div>

    <!-- Saved activities -->
    <div v-else class="space-y-4">
      <ActivityCard v-for="activity in items" :key="activity.id" :activity="activity" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue';
import { useBookmarksStore } from '@/stores/bookmarks';
import ActivityCard from '@/components/activity/ActivityCard.vue';
import { activityList } from '@/api/generated';
import { toActivityListItem, type ActivityListItem } from '@/api/transforms';

const bookmarks = useBookmarksStore();
const items = ref<ActivityListItem[]>([]);
const loading = ref(false);

async function fetchSaved() {
  const ids = bookmarks.bookmarkedIds;
  if (ids.length === 0) {
    items.value = [];
    return;
  }

  loading.value = true;
  try {
    const response = await activityList({ query: { limit: 50 } });
    const all = (response.data?.data ?? []).map(toActivityListItem);
    items.value = all.filter((activity) => ids.includes(activity.id));
  } catch {
    items.value = [];
  } finally {
    loading.value = false;
  }
}

onMounted(fetchSaved);

watch(() => bookmarks.bookmarkedIds.length, fetchSaved);
</script>
