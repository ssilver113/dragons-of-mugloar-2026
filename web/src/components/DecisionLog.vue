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

    <p v-if="!entries.length" class="parchment torn p-6 text-center text-ink-muted">
      No automatic turns taken yet. Open Auto-Play and press Run to hand the game over, or Step to
      watch one turn at a time.
    </p>

    <ul v-else class="flex flex-col gap-2">
      <DecisionEntry v-for="entry in shown" :key="entry.id" :entry="entry" />
    </ul>

    <!--
      At the foot rather than beside the heading: the heading row has to stay one line wide on a
      375px screen, and this is where you arrive having read what is drawn.
    -->
    <div v-if="hidden > 0 || expanded" class="flex justify-center">
      <button
        type="button"
        class="relief rounded-md border border-ink-muted/40 bg-surface-raised/60 px-3 py-1.5 text-sm text-ink-muted hover:border-ink hover:text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
        @click="expanded = !expanded"
      >
        {{ expanded ? `Show the newest ${VISIBLE}` : `Show all ${entries.length} turns` }}
      </button>
    </div>
  </section>
</template>
