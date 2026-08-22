<script setup lang="ts">
import { computed } from 'vue'
import { SPEEDS, type SpeedId } from '../stores/autoplay'

const props = defineProps<{
  running: boolean
  stepping: boolean
  waiting: boolean
  speed: SpeedId
  canPlay: boolean
  busy: boolean
}>()
const emit = defineEmits<{
  run: []
  pause: []
  step: []
  'update:speed': [speed: SpeedId]
}>()

// Run and Step are unavailable whenever any turn is in flight, the solver's or the player's.
// Pause is the exception: it exists precisely to interrupt a run that is mid-turn.
const blocked = computed(() => !props.canPlay || props.busy || props.running || props.stepping)

const status = computed(() => {
  if (props.waiting) {
    return 'Rate limited by the game. Waiting, then carrying on.'
  }
  if (props.running) {
    return 'Running. The solver is taking every turn.'
  }
  if (props.stepping) {
    return 'Taking a turn…'
  }
  return 'Idle. The solver takes a turn only when you ask it to.'
})

function toggle(): void {
  if (props.running) {
    emit('pause')
  } else {
    emit('run')
  }
}

function onSpeed(event: Event): void {
  emit('update:speed', (event.target as HTMLSelectElement).value as SpeedId)
}
</script>

<template>
  <section
    aria-labelledby="autoplay-heading"
    class="flex flex-col gap-3 rounded-lg border border-ink-muted/20 p-4"
  >
    <div class="flex flex-wrap items-center justify-between gap-x-4 gap-y-3">
      <h2 id="autoplay-heading" class="text-lg font-semibold">Auto-play</h2>

      <div class="flex flex-wrap items-center gap-2">
        <!--
          One button that changes what it says, not two that swap places. A `v-if` pair would be
          two different elements, so starting a run from the keyboard would drop focus to the top
          of the document and the player would have to tab back to reach Pause.
        -->
        <button
          type="button"
          class="rounded-md px-3 py-1.5 text-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:cursor-not-allowed disabled:opacity-40"
          :class="
            running
              ? 'border border-ink-muted/40 font-medium hover:border-ink'
              : 'bg-accent font-semibold text-surface hover:brightness-110'
          "
          :disabled="!running && blocked"
          @click="toggle()"
        >
          {{ running ? 'Pause' : 'Run' }}
        </button>

        <button
          type="button"
          class="rounded-md border border-ink-muted/40 px-3 py-1.5 text-sm hover:border-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:cursor-not-allowed disabled:opacity-40"
          :disabled="blocked"
          @click="emit('step')"
        >
          Step
        </button>

        <label class="flex items-center gap-2 text-sm text-ink-muted">
          Speed
          <select
            class="rounded-md border border-ink-muted/40 bg-surface-raised px-2 py-1.5 text-sm text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
            :value="speed"
            @change="onSpeed"
          >
            <option v-for="option in SPEEDS" :key="option.id" :value="option.id">
              {{ option.label }}
            </option>
          </select>
        </label>
      </div>
    </div>

    <!-- The one thing worth announcing per run. The log itself is silent: at max speed it would
         read out a turn every few hundred milliseconds and drown everything else out. -->
    <p class="text-sm text-ink-muted" role="status">{{ status }}</p>
  </section>
</template>
