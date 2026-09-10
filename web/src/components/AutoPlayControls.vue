<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
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

// Run and Step are blocked by any turn in flight. Pause is not — it exists to interrupt one.
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

const turnCount = (turns: number) => `${turns} turn${turns === 1 ? '' : 's'}`

/** The same reading, short enough for the faceplate, which stays visible once the machine pins. */
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
    return props.turns ? `running, ${turnCount(props.turns)}` : 'running'
  }
  if (props.stepping) {
    return 'taking a turn'
  }
  return props.turns ? `idle, ${turnCount(props.turns)} taken` : 'idle'
})

/** The period tracks the speed actually being played, so the cog reports rather than decorates. */
const COG_PERIOD: Record<SpeedId, string> = {
  slow: '3200ms',
  normal: '1600ms',
  fast: '900ms',
  max: '450ms',
}
const cogPeriod = computed(() => COG_PERIOD[props.speed])

/**
 * Whether the drive has left the board it is mounted on, which decides whether it carries its own
 * timber. Observed on the drive itself — a snug wrapper would give sticky a pixel of travel and it
 * would scroll away. The root margin is the pin offset plus one pixel.
 */
const drive = ref<HTMLElement | null>(null)
const stuck = ref(false)
let observer: IntersectionObserver | undefined

onMounted(() => {
  if (drive.value === null || typeof IntersectionObserver === 'undefined') {
    return
  }
  observer = new IntersectionObserver(([entry]) => (stuck.value = entry.intersectionRatio < 1), {
    threshold: [1],
    rootMargin: '-83px 0px 0px 0px',
  })
  observer.observe(drive.value)
})

onBeforeUnmount(() => observer?.disconnect())

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
    ref="drive"
    aria-labelledby="autoplay-heading"
    class="machine"
    :class="{ pinned: running, 'timber lifted': running && stuck }"
  >
    <!-- Brass carries `ink` at 6.37:1 and nothing else: `ink-muted` falls to 3.48, which is why
         the sentence lives on the deck below and only the short reading sits up here. -->
    <div class="faceplate">
      <span class="rivet rivet-tl" aria-hidden="true" />
      <span class="rivet rivet-tr" aria-hidden="true" />
      <span class="rivet rivet-bl" aria-hidden="true" />
      <span class="rivet rivet-br" aria-hidden="true" />

      <h2 id="autoplay-heading" class="engraved">
        <AppIcon
          name="autoplay"
          :size="18"
          class="cog"
          :class="{ turning: running }"
          :style="{ '--cog-period': cogPeriod }"
        />
        Auto-play
      </h2>
      <span class="reading">{{ shortStatus }}</span>
    </div>

    <div class="oak-plate deck">
      <div class="flex flex-wrap items-center gap-2">
        <!-- One button that changes its label, not a `v-if` pair: swapping elements would drop
             keyboard focus to the top of the document on every Run. -->
        <button
          type="button"
          class="relief rounded-md px-3 py-1.5 text-sm font-semibold text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:cursor-not-allowed disabled:bg-transparent disabled:bg-none disabled:text-ink-muted disabled:shadow-none"
          :class="
            running ? 'bg-surface-raised hover:brightness-105' : 'bg-brass hover:brightness-105'
          "
          :disabled="!running && blocked"
          @click="toggle()"
        >
          {{ running ? 'Pause' : 'Run' }}
        </button>

        <button
          type="button"
          class="relief rounded-md bg-surface-raised px-3 py-1.5 text-sm text-ink enabled:hover:brightness-105 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:cursor-not-allowed disabled:text-ink-muted disabled:shadow-none"
          :disabled="blocked"
          @click="emit('step')"
        >
          Step
        </button>

        <label class="flex items-center gap-2 text-sm text-ink-muted">
          Speed
          <!-- The arrow is the shell's: the native one is laid against the rim and ignores
               `padding-right`. `pr-7` reserves the space it is drawn in. -->
          <span class="select-shell">
            <select
              class="setting relief rounded-md py-1.5 pr-7 pl-2 text-sm text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
              :value="speed"
              @change="onSpeed"
            >
              <option v-for="option in SPEEDS" :key="option.id" :value="option.id">
                {{ option.label }}
              </option>
            </select>
          </span>
        </label>
      </div>

      <!-- The one thing announced per run. The log is silent: at max speed it would read out a
           turn every few hundred milliseconds. -->
      <p class="text-sm text-ink-muted" role="status">{{ status }}</p>
    </div>
  </section>
