<template>
  <div data-testid="activity-card"
       class="bg-white rounded-[14px] shadow-card hover:shadow-card-hover transition-all border border-warm-200 overflow-hidden group flex flex-col md:flex-row cursor-pointer"
       @click="$router.push({ name: 'activity-detail', params: { id: activity.id } })">
    <!-- Left Content: Info -->
    <div class="flex-1 p-4 flex flex-col min-w-0">
      <!-- Creator & Activity Badge Header -->
      <div class="flex items-center justify-between mb-3">
        <div class="flex items-center gap-2">
          <img
            v-if="activity.creatorAvatarUrl"
            :src="activity.creatorAvatarUrl"
            class="w-7 h-7 rounded-full object-cover"
            alt="Creator"
          />
          <div v-else class="w-7 h-7 bg-primary-100 rounded-full flex items-center justify-center">
            <span class="text-[10px] font-bold text-primary-700">{{ initials }}</span>
          </div>
          <span class="text-[13px] text-[#6A6560]">
            <strong class="text-slate-900 font-semibold">{{ activity.creatorDisplayName }}</strong> is going {{ activity.activityTypeSlug }}
          </span>
        </div>
        <component :is="typeIcon" class="w-4 h-4 text-[#9D998F]" />
      </div>

      <!-- Title -->
      <h3 class="font-display text-[21px] font-normal text-slate-900 leading-tight mb-1.5">
        {{ activity.title }}
      </h3>

      <!-- Date + Location -->
      <div class="flex items-center gap-3 mb-3 flex-wrap">
        <span class="flex items-center gap-1 text-xs text-[#6A6560]">
          <Calendar class="w-3 h-3 text-[#9D998F]" />
          {{ formatFullDate(activity.startsAt) }}
        </span>
        <span class="flex items-center gap-1 text-xs text-[#6A6560]">
          <MapPin class="w-3 h-3 text-[#9D998F]" />
          {{ activity.city || 'TBD' }}
        </span>
      </div>

      <!-- Stats row: Level badge + custom fields -->
      <div class="flex items-center gap-4 mb-3 flex-wrap">
        <LevelBadge v-if="levelValue" :level="levelValue" />
        <div v-for="field in statFields" :key="field.name" class="flex flex-col gap-0.5">
          <span class="text-[11px] text-[#9D998F] flex items-center gap-1">
            <component :is="field.icon" class="w-3 h-3" />
            {{ field.label }}
          </span>
          <span class="text-xs font-semibold text-slate-900">{{ field.displayValue }}</span>
        </div>
      </div>

      <!-- Footer: Participants + Join + Bookmark -->
      <div class="flex items-center justify-between mt-auto pt-3 border-t border-warm-200">
        <div class="flex items-center gap-2">
          <div class="flex -space-x-2">
            <img
              v-for="(avatar, i) in activity.participantAvatars"
              :key="i"
              :src="avatar"
              class="w-6 h-6 rounded-full border-2 border-white object-cover"
              alt="Participant"
            />
            <div v-if="activity.currentParticipants > activity.participantAvatars?.length" class="w-6 h-6 rounded-full border-2 border-white bg-warm-200 flex items-center justify-center text-[9px] font-bold text-[#6A6560]">
              +{{ activity.currentParticipants - activity.participantAvatars.length }}
            </div>
          </div>
          <span class="text-xs text-[#6A6560]">
            {{ activity.currentParticipants }} people joining
          </span>
        </div>

        <div class="flex items-center gap-2" @click.stop>
          <button @click="bookmarksStore.toggle(activity.id)" class="p-1.5 rounded-lg transition-colors"
                  :class="bookmarked ? 'text-primary-700' : 'text-[#9D998F] hover:text-[#6A6560]'">
            <Bookmark class="w-4 h-4" :fill="bookmarked ? 'currentColor' : 'none'" />
          </button>
          <button
            @click="$router.push({ name: 'activity-detail', params: { id: activity.id } })"
            class="rounded-full px-4 py-1.5 text-[13px] font-semibold transition-colors"
            :class="joined
              ? 'bg-accent-bg text-accent border border-accent'
              : 'bg-primary-700 text-white hover:bg-primary-800'"
          >
            {{ joined ? '✓ Joined' : `Join ${activity.creatorDisplayName?.split(' ')[0]}` }}
          </button>
        </div>
      </div>
    </div>

    <!-- Right Content: Image -->
    <div class="w-full md:w-[180px] h-44 md:h-auto relative overflow-hidden flex-shrink-0 self-center m-3 rounded-[10px]">
      <img
        v-if="activity.imageUrl"
        :src="activity.imageUrl"
        class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
        alt="Activity"
      />
      <div v-else class="w-full h-full bg-gradient-to-br from-primary-100 to-primary-200 flex items-center justify-center">
        <component :is="typeIcon" class="w-8 h-8 text-primary-400" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, type Component } from 'vue'
