import { ref, type Ref } from 'vue'

interface CursorPage<T> {
  data: T[]
  nextCursor?: string
}

export function usePagination<T>(fetchFn: (cursor?: string) => Promise<CursorPage<T>>) {
  const items = ref<T[]>([]) as Ref<T[]>
  const loading = ref(false)
  const loadingMore = ref(false)
  const nextCursor = ref<string>()
  const hasMore = ref(true)
  const error = ref<string | null>(null)
  let requestVersion = 0

  const load = async (reset = false) => {
    if (!reset && (loading.value || loadingMore.value || !hasMore.value)) return

    if (reset) {
      requestVersion += 1
      items.value = []
      nextCursor.value = undefined
      hasMore.value = true
      loading.value = true
    } else {
      loadingMore.value = true
    }

    const version = requestVersion
    try {
      const page = await fetchFn(reset ? undefined : nextCursor.value)
      // A filter reset supersedes older in-flight pages.
      if (version !== requestVersion) return
      items.value = reset ? page.data : [...items.value, ...page.data]
      nextCursor.value = page.nextCursor
      hasMore.value = Boolean(page.nextCursor)
      error.value = null
    } catch (caught: unknown) {
      if (version === requestVersion) {
        error.value = caught instanceof Error ? caught.message : 'Unable to load results'
      }
    } finally {
      if (version === requestVersion) {
        loading.value = false
        loadingMore.value = false
      }
    }
  }

  return {
    items,
    loading,
    loadingMore,
    hasMore,
    error,
    load,
  }
}
