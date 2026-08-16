<template>
  <PageContainer v-if="auth.user">
    <div class="max-w-3xl mx-auto">
      <div class="bg-white rounded-2xl border border-slate-200 overflow-hidden shadow-sm mb-8">
        <div class="h-32 bg-slate-100"></div>
        <div class="p-8 -mt-12">
          <div class="flex items-end justify-between mb-6">
            <div class="w-24 h-24 rounded-2xl bg-white p-1 shadow-lg">
              <div class="w-full h-full bg-slate-200 rounded-xl"></div>
            </div>
            <button
              class="px-6 py-2 border border-slate-200 rounded-xl text-sm font-bold hover:bg-slate-50 transition-all"
            >
              Edit Profile
            </button>
          </div>

          <h1 class="text-3xl font-extrabold text-slate-900">{{ auth.user.displayName }}</h1>
          <p class="text-slate-500 font-medium">{{ auth.user.email }}</p>
        </div>
      </div>

      <div class="space-y-6">
        <div class="bg-white rounded-2xl border border-slate-200 p-8 shadow-sm">
          <h3 class="text-lg font-bold text-slate-900 mb-4">Account Settings</h3>
          <div class="flex items-center justify-between py-4 border-b border-slate-50">
            <div>
              <p class="font-bold text-slate-700">Invite Codes</p>
              <p class="text-sm text-slate-500">You have 5 codes remaining</p>
            </div>
            <button class="text-primary-600 font-bold text-sm hover:underline">Manage</button>
          </div>
        </div>

        <div class="bg-white rounded-2xl border border-slate-200 p-8 shadow-sm">
          <h3 class="text-lg font-bold text-rose-600 mb-2">Danger Zone</h3>
          <p class="text-sm text-slate-500 mb-6">
            Permanently delete your account and all associated data.
          </p>

          <button
            @click="confirmDelete = true"
            class="px-6 py-3 bg-rose-50 text-rose-600 font-bold rounded-xl hover:bg-rose-100 transition-all"
          >
            Delete Account
          </button>
        </div>
      </div>
    </div>

    <!-- Delete Confirmation Modal -->
    <div
      v-if="confirmDelete"
      class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-sm"
    >
      <div class="bg-white rounded-2xl p-8 max-w-md w-full shadow-2xl">
        <h3 class="text-xl font-bold text-slate-900 mb-2">Are you absolutely sure?</h3>
        <p class="text-slate-500 mb-8 leading-relaxed">
          This action cannot be undone. This will permanently delete your account and remove your
          data from our servers.
        </p>
        <div class="flex gap-4">
          <button
            @click="confirmDelete = false"
            class="flex-1 py-3 bg-slate-100 text-slate-600 font-bold rounded-xl hover:bg-slate-200 transition-all"
          >
            Cancel
          </button>
          <button
            @click="handleDelete"
            :disabled="deleting"
            class="flex-1 py-3 bg-rose-600 text-white font-bold rounded-xl hover:bg-rose-700 transition-all shadow-lg shadow-rose-100 disabled:opacity-50"
          >
            {{ deleting ? 'Deleting...' : 'Yes, Delete' }}
          </button>
        </div>
      </div>
    </div>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { userDeleteAccount } from '@/api/generated/sdk.gen';
import PageContainer from '@/components/layout/PageContainer.vue';

const auth = useAuthStore();
const router = useRouter();

const confirmDelete = ref(false);
const deleting = ref(false);

const handleDelete = async () => {
  deleting.value = true;
  try {
    const { error: err } = await userDeleteAccount();
    if (!err) {
      auth.clearSession();
      router.push('/auth/login');
    }
  } catch (e) {
    console.error('Failed to delete account', e);
  } finally {
    deleting.value = false;
    confirmDelete.value = false;
  }
};
</script>
