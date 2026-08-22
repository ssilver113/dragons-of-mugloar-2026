<script setup lang="ts">
import { computed } from 'vue'
import type { AdFlag, AdView } from '../api/types'
import type { AdRead, ValueBand } from '../advisor/ranking'

/** `read` is null when the advisor is off, so there is no half-advised state to render. */
const props = defineProps<{
  ad: AdView
  read: AdRead | null
  solving: boolean
  disabled: boolean
}>()
defineEmits<{ solve: [adId: string] }>()

/**
 * Flags split by who is making the claim. `EXPIRING_NEXT_TURN` restates the game's own
 * `expiresIn` and `UNREADABLE` explains why an ad is not offered, so both are facts about the
 * board. The other two are the model's judgement and belong with the rest of its advice.
 */
const BOARD_FLAGS: Partial<Record<AdFlag, { text: string; class: string }>> = {
  EXPIRING_NEXT_TURN: { text: 'Last turn', class: 'border-warning/50 text-warning' },
  UNREADABLE: { text: 'Unreadable', class: 'border-ink-muted/40 text-ink-muted' },
}

const ADVISOR_FLAGS: Partial<Record<AdFlag, string>> = {
  OUT_OF_LEAGUE: 'The reward is richer than your level handles safely.',
  NEVER_ATTEMPT: 'Never worth a turn, whatever it pays.',
}

/**
 * The band is named as well as coloured, and the pips carry it a third way. Colour alone would
 * make the whole ranking invisible to anyone who cannot separate red from green.
 */
const BANDS: Record<ValueBand, { text: string; pips: string; class: string }> = {
  strong: { text: 'Strong', pips: '●●●', class: 'text-success' },
  fair: { text: 'Fair', pips: '●●○', class: 'text-ink' },
  poor: { text: 'Not worth a life', pips: '●○○', class: 'text-danger' },
}

const badges = computed(() =>
  props.ad.flags.flatMap((flag) => {
    const badge = BOARD_FLAGS[flag]
    return badge ? [badge] : []
  }),
)

const warnings = computed(() => props.ad.flags.flatMap((flag) => ADVISOR_FLAGS[flag] ?? []))

// Deliberately imprecise. The estimate is a fit over a handful of measurements per level band,
// and a bare "85%" would claim more than we know.
const chance = computed(() => `~${Math.round(props.ad.successProbability * 20) * 5}%`)
const payout = computed(() => `~${Math.round(props.ad.expectedValue)}g`)
const turns = computed(() => `${props.ad.expiresIn} ${props.ad.expiresIn === 1 ? 'turn' : 'turns'}`)

const band = computed(() => (props.read ? BANDS[props.read.band] : null))
// Signed on purpose: a minus here is the whole point, and "-12g" reads as a cost at a glance.
const value = computed(() => {
  // Adding zero because a score just under zero rounds to negative zero, which prints as "-0g".
  const score = Math.round(props.read?.score ?? 0) + 0
  return `${score > 0 ? '+' : ''}${score}g`
})

// An ad we could not decode carries an ad id the game service would reject, so it is the one
// thing the player is not offered. Bad odds are still the player's call to make.
const unsendable = computed(() => props.ad.flags.includes('UNREADABLE'))
</script>

<template>
  <li
    class="flex flex-col gap-3 rounded-lg border bg-surface-raised p-4"
    :class="[read?.trap ? 'border-danger/50' : 'border-ink-muted/20', { 'opacity-60': unsendable }]"
  >
    <div class="flex flex-col gap-2">
      <p class="text-sm sm:text-base">{{ ad.message }}</p>
      <ul v-if="badges.length || ad.encrypted || read?.trap" class="flex flex-wrap gap-1.5">
        <li
          v-if="ad.encrypted"
          class="rounded border border-accent/50 px-1.5 py-0.5 text-xs text-accent"
        >
          Decoded
        </li>
        <li
          v-if="read?.trap"
          class="rounded border border-danger/60 px-1.5 py-0.5 text-xs font-semibold text-danger"
        >
          Trap
        </li>
        <li
          v-for="badge in badges"
          :key="badge.text"
          class="rounded border px-1.5 py-0.5 text-xs"
          :class="badge.class"
        >
          {{ badge.text }}
        </li>
      </ul>
    </div>

    <dl class="grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
      <div>
        <dt class="text-xs text-ink-muted">Reward</dt>
        <dd class="tabular-nums">{{ ad.reward }}g</dd>
      </div>
      <div>
        <dt class="text-xs text-ink-muted">Odds</dt>
        <dd>{{ ad.probability }}</dd>
      </div>
    </dl>

    <!-- Everything below is our reading of the board, not the game's. It says so. -->
    <div v-if="read && band" class="rounded-md border border-accent/30 bg-surface/60 p-3">
      <p class="text-xs font-semibold uppercase tracking-wide text-accent">Advisor's read</p>
      <dl class="mt-1.5 grid grid-cols-2 gap-x-4 gap-y-1 text-sm sm:grid-cols-3">
        <div>
          <dt class="text-xs text-ink-muted">Chance</dt>
          <dd class="tabular-nums">{{ chance }}</dd>
        </div>
        <div>
          <dt class="text-xs text-ink-muted">Payout on average</dt>
          <dd class="tabular-nums">{{ payout }}</dd>
        </div>
        <div>
          <dt class="text-xs text-ink-muted">Worth the risk</dt>
          <dd class="flex items-baseline gap-1.5 tabular-nums" :class="band.class">
            <span aria-hidden="true" class="text-[0.6rem] tracking-tight">{{ band.pips }}</span>
            <span>{{ value }}</span>
            <span class="text-xs">{{ band.text }}</span>
          </dd>
        </div>
      </dl>
      <ul v-if="warnings.length || read.trap" class="mt-2 flex flex-col gap-1">
        <li v-if="read.trap" class="text-xs text-danger">
          Pays well and the odds do not back it. This is the job that ends a run.
        </li>
        <li v-for="warning in warnings" :key="warning" class="text-xs text-warning">
          {{ warning }}
        </li>
      </ul>
    </div>

    <div class="flex items-center justify-between gap-3">
      <p class="text-xs text-ink-muted">Expires in {{ turns }}</p>
      <!--
        The accessible name tracks the visible label. One that still read "Solve" while the button
        says "Solving…" would hide the only state change there is to hear.
      -->
      <button
        type="button"
        class="rounded-md bg-accent px-3 py-1.5 text-sm font-semibold text-surface hover:brightness-110 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:cursor-not-allowed disabled:opacity-40"
        :disabled="disabled || unsendable"
        :aria-label="solving ? `Solving: ${ad.message}` : `Solve: ${ad.message}`"
        @click="$emit('solve', ad.adId)"
      >
        {{ solving ? 'Solving…' : 'Solve' }}
      </button>
    </div>
  </li>
</template>
