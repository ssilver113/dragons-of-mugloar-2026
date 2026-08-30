<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppIcon from './AppIcon.vue'
import { SPEEDS, type Halt, type SpeedId } from '../stores/autoplay'

const props = defineProps<{
  running: boolean
  stepping: boolean
  waiting: boolean
  speed: SpeedId
  canPlay: boolean
  busy: boolean
  halt: Halt | null
  turns: number
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

/**
 * The same reading, short enough to sit on the summary line. The panel starts closed — a player
 * opening the game is here to play it, not to hand it over — so what it says while shut has to be
 * enough to tell whether the solver is working, waiting or stopped.
 */
const shortStatus = computed(() => {
  if (props.halt?.kind === 'stalled') {
    return 'stopped to check in'
  }
  if (props.halt?.kind === 'error') {
    return 'the run stopped'
  }
  if (props.waiting) {
    return 'rate limited, waiting'
  }
  if (props.running) {
    return props.turns ? `running, ${props.turns} turns` : 'running'
  }
  if (props.stepping) {
    return 'taking a turn'
  }
  return props.turns ? `idle, ${props.turns} turns taken` : 'idle'
})

/**
 * Closed by default, and opened by us only when the run stops on its own. A halt is the one state
 * that needs a decision — keep going, or try again — and the buttons that answer it are in here.
 * Nothing ever closes the panel on the player's behalf: reopening is theirs to undo, shutting it
 * under their hands is not.
 */
const open = ref(false)

watch(
  () => props.halt,
  (halt) => {
    if (halt !== null) {
      open.value = true
    }
  },
)

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
  <section aria-labelledby="autoplay-heading">
    <details class="panel" :open="open" @toggle="open = ($event.target as HTMLDetailsElement).open">
      <summary
        class="rounded-lg px-4 py-3 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
      >
        <!--
          A div rather than a span: `summary` takes flow content, and an `h2` inside phrasing
          content would be invalid markup that only happens to render.

          Centred, not baseline-aligned. The heading is itself a flex row, so its baseline is taken
          from its first item — the icon — whose baseline is its bottom edge rather than any text,
          and the status sat a couple of pixels low against it.
        -->
        <div class="inline-flex flex-wrap items-center gap-x-2 gap-y-0.5 align-middle">
          <h2 id="autoplay-heading" class="flex items-center gap-2 text-base font-semibold">
            <AppIcon name="autoplay" :size="18" />
            Auto-play
          </h2>
          <span class="text-sm text-ink-muted">— {{ shortStatus }}</span>
        </div>
      </summary>

      <div class="flex flex-col gap-3 px-4 pb-4">
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
                ? 'relief border border-ink-muted/40 bg-surface-raised/60 font-medium hover:border-ink'
                : 'relief bg-accent font-semibold text-surface hover:brightness-110'
            "
            :disabled="!running && blocked"
            @click="toggle()"
          >
            {{ running ? 'Pause' : 'Run' }}
          </button>

          <button
            type="button"
            class="relief rounded-md border border-ink-muted/40 bg-surface-raised/60 px-3 py-1.5 text-sm hover:border-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:cursor-not-allowed disabled:opacity-40 disabled:shadow-none"
            :disabled="blocked"
            @click="emit('step')"
          >
            Step
          </button>

          <label class="flex items-center gap-2 text-sm text-ink-muted">
            Speed
            <select
              class="relief rounded-md border border-ink-muted/40 bg-surface-raised px-2 py-1.5 text-sm text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
              :value="speed"
              @change="onSpeed"
            >
              <option v-for="option in SPEEDS" :key="option.id" :value="option.id">
                {{ option.label }}
              </option>
            </select>
          </label>
        </div>

        <!-- The one thing worth announcing per run. The log itself is silent: at max speed it would
             read out a turn every few hundred milliseconds and drown everything else out. -->
        <p class="text-sm text-ink-muted" role="status">{{ status }}</p>
      </div>
    </details>
  </section>
</template>
