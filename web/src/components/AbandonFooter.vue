<script setup lang="ts">
import { nextTick, ref } from 'vue'
import type { GameView } from '../api/types'

defineProps<{
  game: GameView
  /** False mid-turn and under the solver, for the reason the copy below gives. */
  canAbandon: boolean
  starting: boolean
  solverActive: boolean
}>()
const emit = defineEmits<{ abandon: [] }>()

/**
 * Abandoning takes two clicks: the run it replaces is gone for good.
 *
 * The half-pressed state lives here rather than in the root component, which is also what retires
 * the watcher that used to reset it — the footer is only rendered while a game is playable, so an
 * ending unmounts the question along with the board and the next game cannot inherit it.
 */
const abandoning = ref(false)
const confirmAbandon = ref<HTMLButtonElement | null>(null)
const startNew = ref<HTMLButtonElement | null>(null)

async function askToAbandon(): Promise<void> {
  abandoning.value = true
  await nextTick()
  confirmAbandon.value?.focus()
}

async function keepPlaying(): Promise<void> {
  abandoning.value = false
  await nextTick()
  startNew.value?.focus()
}

function abandon(): void {
  abandoning.value = false
  emit('abandon')
}
</script>

<template>
  <!-- Last on the page and nowhere near the buttons that spend turns. On a sheet rather than the
       backdrop, which runs 0.16 to 0.55 in luminance and took muted ink to 1.5:1; even an 85%
       scrim only reaches 4.0:1. -->
  <footer class="panel mt-auto flex flex-col items-start gap-2 p-4" @keydown.esc="keepPlaying()">
    <template v-if="!abandoning">
      <button
        ref="startNew"
        type="button"
        class="btn btn-quiet rounded-md px-3 py-1.5 text-sm font-semibold text-ink-muted hover:text-ink disabled:opacity-40"
        :disabled="!canAbandon"
        @click="askToAbandon()"
      >
        Start a new game
      </button>
      <p class="text-sm text-ink-muted">
        {{
          solverActive
            ? 'Pause the solver first — a turn already in flight would land on the new game.'
            : 'Ends this run and deals a fresh board. The game itself costs nothing to start.'
        }}
      </p>
    </template>

    <template v-else>
      <p id="abandon-question" class="text-sm">
        Abandon this run? It is worth {{ game.score }} points after {{ game.turn }} turns, and
        cannot be picked back up.
      </p>
      <div class="flex flex-wrap gap-2">
        <button
          ref="confirmAbandon"
          type="button"
          class="btn btn-primary rounded-md px-3 py-1.5 text-sm"
          aria-describedby="abandon-question"
          :disabled="!canAbandon || starting"
          @click="abandon()"
        >
          {{ starting ? 'Starting…' : 'Yes, start a new game' }}
        </button>
        <button
          type="button"
          class="btn btn-quiet rounded-md px-3 py-1.5 text-sm font-semibold text-ink-muted hover:text-ink"
          @click="keepPlaying()"
        >
          Keep playing
        </button>
      </div>
    </template>
  </footer>
</template>
