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

/**
 * The tone is an attribute rather than a class, and the stylesheet reads it. There is exactly one
 * declaration it can change — the colour painted down the plate's leading edge — so a class per
 * tone would name three things that differ in one value.
 */
const tone = computed(() => (state.value.kind === 'outcome' ? props.outcome!.tone : 'none'))

/**
 * Only a finished turn is announced. The waiting states are already spoken by the control that
 * started them — the Solve button renames itself while a job is in flight — and reading out a
 * placeholder that says nothing happened yet would be three announcements per turn instead of one.
 */
const live = computed(() => (state.value.kind === 'outcome' ? 'polite' : 'off'))

/**
 * The sigil answers the outcome: the dragon stands up to a job it survived and hunches over one it
 * did not. Only a tone the game actually judged moves it — a purchase or a scouting report is
 * `info`, and neither went well nor badly.
 */
const MOODS = { success: 'victorious', failure: 'defeated', info: 'idle' } as const

const mood = computed(() => (state.value.kind === 'outcome' ? MOODS[props.outcome!.tone] : 'idle'))
</script>

<template>
  <!--
    An alcove in the same timber the board and the shopfront are built from: a recess cut for the
    dragon, and a milled plate beside it carrying the words. Nothing is read against the wood —
    every surface here that holds text is oak, which is the one wooden face the palette is bounded
    against.
  -->
  <div
    class="timber flex h-24 items-center gap-2 p-2 sm:gap-2.5"
    :data-tone="tone"
    role="status"
    :aria-live="live"
    aria-atomic="true"
  >
    <span class="niche">
      <!--
        The dragon does the waiting. It is the app's one recurring character, it is already the
        thing the header and the end panel use to say how the run is going, and a second loader
        vocabulary for the same wait would be one more thing to keep in step.

        The breath is on the wrapper rather than on the sigil, which runs an entrance of its own on
        every change of mood. Two animations on one element is one of them losing.
      -->
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
 * The recess the dragon stands in, cut into the wood: dark along the top and light along the
 * bottom lip, which is the inverse of `relief` and so reads as a hollow rather than as a boss.
 * The same construction as the Standing wall's empty mounts, arched rather than shield-cut,
 * because this one is a doorway and those are fittings.
 *
 * Much darker than those mounts, and the reason is that something stands in this one. An empty
 * recess only has to read as cut. The three poses share a colour and a median luminance around
 * 114 in sRGB, which is very close to what the mounts are drawn at, so at their lightness the wall
 * and the dragon were the same value and the drawing went flat. The recess has to clear that
 * range, and it can only clear it downwards without ceasing to be a recess.
 *
 * The floor is lit rather than the whole hollow, so the figure is stood on something instead of
 * being silhouetted against an even void.
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
 * The floor of the alcove, and the reason it is not a square.
 *
 * Each pose is cropped to its own outline and the three do not agree on a shape: the standing one
 * is taller than it is wide, the defeated one is wider than it is tall. A square image box inside
 * a doorway that is not square leaves a margin all round and costs every one of them about a
 * quarter of its size. A box shaped like the opening lets each pose bind on whichever edge suits
 * it, the standing one on height and the defeated one on width.
 *
 * Bottom-aligned rather than centred, so every pose stands on the floor instead of hovering in
 * the middle of the doorway.
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
 * The lamp, on the figure rather than on the wall behind it. It is what keeps the darkest part of
 * a pose — the defeated one is mostly wing, and a wing is the deepest green in the set — from
 * merging with the back of the hollow.
 *
 * A `drop-shadow` follows the painted pixels rather than the box, so this is a rim on the animal
 * and not a square behind it. The same reason the shop's studs are drawn this way: a shadow cast
 * by the border box paints edges that were never there.
 */
.niche img {
  filter: drop-shadow(0 0 3px oklch(88% 0.055 82 / 0.55));
}

/* Narrow, the plate is what the sentence needs and the recess is what is left. Below this the
   dragon costs more width than it earns, so the arch closes to a keyhole rather than the plate
   losing a word. */
@media (width < 25rem) {
  .niche {
    width: 3.6rem;
  }
}

/* The same plate the Standing wall wears, which is what this surface is: planed flat and fixed, not
   one of the shop's milled plaques hung on ropes. Stock, cut and rim come from the utility; this
   rule sets only how a plate carrying two lines of a sentence is padded and laid out. The clip is
   here so the painted edge below stops where the plate does. */
.plate {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  overflow: hidden;
  padding: 0.4rem 0.7rem 0.4rem 1rem;
}

/**
 * The verdict, painted down the plate's leading edge.
 *
 * It is the only thing in the box that the tone changes, and it is never the only thing that
 * carries the reading: the title says which way the turn went and the dragon's own posture agrees
 * with it. A turn the game did not judge — a purchase, a scouting report, anything still in flight
 * — leaves the edge the plate's own rim colour, so an unpainted plate reads as no verdict rather
 * than as a neutral one.
 *
 * Inset by a pixel so the rim the `oak-plate` face draws survives underneath it. A stripe flush to
 * the edge would paint over the one line that says where the plate stops.
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
