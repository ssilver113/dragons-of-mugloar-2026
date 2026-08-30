<script setup lang="ts">
import { computed } from 'vue'
import TurnSpinner from './TurnSpinner.vue'
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
 * A verdict, because the row asks a question. It is a word before it is a colour, so the ranking
 * survives anyone who cannot separate red from green; the gold behind it says by how much.
 */
const BANDS: Record<ValueBand, { text: string; class: string }> = {
  strong: { text: 'Yes', class: 'text-success' },
  fair: { text: 'Ok', class: 'text-ink' },
  poor: { text: 'No', class: 'text-danger' },
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

/**
 * A card is not itself a click target — the Solve button is — so the hover cue is a border that
 * warms rather than a surface that lifts. It says the row is live without promising that landing
 * anywhere on it does something. A job that cannot be sent gets none of it.
 */
const hoverable = computed(() => !unsendable.value && !props.disabled)
</script>

<template>
  <li
    class="ad-sheet parchment torn flex flex-col gap-3 p-4"
    :class="[{ 'opacity-60': unsendable }, hoverable ? 'hover:paper-lifted' : '']"
  >
    <div class="flex flex-col gap-2">
      <p class="text-sm sm:text-base">{{ ad.message }}</p>
      <ul v-if="badges.length || ad.encrypted" class="flex flex-wrap gap-1.5">
        <li
          v-if="ad.encrypted"
          class="rounded border border-accent/50 px-1.5 py-0.5 text-xs text-accent"
        >
          Decoded
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
    <div v-if="read && band" class="rounded-md border border-accent/40 bg-surface/70 p-3">
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
          <dd class="flex items-baseline gap-1.5" :class="band.class">
            <span class="font-semibold">{{ band.text }}</span>
            <span class="text-xs tabular-nums">{{ value }}</span>
          </dd>
        </div>
      </dl>
      <p v-if="read.trap" class="mt-2">
        <span
          class="rounded border border-danger/60 px-1.5 py-0.5 text-xs font-semibold text-danger"
          >Trap</span
        >
      </p>
      <ul
        v-if="warnings.length || read.trap"
        class="flex flex-col gap-1"
        :class="read.trap ? 'mt-1.5' : 'mt-2'"
      >
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
        class="relief rounded-md bg-accent px-3 py-1.5 text-sm font-semibold text-surface hover:brightness-110 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:cursor-not-allowed disabled:opacity-40 disabled:shadow-none"
        :disabled="disabled || unsendable"
        :aria-label="solving ? `Solving: ${ad.message}` : `Solve: ${ad.message}`"
        @click="$emit('solve', ad.adId)"
      >
        <span class="flex items-center gap-1.5">
          <TurnSpinner v-if="solving" />
          {{ solving ? 'Solving…' : 'Solve' }}
        </span>
      </button>
    </div>
  </li>
</template>

<style scoped>
/**
 * Pinned rather than stacked. The tilt and the tack are one idea: paper hangs from a single point,
 * so it hangs slightly crooked, and the crookedness is what stops ten sheets reading as ten boxes.
 *
 * It is the `rotate` property and deliberately not a `transform`. `AdList` re-ranks the board by
 * FLIP, which writes `transform` on these same elements for the length of the glide — a tilt
 * declared there would be overwritten for the move and snap back after it. The individual
 * transform properties compose with `transform` instead of replacing it, so the sheet stays
 * crooked while it travels.
 *
 * Cycled on a five against the tear's three, so the two patterns only line up every fifteenth
 * card and a column never repeats itself.
 */
.ad-sheet {
  rotate: -0.6deg;
}

.ad-sheet:nth-child(5n + 2) {
  rotate: 0.65deg;
}

.ad-sheet:nth-child(5n + 3) {
  rotate: -0.3deg;
}

.ad-sheet:nth-child(5n + 4) {
  rotate: 0.45deg;
}

.ad-sheet:nth-child(5n) {
  rotate: -0.75deg;
}

/**
 * The tack. Inside the sheet rather than at its edge: the torn edge is a displacement of up to
 * five or six pixels either way, so a tack sitting on the margin would spend some of the time off
 * the paper it is supposed to be holding. Pushed through it, which is where a real one goes.
 *
 * `::after` is free — parchment draws the sheet on `::before`.
 *
 * Hung off a class rather than off `li`: the badge pills and the advisor's warnings are lists too,
 * and a bare element selector tacked and tilted every one of them.
 */
.ad-sheet::after {
  content: '';
  position: absolute;
  top: 4px;
  left: 50%;
  translate: -50%;
  width: 11px;
  height: 11px;
  border-radius: 9999px;
  background-image: radial-gradient(
    circle at 34% 30%,
    oklch(74% 0.015 70),
    oklch(40% 0.02 55) 55%,
    oklch(26% 0.015 50)
  );
  box-shadow: 0 1px 2px oklch(0% 0 0 / 0.45);
}
</style>
