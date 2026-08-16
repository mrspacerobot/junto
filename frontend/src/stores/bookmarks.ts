import { defineStore } from 'pinia';
import { ref, computed } from 'vue';

export const useBookmarksStore = defineStore('bookmarks', () => {
  const bookmarkedIds = ref<string[]>([]);

  function toggle(id: string) {
    const idx = bookmarkedIds.value.indexOf(id);
    if (idx >= 0) {
      bookmarkedIds.value.splice(idx, 1);
    } else {
      bookmarkedIds.value.push(id);
    }
  }

  function isBookmarked(id: string) {
    return bookmarkedIds.value.includes(id);
  }

  const count = computed(() => bookmarkedIds.value.length);

  return { bookmarkedIds, toggle, isBookmarked, count };
});
