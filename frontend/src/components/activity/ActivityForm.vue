<template>
  <form @submit.prevent="$emit('submit', form)" class="space-y-8 bg-white border border-slate-200 rounded-2xl p-8 shadow-sm">
    <div class="grid grid-cols-1 md:grid-cols-2 gap-8">
      <!-- Basic Info -->
      <div class="space-y-6">
        <h3 class="text-lg font-bold text-slate-900 border-b border-slate-100 pb-2">Basic Information</h3>

        <div class="space-y-2">
          <label class="text-sm font-semibold text-slate-700">Activity Type</label>
          <div class="grid grid-cols-3 gap-3">
             <button
              v-for="type in types"
              :key="type.slug"
              type="button"
              @click="form.typeSlug = type.slug"
              :class="[
                'flex flex-col items-center justify-center p-3 rounded-xl border-2 transition-all',
                form.typeSlug === type.slug
                  ? 'border-primary-500 bg-primary-50 text-primary-700'
                  : 'border-slate-100 bg-slate-50 text-slate-500 hover:border-slate-200'
              ]"
            >
              <span class="text-xs font-bold">{{ type.name }}</span>
            </button>
          </div>
        </div>

        <div class="space-y-2">
          <label class="text-sm font-semibold text-slate-700">Title</label>
          <input
            v-model="form.title"
            type="text"
            required
            placeholder="e.g. Morning Bouldering at The Spot"
            class="w-full bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-sm focus:ring-2 focus:ring-primary-500 outline-none transition-all"
          />
        </div>

        <div class="space-y-2">
          <label class="text-sm font-semibold text-slate-700">Description</label>
          <textarea
            v-model="form.description"
            rows="4"
            placeholder="Tell us more about the activity..."
            class="w-full bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-sm focus:ring-2 focus:ring-primary-500 outline-none transition-all resize-none"
          ></textarea>
        </div>
      </div>

      <!-- Logistics -->
      <div class="space-y-6">
        <h3 class="text-lg font-bold text-slate-900 border-b border-slate-100 pb-2">Logistics</h3>

        <div class="grid grid-cols-2 gap-4">
          <div class="space-y-2">
            <label class="text-sm font-semibold text-slate-700">Date & Time</label>
            <input
              v-model="form.startsAt"
              type="datetime-local"
              required
              class="w-full bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-sm focus:ring-2 focus:ring-primary-500 outline-none transition-all"
            />
          </div>
          <div class="space-y-2">
            <label class="text-sm font-semibold text-slate-700">City</label>
            <input
              v-model="form.city"
              type="text"
              required
              placeholder="e.g. Boulder"
              class="w-full bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-sm focus:ring-2 focus:ring-primary-500 outline-none transition-all"
            />
          </div>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <div class="space-y-2">
            <label class="text-sm font-semibold text-slate-700">Max Participants</label>
            <input
              v-model.number="form.maxParticipants"
              type="number"
              min="2"
              max="50"
              required
              class="w-full bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-sm focus:ring-2 focus:ring-primary-500 outline-none transition-all"
            />
          </div>
          <div class="space-y-2">
            <label class="text-sm font-semibold text-slate-700">Join Policy</label>
            <select
              v-model="form.joinPolicy"
              class="w-full bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-sm focus:ring-2 focus:ring-primary-500 outline-none transition-all"
            >
              <option value="OPEN">Open (Anyone can join)</option>
              <option value="APPROVAL_REQUIRED">Approval Required</option>
            </select>
          </div>
        </div>

        <div class="space-y-2">
          <label class="text-sm font-semibold text-slate-700">Signup Deadline (Optional)</label>
          <input
            v-model="form.signupDeadline"
            type="datetime-local"
            class="w-full bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-sm focus:ring-2 focus:ring-primary-500 outline-none transition-all"
          />
          <p class="text-xs text-slate-400 italic">Defaults to activity start time if not set.</p>
        </div>
      </div>

      <!-- Custom Fields (Dynamic) -->
      <div v-if="selectedType?.customFields?.length" class="space-y-6 md:col-span-2">
        <h3 class="text-lg font-bold text-slate-900 border-b border-slate-100 pb-2">Activity Specific Details</h3>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div v-for="field in selectedType.customFields" :key="field.name" class="space-y-2">
            <label class="text-sm font-semibold text-slate-700">
              {{ field.label }} <span v-if="field.required" class="text-red-500">*</span>
            </label>

            <template v-if="field.type === 'SELECT'">
              <select
                v-model="form.customFields[field.name]"
                :required="field.required"
                class="w-full bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-sm focus:ring-2 focus:ring-primary-500 outline-none transition-all"
              >
                <option disabled value="">Select {{ field.label }}</option>
                <option v-for="opt in field.options" :key="opt" :value="opt">{{ opt }}</option>
              </select>
            </template>

            <template v-else-if="field.type === 'NUMBER'">
              <input
                v-model.number="form.customFields[field.name]"
                type="number"
                :required="field.required"
                class="w-full bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-sm focus:ring-2 focus:ring-primary-500 outline-none transition-all"
              />
            </template>

            <template v-else-if="field.type === 'NUMBER_WITH_UNIT'">
              <div class="flex items-center gap-2">
                <input
                  v-model.number="form.customFields[field.name]"
                  type="number"
                  step="any"
                  :required="field.required"
                  class="flex-1 bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-sm focus:ring-2 focus:ring-primary-500 outline-none transition-all"
                />
                <span class="text-sm font-medium text-slate-500 min-w-fit">{{ field.unit ? (unitLabels[field.unit] ?? field.unit) : '' }}</span>
              </div>
            </template>

            <template v-else-if="field.type === 'BOOLEAN'">
              <label class="flex items-center space-x-3 cursor-pointer">
                <input
                  v-model="form.customFields[field.name]"
                  type="checkbox"
                  :required="field.required"
                  class="w-5 h-5 rounded border-slate-300 text-primary-600 focus:ring-primary-500"
                />
                <span class="text-sm text-slate-700">Yes</span>
              </label>
            </template>

            <template v-else-if="field.type === 'TIME_OF_DAY'">
              <input
                v-model="form.customFields[field.name]"
                type="time"
                :required="field.required"
                class="w-full bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-sm focus:ring-2 focus:ring-primary-500 outline-none transition-all"
              />
            </template>

            <template v-else-if="field.type === 'DATE_RANGE'">
              <div class="grid grid-cols-2 gap-2">
                <input
                  :value="dateRangeValue(field.name).start"
                  @input="setDateRangePart(field.name, 'start', $event)"
                  type="date"
                  :required="field.required"
                  class="bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-sm focus:ring-2 focus:ring-primary-500 outline-none transition-all"
                />
                <input
                  :value="dateRangeValue(field.name).end"
                  @input="setDateRangePart(field.name, 'end', $event)"
                  type="date"
                  :required="field.required"
                  class="bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-sm focus:ring-2 focus:ring-primary-500 outline-none transition-all"
                />
              </div>
            </template>

            <template v-else>
              <input
                v-model="form.customFields[field.name]"
                type="text"
                :required="field.required"
                class="w-full bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-sm focus:ring-2 focus:ring-primary-500 outline-none transition-all"
              />
            </template>
          </div>
        </div>
      </div>

    </div>

    <div class="pt-6 border-t border-slate-100 flex justify-end gap-4">
      <button
        type="button"
        @click="$router.back()"
        class="px-6 py-3 border border-slate-200 text-slate-600 font-bold rounded-xl hover:bg-slate-50 transition-colors"
      >
        Cancel
      </button>
      <button
        type="submit"
        :disabled="loading"
        class="px-10 py-3 bg-primary-600 text-white font-bold rounded-xl hover:bg-primary-700 disabled:opacity-50 transition-all shadow-md shadow-primary-100"
      >
        {{ loading ? 'Creating...' : 'Create Activity' }}
      </button>
    </div>
  </form>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type {
  ActivityFormValues,
  ActivityTypeOption,
  DateRangeValue,
} from '@/api/transforms'

