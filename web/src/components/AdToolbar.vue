<script setup lang="ts">
import { computed } from 'vue'
import { FILTERS, POSTURES, SORTS } from '../advisor/ranking'
import type { FilterId, Posture, SortKey } from '../advisor/ranking'

const props = defineProps<{
  sort: SortKey
  posture: Posture
  filters: FilterId[]
  shown: number
  total: number
  lifeCost: number
}>()
defineEmits<{
  'update:sort': [key: SortKey]
  'update:posture': [posture: Posture]
  'toggle-filter': [id: FilterId]
  'clear-filters': []
}>()

const hidden = computed(() => props.total - props.shown)
const lifeCostGold = computed(() => Math.round(props.lifeCost))
</script>

<template>
  <div class="flex flex-col gap-3 rounded-lg border border-accent/40 bg-surface-raised/75 p-3">
    <div class="flex flex-wrap items-end gap-x-6 gap-y-3">
      <div class="flex flex-col gap-1">
        <label for="ad-sort" class="text-xs font-medium text-ink-muted">Sort by</label>
        <select
          id="ad-sort"
          class="relief rounded-md border border-ink-muted/40 bg-surface px-2 py-1.5 text-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
          :value="sort"
          @change="$emit('update:sort', ($event.target as HTMLSelectElement).value as SortKey)"
        >
          <option v-for="option in SORTS" :key="option.id" :value="option.id">
            {{ option.label }}
          </option>
        </select>
      </div>

      <!--
        Radios, not a slider: three named stances read better than a number, and the group tells a
        screen reader what it is choosing between.
      -->
      <fieldset class="flex flex-col gap-1">
        <legend class="text-xs font-medium text-ink-muted">Risk posture</legend>
        <div class="flex rounded-md border border-ink-muted/40">
          <label
            v-for="(option, index) in POSTURES"
            :key="option.id"
            class="cursor-pointer px-3 py-1.5 text-sm focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-accent"
            :class="[
              index === 0 ? 'rounded-l-md' : '',
              index === POSTURES.length - 1 ? 'rounded-r-md' : 'border-r border-ink-muted/40',
              posture === option.id
                ? 'relief-pressed bg-accent font-semibold text-surface'
                : 'text-ink-muted',
            ]"
          >
            <input
              type="radio"
              name="posture"
              class="sr-only"
              :value="option.id"
              :checked="posture === option.id"
              @change="$emit('update:posture', option.id)"
            />
            {{ option.label }}
          </label>
        </div>
      </fieldset>
    </div>

    <p class="text-xs text-ink-muted">
      Value prices a life at {{ lifeCostGold }}g right now, then asks whether the reward covers the
      risk of losing one. Balanced is the stance the auto-player uses.
    </p>

    <fieldset class="flex flex-col gap-1.5">
      <legend class="text-xs font-medium text-ink-muted">Show only</legend>
      <div class="flex flex-wrap gap-x-4 gap-y-1.5">
        <label
          v-for="filter in FILTERS"
          :key="filter.id"
          class="flex items-center gap-1.5 text-sm text-ink-muted"
          :title="filter.hint"
        >
          <input
            type="checkbox"
            class="size-4 accent-accent focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
            :value="filter.id"
            :checked="filters.includes(filter.id)"
            @change="$emit('toggle-filter', filter.id)"
          />
          {{ filter.label }}
        </label>
      </div>
    </fieldset>

    <p v-if="filters.length" class="flex flex-wrap items-center gap-2 text-xs" role="status">
      <span>Showing {{ shown }} of {{ total }} jobs, {{ hidden }} filtered out.</span>
      <button
        type="button"
        class="relief rounded border border-ink-muted/40 bg-surface-raised/60 px-2 py-0.5 hover:border-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
        @click="$emit('clear-filters')"
      >
        Clear filters
      </button>
    </p>
  </div>
</template>
