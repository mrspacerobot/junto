import { ref, onMounted } from 'vue'
import { activityTypeList } from '@/api/generated'
import {
  toActivityTypeOption,
  type ActivityTypeOption,
} from '@/api/transforms'

const activityTypes = ref<ActivityTypeOption[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

export function useActivityTypes() {
  const fetchTypes = async () => {
    if (activityTypes.value.length > 0 || loading.value) return

    loading.value = true
    error.value = null
    try {
      const response = await activityTypeList()
      activityTypes.value = (response.data ?? []).map(toActivityTypeOption)
    } catch (caught: unknown) {
      error.value = caught instanceof Error ? caught.message : 'Unable to load activity types'
    } finally {
      loading.value = false
    }
  }

  onMounted(() => { void fetchTypes() })

  return {
    activityTypes,
    loading,
    error,
    fetchTypes,
  }
}
