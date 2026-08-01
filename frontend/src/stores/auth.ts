import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { authMe } from '@/api/generated/sdk.gen';
import type { MeResponse } from '@/api/generated/types.gen';

export type User = MeResponse;

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null);
  const loading = ref(false);
  const initialized = ref(false);

  const isAuthenticated = computed(() => !!user.value);
  const isOnboarded = computed(() => !!user.value?.onboarded);

  async function fetchMe() {
    loading.value = true;
    try {
      const { data } = await authMe();
      user.value = data ?? null;
    } catch {
      user.value = null;
    } finally {
      loading.value = false;
      initialized.value = true;
    }
  }

  function clearSession() {
    user.value = null;
  }

  return {
    user,
    loading,
    initialized,
    isAuthenticated,
    isOnboarded,
    fetchMe,
    clearSession,
  };
});
