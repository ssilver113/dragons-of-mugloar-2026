<script setup lang="ts">
import { computed } from 'vue'
import AppIcon from './AppIcon.vue'
import DragonSigil from './DragonSigil.vue'

export type ResultTone = 'success' | 'failure' | 'info'
export type PendingKind = 'solve' | 'purchase' | 'investigation'

/**
 * Where a turn's outcome is reported. One size in all four states and never absent — it used to
 * unmount between turns, which moved the board twice a turn. The height is fixed rather than
 * floored because an upstream failure message is not length-bounded; the clamped text stays whole
 * in the DOM for a screen reader.
 */
const props = defineProps<{
  pending: PendingKind | null
  solverRunning: boolean
  outcome: { tone: ResultTone; title: string; body: string } | null
}>()

const PENDING: Record<PendingKind, { title: string; body: string }> = {
  solve: {
    title: 'Taking the job…',
    body: 'The dragon is out. The board will tell us how it went.',
  },
  purchase: {
    title: 'At the counter…',
    body: 'Paying up. A purchase costs a turn whether or not the shop agrees to it.',
  },
  investigation: {
    title: 'The scouts are out…',
    body: 'A turn spent and nothing risked — the only move that cannot cost a life.',
  },
}

const IDLE = {
  title: 'No job taken yet',
  body: 'Solve a job, buy an item or send scouts. How it went appears here.',
}

const SOLVING = {
  title: 'The solver has the game',
  body: 'It is taking every turn for you. The decision log carries the reasoning, turn by turn.',
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

/** An attribute, not a class: the tone changes exactly one declaration. */
const tone = computed(() => (state.value.kind === 'outcome' ? props.outcome!.tone : 'none'))

/** Only a finished turn is announced; the waiting states are already spoken by their control. */
const live = computed(() => (state.value.kind === 'outcome' ? 'polite' : 'off'))

/** Only a tone the game actually judged moves the sigil; a purchase or a report is `info`. */
const MOODS = { success: 'victorious', failure: 'defeated', info: 'idle' } as const

const mood = computed(() => (state.value.kind === 'outcome' ? MOODS[props.outcome!.tone] : 'idle'))
</script>

<template>
  <!-- An alcove in the board's timber. Nothing is read against the wood: every surface here that
       holds text is oak, the one wooden face the palette is bounded against. -->
  <div
    class="timber flex h-24 items-center gap-2 p-2 sm:gap-2.5"
    :data-tone="tone"
    role="status"
    :aria-live="live"
    aria-atomic="true"
  >
    <span class="niche">
      <!-- The breath is on the wrapper, not the sigil, which runs its own entrance on every
           change of mood. Two animations on one element is one of them losing. -->
      <span
        v-if="state.kind !== 'solver'"
        class="stage flex"
        :class="{ waiting: state.kind === 'pending' }"
      >
        <DragonSigil :mood="mood" :size="72"
      /></span>
      <AppIcon v-else name="autoplay" :size="48" class="size-10 sm:size-11" />
    </span>

    <div class="oak-plate plate min-w-0 flex-1 self-stretch">
      <p
        class="truncate font-display text-[0.9375rem] font-bold sm:text-base"
        :class="{ 'text-ink-muted': state.kind === 'idle' }"
      >
        {{ state.title }}
      </p>
      <p class="mt-0.5 line-clamp-2 text-sm text-ink-muted">{{ state.body }}</p>
    </div>
  </div>
</template>

<style scoped>
/**
 * The recess the dragon stands in: the inverse of `relief`, so it reads as a hollow. Much darker
 * than the Standing wall's empty mounts, because the three poses sit near 114 median luminance in
 * sRGB and went flat against them. Only the floor is lit, so the figure stands on something.
 */
.niche {
  position: relative;
  display: grid;
  height: 100%;
  width: 4.5rem;
  flex-shrink: 0;
  place-items: center;
  border-radius: 2.25rem 2.25rem 0.2rem 0.2rem;
  background-color: oklch(26% 0.018 58);
  background-image: radial-gradient(88% 58% at 50% 94%, oklch(62% 0.05 72 / 0.72), transparent 76%);
  box-shadow:
    inset 0 5px 9px oklch(10% 0.015 50 / 0.75),
    inset 0 -2px 0 oklch(100% 0 0 / 0.18);
}

/**
 * Shaped like the opening rather than square: the poses are cropped to their own outlines and do
 * not agree on a shape, so a square box would cost each about a quarter of its size. Bottom-aligned
 * so every pose stands on the floor.
 */
.stage {
  height: 100%;
  width: 100%;
  padding: 0.3rem 0.35rem 0.4rem;
}

.stage img {
  height: 100%;
  width: 100%;
  object-position: 50% 100%;
}

/**
 * A rim on the figure, keeping the darkest pose off the back of the hollow. `drop-shadow` follows
 * the painted pixels; a box shadow would paint a square that was never there.
 */
.niche img {
  filter: drop-shadow(0 0 3px oklch(88% 0.055 82 / 0.55));
}

/* Below this the dragon costs more width than it earns, so the arch narrows rather than the
   plate losing a word. */
@media (width < 25rem) {
  .niche {
    width: 3.6rem;
  }
}

/* Stock, cut and rim come from `oak-plate`; this sets only the padding for two lines of prose.
   The clip is what stops the painted edge below where the plate does. */
.plate {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  overflow: hidden;
  padding: 0.4rem 0.7rem 0.4rem 1rem;
}

/**
 * The verdict, painted down the leading edge. Never the only thing carrying it — the title and the
 * dragon's posture say the same. An unjudged turn leaves the rim colour, which reads as no verdict
 * rather than a neutral one. Inset by a pixel so the plate's own rim survives underneath.
 */
.plate::before {
  content: '';
  position: absolute;
  inset: 1px auto 1px 1px;
  width: 5px;
  background-color: var(--tone, oklch(52% 0.05 66));
}

[data-tone='success'] .plate {
  --tone: var(--color-success);
}

[data-tone='failure'] .plate {
  --tone: var(--color-danger);
}

@media (prefers-reduced-motion: no-preference) {
  /* A breath rather than a spin: a character that whirls while it waits reads as an error. */
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
