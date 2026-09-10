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

/** Split by who is making the claim: these two are facts about the board, the others are ours. */
const BOARD_FLAGS: Partial<Record<AdFlag, { text: string; class: string }>> = {
  EXPIRING_NEXT_TURN: { text: 'Last turn', class: 'border-warning/50 text-warning' },
  UNREADABLE: { text: 'Unreadable', class: 'border-ink-muted/40 text-ink-muted' },
}

const ADVISOR_FLAGS: Partial<Record<AdFlag, string>> = {
  OUT_OF_LEAGUE: 'The reward is richer than your level handles safely.',
  NEVER_ATTEMPT: 'Never worth a turn, whatever it pays.',
}

/** A word before it is a colour, so the ranking survives anyone who cannot separate red from green. */
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

// Deliberately imprecise: the estimate is a fit, and a bare "85%" would claim more than we know.
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

/** The card is not a click target, so the cue warms a border rather than promising a click. */
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

    <!-- Our reading, not the game's: the advisor's own ink rather than the accent, so a player
         can see which marks come from which. Rows rather than columns because across, the labels
         wrapped and the figures never lined up between one card and the next. -->
    <div v-if="read && band" class="advisor-read">
      <!-- No mark beside it: the icon set bakes its own palette, so the eye would come out warm
           brown against a green label. -->
      <p class="text-xs font-semibold uppercase tracking-wide text-advisor">Advisor's read</p>
      <dl class="mt-1.5 text-sm">
        <div class="row">
          <dt class="text-xs text-ink-muted">Chance</dt>
          <dd class="tabular-nums">{{ chance }}</dd>
        </div>
        <div class="row">
          <dt class="text-xs text-ink-muted">Payout on average</dt>
          <dd class="tabular-nums">{{ payout }}</dd>
        </div>
        <div class="row">
          <dt class="text-xs text-ink-muted">Worth the risk</dt>
          <dd class="flex items-baseline justify-end gap-1.5" :class="band.class">
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
      <!-- The accessible name tracks the visible label, which is the only state change to hear. -->
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
 * The advisor's slip on the sheet. A wash rather than a fill, so the paper's blotches still show
 * through. The sheet itself is never tinted — the verdict is words, not the colour of the paper.
 */
.advisor-read {
  border: 1px solid color-mix(in oklab, var(--color-advisor) 42%, transparent);
  border-radius: 4px;
  background-image: linear-gradient(
    color-mix(in oklab, var(--color-advisor) 9%, transparent),
    color-mix(in oklab, var(--color-advisor) 4%, transparent)
  );
  padding: 0.625rem 0.6875rem;
}

/** Label left, figure right, ruled between at a fifth so it reads as a table and not a grid. */
.row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: baseline;
  gap: 0.75rem;
  padding: 0.1875rem 0;
}

.row + .row {
  border-top: 1px solid color-mix(in oklab, var(--color-advisor) 20%, transparent);
}

.row dd {
  text-align: right;
}

/**
 * Paper hangs from one tack, so it hangs crooked, which is what stops ten sheets reading as ten
 * boxes. `rotate` and not `transform`: the FLIP re-rank writes `transform` on these same elements,
 * and the individual properties compose with it rather than being replaced. Cycled on a five
 * against the tear's three, so the patterns line up only every fifteenth card.
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
 * The tack, inset rather than on the margin: the torn edge displaces five or six pixels either
 * way, so a tack at the edge would spend some of its time off the paper. `::after` because
 * `parchment` draws the sheet on `::before`, and a class because the pills are `li` too.
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