</template>

<style scoped>
/**
 * The drive, mounted on the board the log lies on, with no ground of its own. The negative margin
 * is cancelled by the padding so the box reaches the board's edges while its content still lines
 * up with the book below — which only matters once it lifts, and then matters completely.
 */
.machine {
  position: relative;
  margin-inline: calc(var(--solver-pad) * -1);
  padding: 0.125rem var(--solver-pad);
}

/**
 * Pinned only on wide screens, where Pause would otherwise scroll away from the board being
 * watched. On a phone the drive and the log share a tab, so it is already a tap away.
 */
@media (width >= 64rem) {
  .machine.pinned {
    position: sticky;
    /* Clears the stats rail, measured at 86px wherever this rule applies. In rem so it grows with
       the type the rail is sized by, and below the rail in stacking order so that being wrong
       hides the machine rather than the score. */
    top: 5.375rem;
    z-index: 1;
  }

  /**
   * Lifted, and carrying a piece of the board: without it the book would scroll through the gaps
   * either side. Applied only while it is actually clear, so the wood never appears twice at once.
   */
  .machine.lifted {
    border-radius: 0 0 3px 3px;
    box-shadow:
      inset 0 0 0 1px oklch(20% 0.02 50 / 0.6),
      0 6px 14px oklch(30% 0.028 52 / 0.35);
  }
}

/** The brass plate the mechanism is named on, screwed down rather than inlaid. */
.faceplate {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  /* Centred, not baseline: the heading is a flex row starting with the cog, so its baseline is the
     cog's bottom edge and the reading sat a couple of pixels low. */
  align-items: center;
  gap: 0.25rem 0.625rem;
  padding: 0.4rem 0.9rem 0.45rem;
  border-radius: 2px;
  background-color: var(--color-brass);
  background-image: linear-gradient(oklch(100% 0 0 / 0.28), oklch(0% 0 0 / 0.12));
  box-shadow:
    inset 0 1px 0 oklch(100% 0 0 / 0.55),
    inset 0 0 0 1px oklch(52% 0.07 82 / 0.6),
    0 1px 2px oklch(18% 0.02 50 / 0.55);
}

.engraved {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0;
  font-size: 0.9375rem;
  font-weight: 600;
  letter-spacing: 0.06em;
  /* One pale pixel below a dark face: the shallowest engraving that survives at fifteen pixels. */
  text-shadow: 0 1px 0 oklch(100% 0 0 / 0.4);
}

/* Full-strength ink, because brass carries nothing else; held back by size and letterfit instead. */
.reading {
  font-size: 0.75rem;
  letter-spacing: 0.03em;
  text-shadow: 0 1px 0 oklch(100% 0 0 / 0.35);
}

/** A rivet, one per corner. Round because a shadow follows the border box, not the paint. */
.rivet {
  position: absolute;
  width: 5px;
  height: 5px;
  border-radius: 9999px;
  background-image: radial-gradient(
    circle at 35% 30%,
    oklch(94% 0.06 88),
    var(--color-brass) 55%,
    oklch(54% 0.07 78)
  );
  box-shadow: 0 1px 1px oklch(18% 0.02 50 / 0.5);
}

.rivet-tl,
.rivet-bl {
  left: 5px;
}

.rivet-tr,
.rivet-br {
  right: 5px;
}

.rivet-tl,
.rivet-tr {
  top: 5px;
}

.rivet-bl,
.rivet-br {
  bottom: 5px;
}

/** Oak rather than brass because it carries a sentence: `ink-muted` is 3.48:1 on brass, 5.90 here. */
.deck {
  display: flex;
  flex-direction: column;
  gap: 0.625rem;
  margin-top: 0.5rem;
  padding: 0.75rem 0.875rem;
}

/* Rimmed in the plate's metal rather than ink: every other edge on this object is hardware. */
.setting {
  border: 1px solid oklch(58% 0.07 78 / 0.75);
  background-color: var(--color-surface-raised);
}

/** Under `reduce` the rule does not exist and the cog is simply the mark it always was. */
@media (prefers-reduced-motion: no-preference) {
  .cog.turning {
    animation: cog-turn var(--cog-period, 1600ms) linear infinite;
  }
}

@keyframes cog-turn {
  to {
    transform: rotate(1turn);
  }
}
</style>
