<template>
  <div class="min-h-screen bg-warm-100 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
    <div class="sm:mx-auto sm:w-full sm:max-w-md text-center">
      <JuntoLogo :size="2.2" class="text-primary-700 mx-auto mb-6" />
      <h2 class="text-3xl font-extrabold text-slate-900 tracking-tight font-display">
        Welcome to Junto
      </h2>
      <p class="mt-2 text-sm text-[#6A6560]">
        {{ step === 'EMAIL' ? 'Sign in or create an account to get started.' : 'Enter the 6-digit code sent to your email.' }}
      </p>
    </div>

    <div class="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
      <div class="bg-white py-8 px-4 shadow-card border border-warm-200 sm:rounded-2xl sm:px-10">
        <form v-if="step === 'EMAIL'" @submit.prevent="handleRequestCode" class="space-y-6">
          <div>
            <label for="email" class="block text-sm font-semibold text-slate-700">Email address</label>
            <div class="mt-1">
              <input
                id="email"
                v-model="email"
                type="email"
                required
                placeholder="you@example.com"
                class="appearance-none block w-full px-4 py-3 border border-warm-300 rounded-xl shadow-sm placeholder-[#9D998F] focus:outline-none focus:ring-primary-500 focus:border-primary-500 sm:text-sm transition-all"
              />
            </div>
          </div>

          <div>
            <label for="inviteCode" class="block text-sm font-semibold text-slate-700">Invite Code (New users only)</label>
            <div class="mt-1">
              <input
                id="inviteCode"
                v-model="inviteCode"
                type="text"
                placeholder="Optional if you already have an account"
                class="appearance-none block w-full px-4 py-3 border border-warm-300 rounded-xl shadow-sm placeholder-[#9D998F] focus:outline-none focus:ring-primary-500 focus:border-primary-500 sm:text-sm transition-all"
              />
            </div>
          </div>

          <div>
            <button
              type="submit"
              :disabled="loading"
              class="w-full flex justify-center py-3 px-4 border border-transparent rounded-xl shadow-md text-sm font-bold text-white bg-primary-600 hover:bg-primary-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500 disabled:opacity-50 transition-all"
            >
              {{ loading ? 'Sending...' : 'Continue' }}
            </button>
          </div>
        </form>

        <form v-else @submit.prevent="handleVerify" class="space-y-6">
          <div>
            <label for="code" class="block text-sm font-semibold text-slate-700 text-center mb-4">Verification Code</label>
            <div class="mt-1">
              <input
                id="code"
                v-model="code"
                type="text"
                required
                maxlength="6"
                placeholder="000000"
                class="block w-full px-4 py-4 border-2 border-warm-300 rounded-2xl shadow-sm text-center text-2xl font-bold tracking-[1em] focus:outline-none focus:ring-primary-500 focus:border-primary-500 transition-all"
              />
            </div>
          </div>

          <div class="flex flex-col gap-3">
            <button
              type="submit"
              :disabled="loading"
              class="w-full flex justify-center py-3 px-4 border border-transparent rounded-xl shadow-md text-sm font-bold text-white bg-primary-600 hover:bg-primary-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500 disabled:opacity-50 transition-all"
            >
              {{ loading ? 'Verifying...' : 'Verify & Sign In' }}
            </button>
            <button
              type="button"
              @click="step = 'EMAIL'"
              class="w-full flex justify-center py-3 px-4 text-sm font-bold text-slate-600 hover:text-slate-900 transition-all"
            >
              Back to email
            </button>
          </div>
        </form>

        <div v-if="error" class="mt-4 p-3 bg-rose-50 text-rose-700 text-xs font-bold rounded-lg text-center border border-rose-100">
          {{ error }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { authRequestCode, authVerify } from '@/api/generated/sdk.gen';
import { apiErrorMessage } from '@/api/transforms';
import JuntoLogo from '@/components/JuntoLogo.vue';

const router = useRouter();
const auth = useAuthStore();

const step = ref<'EMAIL' | 'CODE'>('EMAIL');
const email = ref('');
const inviteCode = ref('');
const code = ref('');
const loading = ref(false);
const error = ref('');

const handleRequestCode = async () => {
  loading.value = true;
  error.value = '';
  try {
    const { error: err } = await authRequestCode({
      body: { email: email.value, inviteCode: inviteCode.value }
    });
    if (err) {
      error.value = apiErrorMessage(err, 'Failed to request code');
    } else {
      step.value = 'CODE';
    }
  } catch {
    error.value = 'Network error';
  } finally {
    loading.value = false;
  }
};

const handleVerify = async () => {
  loading.value = true;
  error.value = '';
  try {
    const { error: err } = await authVerify({
      body: { email: email.value, code: code.value }
    });
    if (err) {
      error.value = apiErrorMessage(err, 'Invalid code');
    } else {
      await auth.fetchMe();
      if (auth.isOnboarded) {
        router.push('/activities');
      } else {
        router.push('/auth/onboard');
      }
    }
  } catch {
    error.value = 'Network error';
  } finally {
    loading.value = false;
  }
};
</script>
