<template>
  <div class="min-h-screen bg-warm-100 flex items-center justify-center px-4">
    <div
      class="w-full max-w-md rounded-2xl border border-warm-200 bg-white p-8 text-center shadow-card"
    >
      <JuntoLogo :size="2" class="text-primary-700 mx-auto mb-5" />
      <h1 class="font-display text-2xl text-slate-900">Signing you in to Junto</h1>
      <p v-if="status === 'verifying'" class="mt-3 text-sm text-[#6A6560]">
        Verifying your secure link…
      </p>
      <template v-else-if="status === 'failed'">
        <p role="alert" class="mt-3 text-sm text-rose-700">
          This link is invalid, expired, or has already been used.
        </p>
        <router-link
          to="/auth/login"
          class="mt-6 inline-flex rounded-xl bg-primary-700 px-5 py-2.5 text-sm font-semibold text-white"
          >Request a new link</router-link
        >
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import JuntoLogo from '@/components/JuntoLogo.vue';
import { authVerifyToken } from '@/api/generated/sdk.gen';
import { useAuthStore } from '@/stores/auth';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const status = ref<'verifying' | 'failed'>('verifying');

onMounted(async () => {
  const token = typeof route.query.token === 'string' ? route.query.token : '';
  // Remove the one-time secret from browser history before making the request.
  window.history.replaceState({}, '', '/auth/verify');
  if (!token) {
    status.value = 'failed';
    return;
  }

  try {
    const { error } = await authVerifyToken({ body: { token } });
    if (error) {
      status.value = 'failed';
      return;
    }
    await auth.fetchMe();
    await router.replace(auth.isOnboarded ? '/activities' : '/auth/onboard');
  } catch {
    status.value = 'failed';
  }
});
</script>
