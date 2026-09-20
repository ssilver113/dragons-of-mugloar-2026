<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import DragonSigil from './DragonSigil.vue'
import type { GameView } from '../api/types'

const props = defineProps<{ ending: 'finished' | 'lost'; game: GameView; starting: boolean }>()
defineEmits<{ restart: [] }>()

/** A lost session is not a defeat — the dragon was fine, the server stopped tracking it. */
const ENDINGS = {
  finished: {
    heading: 'The dragon has fallen',
    action: 'Play again',
  },
  lost: {
    heading: 'This game was lost',
    action: 'Start a new game',
  },
} as const

const copy = computed(() => ENDINGS[props.ending])

/**
 * The button that ended the run has unmounted, so focus would otherwise fall to the document.
 *
 * On mount rather than on a watcher, which is what this component being its own thing buys: it is
 * rendered exactly when the game ends, so "the panel appeared" and "the panel mounted" are the
 * same event and there is no previous value to compare against.
 */
const panel = ref<HTMLElement | null>(null)
onMounted(() => panel.value?.focus())
</script>

<template>
  <section
    ref="panel"
    tabindex="-1"
    class="panel focus-ring flex flex-col items-start gap-4 p-6"
    role="status"
  >
    <DragonSigil
      v-if="ending === 'finished'"
      mood="defeated"
      :size="256"
      class="size-40 self-center sm:size-64"
    />
    <div class="flex flex-col gap-1">
      <h2 class="text-lg font-semibold">{{ copy.heading }}</h2>
      <p v-if="ending === 'lost'" class="text-ink-muted">
        The server is no longer tracking this game — it aged out, or the API restarted. A session is
        never picked back up, so the run ends here.
      </p>
      <p class="text-ink-muted">
        {{ ending === 'lost' ? 'It was worth' : 'Final score' }}
        {{ game.score }} points after {{ game.turn }} turns.
      </p>
    </div>
    <button
      type="button"
      class="btn btn-primary rounded-md px-4 py-2"
      :disabled="starting"
      @click="$emit('restart')"
    >
      {{ starting ? 'Starting…' : copy.action }}
    </button>
  </section>
</template>
