<script setup lang="ts">
import { computed } from 'vue'
import AppIcon from './AppIcon.vue'
import DragonSigil from './DragonSigil.vue'

export type ResultTone = 'success' | 'failure' | 'info'
export type PendingKind = 'solve' | 'purchase' | 'investigation'

/**
 * Where a turn's outcome is reported — and, just as importantly, where it is reported *from*.
 *
 * The box is the same size in all four of its states and is never absent, because it used to be:
 * the store clears the last outcome the moment a new turn starts, so the banner unmounted for the
 * length of every action and remounted after it, and the board below it moved twice a turn. A
 * result that is worth reading is worth a place that stays still.
 *
 * The height is fixed rather than floored. A failure message comes from upstream and is not
 * length-bounded, so the title takes one line and the body takes two; the full text stays in the
 * DOM for a screen reader, which does not clamp.
 */
const props = defineProps<{
  pending: PendingKind | null
  solverRunning: boolean
  outcome: { tone: ResultTone; title: string; body: string } | null
}>()

const PENDING: Record<PendingKind, { title: string; body: string }> = {
  solve: { title: 'Taking the job…', body: 'The dragon is out. The board will tell us how it went.' },
  purchase: { title: 'At the counter…', body: 'Paying up. A purchase costs a turn whether or not the shop agrees to it.' },
  investigation: { title: 'The scouts are out…', body: 'A turn spent and nothing risked — the only move that cannot cost a life.' },
}

const IDLE = {
  title: 'No job taken yet',
  body: 'Solve a job, buy an item or send scouts. How it went appears here.',
}

const SOLVING = {
  title: 'The solver has the game',
  body: 'It is taking every turn for you. The decision log carries the reasoning, turn by turn.',
}

const TONES: Record<ResultTone, string> = {
  success: 'border-success/50 bg-success/10',
  failure: 'border-danger/50 bg-danger/10',
  info: 'border-ink-muted/40 bg-surface-raised',
}

const state = computed(() => {
  if (props.pending !== null) {
    return { kind: 'pending' as const, ...PENDING[props.pending] }
  }
  if (props.solverRunning) {
    return { kind: 'solver' as const, ...SOLVING }
  }
  if (props.outcome !== null) {
    return { kind: 'outcome' as const, ...props.outcome }
  }
  return { kind: 'idle' as const, ...IDLE }
})

const tone = computed(() =>
  state.value.kind === 'outcome' ? TONES[props.outcome!.tone] : 'border-ink-muted/25 bg-surface-raised/50',
)

/**
 * Only a finished turn is announced. The waiting states are already spoken by the control that
 * started them — the Solve button renames itself while a job is in flight — and reading out a
 * placeholder that says nothing happened yet would be three announcements per turn instead of one.
 */
const live = computed(() => (state.value.kind === 'outcome' ? 'polite' : 'off'))

/**
 * A job the dragon failed is not a dragon that fell. The defeated sigil belongs to the end of a
 * run and nowhere else — the red border already says the job went badly, and stamping the seal on
 * a lost life would tell the player their game was over when it is not.
 */
const mood = computed(() =>
  state.value.kind === 'outcome' && props.outcome!.tone === 'success' ? 'victorious' : 'idle',
)
</script>

<template>
  <div
    class="flex h-24 items-center gap-3 rounded-lg border px-4 py-3"
    :class="tone"
    role="status"
    :aria-live="live"
    aria-atomic="true"
  >
    <!--
      The dragon does the waiting. It is the app's one recurring character, it is already the thing
      the header and the end panel use to say how the run is going, and a second loader vocabulary
      for the same wait would be one more thing to keep in step.
    -->
    <span
      v-if="state.kind !== 'solver'"
      class="flex shrink-0"
      :class="{ waiting: state.kind === 'pending' }"
    >
      <DragonSigil :mood="mood" :size="56" class="size-12 sm:size-14" />
    </span>
    <AppIcon v-else name="autoplay" :size="48" class="size-11 sm:size-12" />

    <div class="min-w-0 flex-1">
      <p class="truncate font-semibold" :class="{ 'text-ink-muted': state.kind === 'idle' }">
        {{ state.title }}
      </p>
      <p class="mt-0.5 line-clamp-2 text-sm text-ink-muted">{{ state.body }}</p>
    </div>
  </div>
</template>

<style scoped>
@media (prefers-reduced-motion: no-preference) {
  /* A breath rather than a spin: the sigil is a character, and a character that whirls while it
     waits reads as an error state. */
  .waiting {
    animation: waiting 1.4s ease-in-out infinite;
  }
}

@keyframes waiting {
  50% {
    opacity: 0.55;
    transform: scale(0.94);
  }
}
</style>
