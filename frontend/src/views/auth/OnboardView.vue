<template>
  <div class="min-h-screen bg-slate-50 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
    <div class="sm:mx-auto sm:w-full sm:max-w-md text-center">
      <h2 class="text-3xl font-extrabold text-slate-900 tracking-tight">One last thing</h2>
      <p class="mt-2 text-sm text-slate-600">
        How should we call you? This will be your public name on Junto.
      </p>
    </div>

    <div class="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
      <div class="bg-white py-8 px-4 shadow-sm border border-slate-200 sm:rounded-2xl sm:px-10">
        <form @submit.prevent="handleOnboard" class="space-y-6">
          <div>
            <label for="displayName" class="block text-sm font-semibold text-slate-700"
              >Display Name</label
            >
            <div class="mt-1">
              <input
                id="displayName"
                v-model="displayName"
                type="text"
                required
                minlength="2"
                maxlength="50"
                placeholder="e.g. Alex"
                class="appearance-none block w-full px-4 py-3 border border-slate-200 rounded-xl shadow-sm placeholder-slate-400 focus:outline-none focus:ring-primary-500 focus:border-primary-500 sm:text-sm transition-all"
              />
            </div>
          </div>

          <div>
            <button
              type="submit"
              :disabled="loading || displayName.length < 2"
              class="w-full flex justify-center py-3 px-4 border border-transparent rounded-xl shadow-md text-sm font-bold text-white bg-primary-600 hover:bg-primary-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500 disabled:opacity-50 transition-all"
            >
              {{ loading ? 'Saving...' : 'Start Exploring' }}
            </button>
          </div>
        </form>

        <div
          v-if="error"
          class="mt-4 p-3 bg-rose-50 text-rose-700 text-xs font-bold rounded-lg text-center border border-rose-100"
        >
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
import { userOnboard } from '@/api/generated/sdk.gen';
import { apiErrorMessage } from '@/api/transforms';

const router = useRouter();
const auth = useAuthStore();

const displayName = ref('');
const loading = ref(false);
const error = ref('');

const handleOnboard = async () => {
  loading.value = true;
  error.value = '';
  try {
    const { error: err } = await userOnboard({
      body: { displayName: displayName.value },
    });
    if (err) {
      error.value = apiErrorMessage(err, 'Failed to save');
    } else {
      await auth.fetchMe();
      router.push('/activities');
    }
  } catch {
    error.value = 'Network error';
  } finally {
    loading.value = false;
  }
};
</script>
