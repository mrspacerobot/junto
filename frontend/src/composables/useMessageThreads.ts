import { ref, onMounted } from 'vue'
import { messageGetThreads } from '@/api/generated'
import { toMessageThread } from '@/api/transforms'

export type MessageThread = ReturnType<typeof toMessageThread>

export function useMessageThreads() {
  const threads = ref<MessageThread[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchThreads() {
    loading.value = true
    error.value = null
    try {
      const response = await messageGetThreads()
      if (response.error) throw new Error('Unable to load message threads')
      threads.value = (response.data ?? []).map(toMessageThread)
    } catch (caught: unknown) {
      error.value = caught instanceof Error ? caught.message : 'Unable to load message threads'
    } finally {
      loading.value = false
    }
  }

  onMounted(() => { void fetchThreads() })

  return { threads, loading, error, refresh: fetchThreads }
}
