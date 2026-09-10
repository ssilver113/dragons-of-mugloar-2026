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

const turnCount = (turns: number) => `${turns} turn${turns === 1 ? '' : 's'}`

/**
 * The same reading, short enough to sit on the faceplate. It used to be the summary of a closed
 * disclosure; it is now what the plate says while the machine is pinned to the top of the screen
 * and the sentence below it has scrolled away.
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
    return props.turns ? `running, ${turnCount(props.turns)}` : 'running'
  }
  if (props.stepping) {
    return 'taking a turn'
  }
  return props.turns ? `idle, ${turnCount(props.turns)} taken` : 'idle'
})

/**
 * How fast the drive turns, taken from how fast it is actually playing. A cog spinning at one rate
 * while the log fills at four different ones would be decoration; this is the machine reporting
 * its own gearing, which is the only reason it is drawn moving at all.
 */
const COG_PERIOD: Record<SpeedId, string> = {
  slow: '3200ms',
  normal: '1600ms',
  fast: '900ms',
  max: '450ms',
}
const cogPeriod = computed(() => COG_PERIOD[props.speed])

/**
 * Whether the drive has actually left the board it is mounted on. Watched on the drive itself
 * rather than on a marker above it, for the reason the stats rail already records: sticky travels
 * only within its own parent's box, so a wrapper drawn snugly around it would give it a pixel of
 * travel and it would scroll away like anything else.
 *
 * The inset in the margin is the pin's own offset plus one pixel, so the drive can only be fully
 * inside that region while it is still resting on the wood. What the answer buys is a ground:
 * mounted, the plate needs none and the board shows through around it; lifted, it has to carry its
 * own timber or the page would scroll through the gaps beside it.
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
    <!--
      The faceplate. Brass carries `ink` at 6.37:1 and nothing else in the palette — `ink-muted`
      falls to 3.48 on it — so the reading beside the heading is full-strength ink held back by
      size and letterfit instead of by colour. That is the whole reason the sentence lives on the
      plate below rather than up here.
    -->
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
        <!--
          One button that changes what it says, not two that swap places. A `v-if` pair would be
          two different elements, so starting a run from the keyboard would drop focus to the top
          of the document and the player would have to tab back to reach Pause.

          Brass, like the shop's Buy: on a machine the thing you press is the fitting, not a
          painted word. It is the same departure from the accent for the same reason, and it takes
          `ink` for the same reason too.
        -->
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
          <!--
            The arrow is the shell's, not the browser's — the native one is laid out against the rim
            and cannot be given room. `pr-7` is what reserves the space it is drawn in.
          -->
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

      <!-- The one thing worth announcing per run. The log itself is silent: at max speed it would
           read out a turn every few hundred milliseconds and drown everything else out. -->
      <p class="text-sm text-ink-muted" role="status">{{ status }}</p>
    </div>
  </section>
</template>

<style scoped>
/**
 * The drive, mounted on the board the log lies on.
 *
 * It has no ground of its own: the timber behind it is the board, and everything here that carries
 * a word — the brass plate, the oak deck — brings its own surface, exactly as an ad, a plaque and a
 * crest do on the three boards above. The log records the solver's turns and nothing else's, which
 * is why the two share one piece of furniture rather than sitting in two panels.
 *
 * The negative margin is cancelled by the padding, so the content lines up with the book below
 * while the box itself reaches the board's edges. That only matters once it lifts, and then it
 * matters completely.
 */
.machine {
  position: relative;
  margin-inline: calc(var(--solver-pad) * -1);
  padding: 0.125rem var(--solver-pad);
}

/**
 * Pinned while the solver is running, and only where the problem exists. On a phone the drive and
 * the log share a tab, so Pause is already a tap away; on a wide screen the board and the shop sit
 * above them both and Pause would otherwise be a scroll away from the thing the player is watching.
 *
 * Sticky against the page rather than a scroller: the containing block is the board that holds the
 * drive and the volume, so the plate travels the height of the log and then leaves with it.
 */
@media (width >= 64rem) {
  .machine.pinned {
    position: sticky;
    /* Under the figures, not behind them. The stats rail pins to the top of the window from `sm`
       up and is 82px tall wherever its five tiles fit on one row, which is everywhere this rule
       applies. In rem rather than pixels so the offset grows with the type the bar is sized by,
       and the machine's stacking order is left below the rail's so that being wrong about it hides
       the machine rather than letting it cover the score. */
    top: 5.125rem;
    z-index: 1;
  }

  /**
   * Lifted off the board, and carrying a piece of it. A plate riding the top of the screen with
   * nothing behind it would let the book scroll through the gaps either side of it, so it takes
   * the same timber it was resting on and a shadow that says it is no longer flat against it.
   *
   * Only while it is actually clear of its place, so the wood never appears twice at once: mounted
   * there is one board, lifted there are two and the second is meant to be seen.
   */
  .machine.lifted {
    border-radius: 0 0 3px 3px;
    box-shadow:
      inset 0 0 0 1px oklch(20% 0.02 50 / 0.6),
      0 6px 14px oklch(30% 0.028 52 / 0.35);
  }
}

/**
 * The brass plate the mechanism is named on. Screwed down rather than inlaid — the rim is the
 * plate's own edge catching light at the top and losing it at the bottom, and the rivets are proud
 * of it.
 */
.faceplate {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  /* Centred, not on the baseline. The heading is itself a flex row with the cog in it, and a flex
     container takes its baseline from its first item — the cog, whose baseline is its bottom edge.
     So aligning on baselines lined the reading up with the bottom of a picture rather than with
     the word beside it, and the two sat a couple of pixels apart. */
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
  /* Struck into the metal: a dark face with one pale pixel below it, which is the shallowest
     engraving that survives at fifteen pixels. */
  text-shadow: 0 1px 0 oklch(100% 0 0 / 0.4);
}

/* Full-strength ink, because brass carries nothing else. What holds it back is that it is two
   sizes down and spaced wide, which is how a rating plate distinguishes its own small print. */
.reading {
  font-size: 0.75rem;
  letter-spacing: 0.03em;
  text-shadow: 0 1px 0 oklch(100% 0 0 / 0.35);
}

/**
 * A rivet, one at each corner. Round for the same reason the reputation wall's nails are: a shadow
 * is cast by the border box whatever is painted inside it, so anything but a circle throws the
 * shadow of a square.
 */
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

/**
 * The working deck. Planed oak rather than more brass: this is the one surface here that carries a
 * sentence, and brass holds `ink-muted` at 3.48:1. On a plate it is 5.90, which is the same
 * distinction the shop already draws between a plaque with two words on it and a plate with a line
 * of prose.
 */
.deck {
  display: flex;
  flex-direction: column;
  gap: 0.625rem;
  margin-top: 0.5rem;
  padding: 0.75rem 0.875rem;
}

/* A setting on the deck, rimmed in the same metal as the plate above it rather than in ink — every
   other edge on this object is hardware. */
.setting {
  border: 1px solid oklch(58% 0.07 78 / 0.75);
  background-color: var(--color-surface-raised);
}

/**
 * The drive turning. Rotation is the only motion here, its period is the speed the solver is
 * actually playing at, and under `reduce` the rule does not exist — the cog is then simply the
 * mark it always was.
 */
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
