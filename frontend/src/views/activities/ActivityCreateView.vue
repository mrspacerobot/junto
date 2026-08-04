<template>
  <PageContainer>
    <div class="max-w-2xl mx-auto">
      <div class="mb-8">
        <h1 class="text-3xl font-extrabold text-slate-900 tracking-tight">Create Activity</h1>
        <p class="text-slate-500 mt-1">Gather your community and share an experience.</p>
      </div>

      <div v-if="error" role="alert" class="mb-4 rounded-xl bg-rose-50 p-3 text-sm text-rose-700">
        {{ error }}
      </div>
      <ActivityForm
        :types="activityTypes"
        :loading="creating || typesLoading"
        @submit="handleCreate"
      />
    </div>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import PageContainer from '@/components/layout/PageContainer.vue';
import ActivityForm from '@/components/activity/ActivityForm.vue';
import { activityCreate } from '@/api/generated';
import { toActivityCreateRequest, type ActivityFormValues } from '@/api/transforms';
import { useActivityTypes } from '@/composables/useActivityTypes';

const router = useRouter();
const creating = ref(false);
const error = ref<string | null>(null);
const { activityTypes, loading: typesLoading } = useActivityTypes();

const handleCreate = async (form: ActivityFormValues) => {
  creating.value = true;
  error.value = null;
  try {
    const response = await activityCreate({ body: toActivityCreateRequest(form) });
    if (response.error || !response.data?.id) {
      error.value = 'Activity could not be created. Check the form and try again.';
      return;
    }
    await router.push(`/activities/${response.data.id}`);
  } catch {
    error.value = 'Activity could not be created. Check your connection and try again.';
  } finally {
    creating.value = false;
  }
};
</script>
