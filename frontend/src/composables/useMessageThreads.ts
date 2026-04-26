import { ref, onMounted } from 'vue'

export interface MessageThread {
  activityId: string
  activityTitle: string
  activityTypeSlug: string
  activityTypeName: string
  imageUrl: string | null
  participantCount: number
  participantAvatars: string[]
  lastMessageContent: string | null
  lastMessageSender: string | null
  lastMessageAt: string | null
}

export function useMessageThreads() {
  const threads = ref<MessageThread[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchThreads() {
    loading.value = true
    error.value = null
    try {
      const res = await fetch('/api/messages/threads', { credentials: 'same-origin' })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      threads.value = await res.json()
    } catch (e: any) {
      error.value = e.message
    } finally {
      loading.value = false
    }
  }

  onMounted(fetchThreads)

  return { threads, loading, error, refresh: fetchThreads }
}
