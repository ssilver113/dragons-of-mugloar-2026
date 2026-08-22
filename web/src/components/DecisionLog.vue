<script setup lang="ts">
import { computed } from 'vue'
import DecisionEntry from './DecisionEntry.vue'
import MessageBanner from './MessageBanner.vue'
import { present } from '../api/errorPresentation'
import type { Halt, LogEntry } from '../stores/autoplay'

const props = defineProps<{ entries: LogEntry[]; halt: Halt | null }>()
defineEmits<{ 'keep-going': []; retry: [] }>()

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
</script>

<template>
  <section aria-labelledby="log-heading" class="flex flex-col gap-3">
    <div class="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
      <h2 id="log-heading" class="text-lg font-semibold">Decision log</h2>
      <p v-if="entries.length" class="text-xs text-ink-muted">
        {{ entries.length }} turn{{ entries.length === 1 ? '' : 's' }}, newest first
      </p>
    </div>

    <MessageBanner
      v-if="halt?.kind === 'stalled'"
      tone="info"
      title="The solver has stopped to check in"
    >
      It passed {{ halt.passes }} turns in a row — nothing on the board was worth a life and
      nothing in the shop was affordable. Passing is safe, so the game will not end on its own.
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

    <p
      v-if="!entries.length"
      class="rounded-lg border border-ink-muted/20 p-6 text-center text-ink-muted"
    >
      No turns taken yet. Press Run to hand the game over, or Step to watch one turn at a time.
    </p>

    <ul v-else class="flex flex-col gap-2">
      <DecisionEntry v-for="entry in newestFirst" :key="entry.id" :entry="entry" />
    </ul>
  </section>
</template>
