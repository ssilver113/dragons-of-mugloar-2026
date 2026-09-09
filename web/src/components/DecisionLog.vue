<script setup lang="ts">
import { computed, ref } from 'vue'
import AppIcon from './AppIcon.vue'
import DecisionEntry from './DecisionEntry.vue'
import MessageBanner from './MessageBanner.vue'
import { present } from '../api/errorPresentation'
import type { Halt, LogEntry } from '../stores/autoplay'

const props = defineProps<{ entries: LogEntry[]; halt: Halt | null }>()
defineEmits<{ 'keep-going': []; retry: [] }>()

/**
 * How many turns are drawn before the log asks whether you want the rest.
 *
 * The cap is on the rendering, never on the record: a run at max speed is fifty turns and every
 * entry carries two tables of the solver's reasoning, which is what makes the page unmanageable —
 * but that reasoning is the whole point of keeping a log, and throwing the older half away to
 * shorten a page would be discarding the evidence to tidy the exhibit.
 */
const VISIBLE = 10
const expanded = ref(false)

/**
 * Whether pressing Run again could do anything. A lost session or a finished game cannot be
 * resumed, and a button that silently does nothing is worse than no button.
 */
const resumable = computed(
  () => props.halt?.kind === 'error' && present(props.halt.error.code).severity !== 'terminal',
)

// Newest first. A run at max speed outpaces reading, and chasing the bottom of a growing list is
// worse than losing the chronology.
const newestFirst = computed(() => [...props.entries].reverse())

const shown = computed(() =>
  expanded.value ? newestFirst.value : newestFirst.value.slice(0, VISIBLE),
)
const hidden = computed(() => props.entries.length - shown.value.length)
</script>

<template>
  <section aria-labelledby="log-heading" class="flex flex-col gap-3">
    <div class="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
      <h2
        id="log-heading"
        class="flex items-center gap-1.5 text-base font-semibold sm:gap-2 sm:text-lg"
      >
        <AppIcon name="log" :size="20" class="size-4 sm:size-5" />
        Decision log
      </h2>
      <p v-if="entries.length" class="text-xs text-ink-muted">
        {{ entries.length }} turn{{ entries.length === 1 ? '' : 's' }}, newest first
      </p>
    </div>

    <MessageBanner
      v-if="halt?.kind === 'stalled'"
      tone="info"
      title="The solver has stopped to check in"
    >
      It passed {{ halt.passes }} turns in a row — nothing on the board was worth a life and nothing
      in the shop was affordable. Passing is safe, so the game will not end on its own.
      <button
        type="button"
        class="ml-1 rounded font-semibold text-accent underline hover:brightness-110 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
        @click="$emit('keep-going')"
      >
        Keep going anyway
      </button>
    </MessageBanner>

    <MessageBanner v-else-if="halt?.kind === 'error'" tone="error" title="The run stopped">
      {{ halt.error.message }}
      <button
        v-if="resumable"
        type="button"
        class="ml-1 rounded font-semibold text-accent underline hover:brightness-110 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
        @click="$emit('retry')"
      >
        Try again
      </button>
    </MessageBanner>

    <!--
      The binding says nothing and is out of the tree for the same reason the shop's rail is: to a
      screen reader this section is a heading, a list of turns and a button.
    -->
    <div class="volume ledger">
      <div class="spine" aria-hidden="true">
        <span class="band band-head" /><span class="band band-tail" />
      </div>

      <p v-if="!entries.length" class="p-6 text-center text-ink-muted">
        No automatic turns taken yet. Open Auto-Play and press Run to hand the game over, or Step to
        watch one turn at a time.
      </p>

      <template v-else>
        <!-- The units live on the entries, said quietly, so the heads carry no meaning of their
             own and can go when the columns do. -->
        <div class="ledger-row heads" aria-hidden="true">
          <span class="text-right">Turn</span>
          <span>Entry</span>
          <span class="text-right">Score</span>
          <span class="text-right">Gold</span>
          <span class="text-right">Lives</span>
          <span class="text-right">Lvl</span>
        </div>

        <ul>
          <DecisionEntry v-for="entry in shown" :key="entry.id" :entry="entry" />
        </ul>

        <!--
          At the foot rather than beside the heading: the heading row has to stay one line wide on a
          375px screen, and this is where you arrive having read what is drawn.
        -->
        <div v-if="hidden > 0 || expanded" class="flex justify-center p-3">
          <button
            type="button"
            class="relief rounded-md border border-ink-muted/40 bg-surface-raised px-3 py-1.5 text-sm text-ink-muted hover:border-ink hover:text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
            @click="expanded = !expanded"
          >
            {{ expanded ? `Show the newest ${VISIBLE}` : `Show all ${entries.length} turns` }}
          </button>
        </div>
      </template>
    </div>
  </section>
</template>

<style scoped>
/**
 * The volume. The page is the `ledger` utility; this rule is only the binding it is sewn into.
 *
 * The spine is absolute and the page is inset past it, rather than the two being columns of a grid:
 * an entry that grows has to take the binding with it, and a raised band has to be able to sit at a
 * fixed distance from an edge that moves.
 */
.volume {
  position: relative;
  padding-left: 2.4rem;
}

.spine {
  position: absolute;
  inset: 0 auto 0 0;
  width: 2.4rem;
  border-radius: 3px 0 0 3px;
  background-color: oklch(38% 0.045 40);
  background-image:
    repeating-linear-gradient(180deg, oklch(100% 0 0 / 0.05) 0 2px, transparent 2px 7px),
    radial-gradient(80% 40% at 30% 20%, oklch(48% 0.05 40 / 0.6), transparent 70%),
    var(--parchment-grain);
  background-size:
    auto,
    auto,
    70px 70px;
  background-blend-mode: normal, normal, multiply;
  /* Leather is the only thing here that never carries text, so it is outside the measured palette
     exactly as the timber is. */
  box-shadow:
    inset -6px 0 8px oklch(20% 0.03 40 / 0.45),
    inset 1px 0 0 oklch(100% 0 0 / 0.12);
}

/**
 * A raised band is a cord under the leather, so it is proud of the spine rather than printed on it,
 * and the gilt lines are ruled either side of the swell instead of on top of it.
 */
.band {
  position: absolute;
  left: 0;
  width: 100%;
  height: 12px;
  background: oklch(45% 0.05 40);
  box-shadow:
    inset 0 1px 0 oklch(100% 0 0 / 0.22),
    0 1px 2px oklch(18% 0.02 40 / 0.5);
}

.band-head {
  top: 2.2rem;
}

.band-tail {
  bottom: 2.2rem;
}

.band::before,
.band::after {
  content: '';
  position: absolute;
  left: 0.45rem;
  width: 1.5rem;
  height: 1px;
  background: oklch(80% 0.09 88 / 0.75);
}

.band::before {
  top: -3px;
}

.band::after {
  bottom: -3px;
}

/* Small caps under a double rule, which is the one place in the app where a border is drawn twice
   on purpose. Gone once the figures leave their columns, where a head would name a column that is
   no longer under it. */
.heads {
  --ledger-pad: 0.3rem 0.7rem;

  display: none;
  border-top: 1px solid color-mix(in oklab, var(--color-ink-muted) 35%, transparent);
  border-bottom: 3px double color-mix(in oklab, var(--color-ink-muted) 35%, transparent);
  font-size: 0.64rem;
  letter-spacing: 0.09em;
  text-transform: uppercase;
  color: var(--color-ink-muted);
}

@media (width >= 48rem) {
  .heads {
    display: grid;
  }
}
</style>