import {
  MapPin,
  Calendar,
  Bookmark,
  Activity as ActivityIcon,
  Timer,
  Mountain,
  Bike,
  Flame,
  Clock
} from 'lucide-vue-next'
import { useActivityTypes } from '@/composables/useActivityTypes'
import { useBookmarksStore } from '@/stores/bookmarks'
import LevelBadge from './LevelBadge.vue'
import type { ActivityListItem } from '@/api/transforms'

const props = defineProps<{
  activity: ActivityListItem
}>()

const { activityTypes } = useActivityTypes()
const bookmarksStore = useBookmarksStore()
const bookmarked = computed(() => bookmarksStore.isBookmarked(props.activity.id))
const joined = ref(false)

const initials = computed(() => {
  const name = props.activity.creatorDisplayName
  if (!name) return '?'
  return name.split(' ').map((w: string) => w[0]).join('').toUpperCase().slice(0, 2)
})

const typeIcon = computed(() => {
  const icons: Record<string, Component> = {
    cycling: Bike,
    climbing: Mountain,
    running: ActivityIcon,
    skiing: Flame,
    yoga: Clock
  }
  return icons[props.activity.activityTypeSlug] || ActivityIcon
})

const formatFullDate = (dateStr: string) => {
  return new Date(dateStr).toLocaleDateString(undefined, {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// Find the level/experienceLevel custom field value
const levelValue = computed(() => {
  if (!props.activity.customFields) return null
  const value = props.activity.customFields.level
    ?? props.activity.customFields.experienceLevel
    ?? props.activity.customFields.difficulty
  return value == null ? null : String(value)
})

// Stat fields (excluding level which gets its own badge)
const statFields = computed(() => {
  const type = activityTypes.value.find(t => t.slug === props.activity.activityTypeSlug)
  if (!type || !type.customFields || !props.activity.customFields) return []

  return type.customFields
    .map(def => {
      const value = props.activity.customFields[def.name]
      if (value === undefined || value === null || value === '') return null
      // Skip level — it gets its own badge
      if (['level', 'experienceLevel', 'difficulty'].includes(def.name.toLowerCase())) return null

      let displayValue = String(value)
      if (def.type === 'NUMBER_WITH_UNIT' && def.unit) {
        displayValue = `${String(value)} ${def.unit.toLowerCase()}`
      } else if (def.type === 'SELECT') {
        displayValue = String(value).charAt(0).toUpperCase() + String(value).slice(1).toLowerCase()
      } else if (def.type === 'BOOLEAN') {
        displayValue = value ? 'Yes' : 'No'
      }

      return {
        name: def.name,
        label: def.label,
        displayValue,
        icon: getFieldIcon(def.name)
      }
    })
    .filter(f => f !== null)
    .slice(0, 2)
})

const getFieldIcon = (fieldName: string) => {
  const icons: Record<string, Component> = {
    distance: Bike,
    pace: Timer,
    duration: Clock,
    terrain: Mountain,
    elevation: Mountain,
  }
  return icons[fieldName.toLowerCase()] || ActivityIcon
}
</script>
