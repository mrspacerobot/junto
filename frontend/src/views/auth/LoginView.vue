<template>
  <div class="min-h-screen bg-warm-100 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
    <div class="sm:mx-auto sm:w-full sm:max-w-md text-center">
      <JuntoLogo :size="2.2" class="text-primary-700 mx-auto mb-6" />
      <h2 class="text-3xl font-extrabold text-slate-900 tracking-tight font-display">
        Welcome to Junto
      </h2>
      <p class="mt-2 text-sm text-[#6A6560]">
        {{ step === 'EMAIL' ? modeDescription : verificationPrompt }}
      </p>
    </div>

    <div class="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
      <div class="bg-white py-8 px-4 shadow-card border border-warm-200 sm:rounded-2xl sm:px-10">
        <template v-if="step === 'EMAIL'">
          <div class="grid grid-cols-2 gap-2 mb-6" aria-label="Authentication choice">
            <button
              type="button"
              :aria-pressed="mode === 'SIGN_IN'"
              :class="modeButtonClass('SIGN_IN')"
              @click="setMode('SIGN_IN')"
            >
              Sign in
            </button>
            <button
              type="button"
              :aria-pressed="mode === 'SIGN_UP'"
              :class="modeButtonClass('SIGN_UP')"
              @click="setMode('SIGN_UP')"
            >
              Create account
            </button>
          </div>

          <form novalidate @submit.prevent="handleRequestCode" class="space-y-6">
            <div>
              <label for="email" class="block text-sm font-semibold text-slate-700">Email address</label>
              <div class="mt-1">
                <input
                  id="email"
                  v-model="email"
                  type="email"
                  required
                  autocomplete="email"
                  placeholder="you@example.com"
                  class="appearance-none block w-full px-4 py-3 border border-warm-300 rounded-xl shadow-sm placeholder-[#9D998F] focus:outline-none focus:ring-primary-500 focus:border-primary-500 sm:text-sm transition-all"
                />
              </div>
            </div>

            <div v-if="mode === 'SIGN_UP'">
              <label for="inviteCode" class="block text-sm font-semibold text-slate-700">Invite Code</label>
              <div class="mt-1">
                <input
                  id="inviteCode"
                  v-model="inviteCode"
                  type="text"
                  required
                  autocomplete="off"
                  placeholder="Required to create an account"
                  class="appearance-none block w-full px-4 py-3 border border-warm-300 rounded-xl shadow-sm placeholder-[#9D998F] focus:outline-none focus:ring-primary-500 focus:border-primary-500 sm:text-sm transition-all"
                />
              </div>
              <p class="mt-2 text-xs text-[#6A6560]">A valid invite is required for every new account.</p>
            </div>

            <div>
              <button
                type="submit"
                :disabled="loading"
                class="w-full flex justify-center py-3 px-4 border border-transparent rounded-xl shadow-md text-sm font-bold text-white bg-primary-600 hover:bg-primary-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500 disabled:opacity-50 transition-all"
              >
                {{ requestButtonLabel }}
              </button>
            </div>
          </form>
        </template>

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
                inputmode="numeric"
                autocomplete="one-time-code"
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
              @click="backToEmail"
              class="w-full flex justify-center py-3 px-4 text-sm font-bold text-slate-600 hover:text-slate-900 transition-all"
            >
              Back to email
            </button>
          </div>
        </form>

        <div
          v-if="error"
          role="alert"
          aria-live="polite"
          class="mt-4 p-3 bg-rose-50 text-rose-700 text-xs font-bold rounded-lg text-center border border-rose-100"
        >
          {{ error }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { authRequestCode, authRequestSignupCode, authVerify } from '@/api/generated/sdk.gen';
import { apiErrorMessage } from '@/api/transforms';
import JuntoLogo from '@/components/JuntoLogo.vue';

type AuthMode = 'SIGN_IN' | 'SIGN_UP';

const router = useRouter();
const auth = useAuthStore();

const step = ref<'EMAIL' | 'CODE'>('EMAIL');
const mode = ref<AuthMode>('SIGN_IN');
const email = ref('');
const inviteCode = ref('');
const code = ref('');
const loading = ref(false);
const error = ref('');

const modeDescription = computed(() => mode.value === 'SIGN_IN'
  ? 'Sign in to your existing account. No invite is needed.'
  : 'Create a new account with a valid invite.');

const verificationPrompt = computed(() => {
  const destination = email.value.trim();
  return mode.value === 'SIGN_UP'
    ? `Enter the 6-digit code sent to ${destination} to finish creating your account.`
    : `If an account exists for ${destination}, enter the 6-digit code from the email.`;
});

const requestButtonLabel = computed(() => {
  if (loading.value) return 'Sending...';
  return mode.value === 'SIGN_UP' ? 'Create account' : 'Send sign-in code';
});

const modeButtonClass = (buttonMode: AuthMode) => [
  'rounded-xl px-3 py-2 text-sm font-bold transition-all border',
  mode.value === buttonMode
    ? 'bg-primary-600 text-white border-primary-600'
    : 'bg-white text-slate-600 border-warm-300 hover:border-primary-400',
];

const setMode = (nextMode: AuthMode) => {
  mode.value = nextMode;
  error.value = '';
};

const backToEmail = () => {
  step.value = 'EMAIL';
  code.value = '';
  error.value = '';
};

const handleRequestCode = async () => {
  error.value = '';
  if (!email.value.trim()) {
    error.value = 'Enter your email address.';
    return;
  }
  if (mode.value === 'SIGN_UP' && !inviteCode.value.trim()) {
    error.value = 'Enter your invite code to create an account.';
    return;
  }

  loading.value = true;
  try {
    const result = mode.value === 'SIGN_UP'
      ? await authRequestSignupCode({
          body: { email: email.value.trim(), inviteCode: inviteCode.value.trim() },
        })
      : await authRequestCode({ body: { email: email.value.trim() } });
    if (result.error) {
      error.value = apiErrorMessage(result.error, 'Failed to request code');
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
      body: { email: email.value.trim(), code: code.value },
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