const props = defineProps<{
  types: ActivityTypeOption[]
  loading?: boolean
}>()

defineEmits<{ submit: [form: ActivityFormValues] }>()

const unitLabels: Record<string, string> = {
  KM: 'km',
  MILES: 'miles',
  METERS: 'm',
  MINUTES: 'minutes',
  HOURS: 'hours',
  LAPS: 'laps',
  SETS: 'sets',
  REPS: 'reps',
  KG: 'kg',
  LBS: 'lbs',
}

const form = ref<ActivityFormValues>({
  title: '',
  description: '',
  typeSlug: props.types[0]?.slug ?? '',
  city: '',
  startsAt: '',
  signupDeadline: '',
  maxParticipants: 10,
  joinPolicy: 'OPEN',
  customFields: {},
})

const selectedType = computed(() =>
  props.types.find(type => type.slug === form.value.typeSlug),
)

watch(() => props.types, types => {
  if (!form.value.typeSlug && types.length > 0) {
    form.value.typeSlug = types[0].slug
  }
}, { immediate: true })

watch(() => form.value.typeSlug, () => {
  form.value.customFields = {}
  selectedType.value?.customFields.forEach(field => {
    if (field.type === 'DATE_RANGE') {
      form.value.customFields[field.name] = { start: '', end: '' }
    }
  })
})

function dateRangeValue(fieldName: string): DateRangeValue {
  const value = form.value.customFields[fieldName]
  return typeof value === 'object' ? value : { start: '', end: '' }
}

function setDateRangePart(fieldName: string, part: keyof DateRangeValue, event: Event) {
  const input = event.target as HTMLInputElement
  form.value.customFields[fieldName] = {
    ...dateRangeValue(fieldName),
    [part]: input.value,
  }
}
</script>
