<script setup lang="ts">
import AppIcon from './AppIcon.vue'
import TurnSpinner from './TurnSpinner.vue'
import { crestArt, type Faction } from '../assets/artwork'
import type { ReputationView } from '../api/types'

const props = defineProps<{
  reputation: ReputationView | null
  disabled: boolean
  scouting: boolean
}>()
defineEmits<{ scout: [] }>()

const FACTIONS: { id: Faction; label: string }[] = [
  { id: 'people', label: 'People' },
  { id: 'state', label: 'State' },
  { id: 'underworld', label: 'Underworld' },
]

/**
 * Signed and to one decimal. Standing moves in fractions and can go negative, and a bare rounded
 * integer would show a faction turning against you as a flat zero.
 */
const reading = (faction: Faction): string => {
  const value = props.reputation?.[faction] ?? 0
  return `${value > 0 ? '+' : ''}${value.toFixed(1)}`
}
</script>

<template>
  <section class="flex flex-col gap-3 rounded-lg border border-ink-muted/20 bg-surface-raised p-4">
    <div class="flex items-baseline justify-between gap-3">
      <h2 class="flex items-center gap-2 text-sm font-semibold uppercase tracking-wide text-accent">
        <AppIcon name="standing" :size="16" />
        Standing
      </h2>
      <button
        type="button"
        class="rounded-md border border-accent/50 px-2.5 py-1 text-xs font-semibold text-accent hover:brightness-110 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:cursor-not-allowed disabled:opacity-40"
        :disabled="disabled || scouting"
        @click="$emit('scout')"
      >
        <span class="flex items-center gap-1.5">
          <TurnSpinner v-if="scouting" :size="12" />
          {{ scouting ? 'Scouting…' : 'Send scouts' }}
        </span>
      </button>
    </div>

    <!--
      Named as well as drawn, and the figure is text rather than a bar: three crests alone would
      put the whole reading on colour and shape, which is exactly what the ad scale avoids.
    -->
    <dl v-if="reputation" class="grid grid-cols-3 gap-2">
      <div v-for="faction in FACTIONS" :key="faction.id" class="flex flex-col items-center gap-1">
        <img
          :src="crestArt(faction.id)"
          alt=""
          aria-hidden="true"
          width="36"
          height="43"
          loading="lazy"
          decoding="async"
          class="h-11 w-auto"
        />
        <dt class="text-xs text-ink-muted">{{ faction.label }}</dt>
        <dd class="text-sm font-semibold tabular-nums">{{ reading(faction.id) }}</dd>
      </div>
    </dl>

    <p v-else class="text-xs text-ink-muted">
      Nobody has scouted this dragon's reputation yet. Sending scouts costs a turn and ages every
      ad by one — but it is the only move that cannot cost a life.
    </p>
  </section>
</template>
