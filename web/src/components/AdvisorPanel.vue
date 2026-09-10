<script setup lang="ts">
import { computed } from 'vue'
import AdToolbar from './AdToolbar.vue'
import CalibrationTable from './CalibrationTable.vue'
import { advisorArt } from '../assets/artwork'
import type { CalibrationRow } from '../stores/calibration'
import type { FilterId, Posture, SortKey } from '../advisor/ranking'

/**
 * Everything the advisor is, and everything it has been wrong about, in one box. Above both
 * columns rather than inside either: it governs both, and inside one it would shove that column
 * around on every toggle while the other stood still.
 */
const props = defineProps<{
  advisor: boolean
  sort: SortKey
  posture: Posture
  filters: FilterId[]
  shown: number
  total: number
  lifeCost: number
  rows: CalibrationRow[]
  attempts: number
  games: number
}>()
defineEmits<{
  'toggle-advisor': []
  'update:sort': [key: SortKey]
  'update:posture': [posture: Posture]
  'toggle-filter': [id: FilterId]
  'clear-filters': []
  'reset-calibration': []
}>()

const subtitle = computed(() =>
  props.advisor
    ? `Ranking ${props.total} ${props.total === 1 ? 'job' : 'jobs'} by what each is worth`
    : 'Off — the board is listed in the order it was posted',
)

/** `height: auto` cannot be transitioned in the target browsers, so the height is written here. */
function measure(el: Element): void {
  const box = el as HTMLElement
  box.style.height = '0px'
  // Forces a reflow, so the browser has a start value to animate from.
  void box.offsetHeight
  box.style.height = `${box.scrollHeight}px`
}

function release(el: Element): void {
  ;(el as HTMLElement).style.height = ''
}

function collapse(el: Element): void {
  const box = el as HTMLElement
  box.style.height = `${box.scrollHeight}px`
  void box.offsetHeight
  box.style.height = '0px'
}
</script>

<template>
  <section class="baize advisor" aria-labelledby="advisor-title">
    <!-- One control: on is open, off is shut. Not a `<details>` — the user agent hides the content
         itself, leaving nothing to transition. -->
    <div class="header">
      <span class="mark">
        <img
          :src="advisorArt"
          alt=""
          aria-hidden="true"
          width="370"
          height="451"
          decoding="async"
        />
      </span>
      <span class="naming">
        <span id="advisor-title" class="title">The Advisor</span>
        <span class="subtitle">{{ subtitle }}</span>
      </span>
      <!-- Named "The Advisor On" from the title plus its own word, so the accessible name
           contains the visible label a voice user would say. -->
      <button
        type="button"
        role="switch"
        class="switch"
        :aria-checked="advisor"
        :aria-expanded="advisor"
        aria-controls="advisor-body"
        aria-labelledby="advisor-title advisor-state"
        @click="$emit('toggle-advisor')"
      >
        <span id="advisor-state">{{ advisor ? 'On' : 'Off' }}</span>
        <!-- The track is decoration; `aria-checked` carries the state. The chevron says this
             control is also the disclosure. -->
        <span aria-hidden="true" class="track"><span class="knob" /></span>
        <svg
          class="chevron"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2.2"
          aria-hidden="true"
          focusable="false"
        >
          <path d="m6 9 6 6 6-6" />
        </svg>
      </button>
    </div>

    <!-- Height is scripted because both declarative routes are too new for the build targets:
         `interpolate-size` for the `auto` keywords, and newer engines for the `0fr`/`1fr` grid
         trick. The transition itself is still CSS, inside the reduced-motion query. -->
    <Transition name="fold" @enter="measure" @after-enter="release" @leave="collapse">
      <div v-show="advisor" id="advisor-body" class="body">
        <!-- Hidden below `sm`, where it would take a third of the sheet and the filters already
             wrap twice at 375px. -->
        <img
          class="scribe"
          :src="advisorArt"
          alt=""
          aria-hidden="true"
          width="370"
          height="451"
          loading="lazy"
          decoding="async"
        />

        <div class="sheet">
          <AdToolbar
            :sort="sort"
            :posture="posture"
            :filters="filters"
            :shown="shown"
            :total="total"
            :life-cost="lifeCost"
            @update:sort="$emit('update:sort', $event)"
            @update:posture="$emit('update:posture', $event)"
            @toggle-filter="$emit('toggle-filter', $event)"
            @clear-filters="$emit('clear-filters')"
          />

          <hr class="rule" />

          <CalibrationTable
            :rows="rows"
            :attempts="attempts"
            :games="games"
            @reset="$emit('reset-calibration')"
          />
        </div>
      </div>
    </Transition>
  </section>
</template>

<style scoped>
/** The table. `baize` is the surface, shared with nothing else; this sets what is particular. */
.advisor {
  position: relative;
  padding: 0.75rem;
}

/**
 * Brass corner clips. `drop-shadow`, not `box-shadow`: a box shadow follows the border box, so an
 * L-shaped clip would throw the shadow of a whole square along two edges that were never painted.
 */
.advisor::before,
.advisor::after {
  content: '';
  position: absolute;
  width: 15px;
  height: 15px;
  border: 2px solid var(--color-brass);
  border-radius: 2px;
  filter: drop-shadow(0 1px 1px oklch(18% 0.02 50 / 0.5));
  pointer-events: none;
}

.advisor::before {
  top: 3px;
  left: 3px;
  border-right: 0;
  border-bottom: 0;
}

.advisor::after {
  bottom: 3px;
  right: 3px;
  border-left: 0;
  border-top: 0;
}

/**
 * The one thing on this panel that never moves. Padding is even on all four sides so the row does
 * not shift on a toggle; the gap below is the fold's own top padding.
 */
.header {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  padding: 0.375rem;
}

/** The same drawing as the figure below, cropped to the head. Cream behind it, because the art is
 * transparent and the head would otherwise sit on cloth. */
.mark {
  width: 2.25rem;
  height: 2.25rem;
  flex: none;
  overflow: hidden;
  border-radius: 9999px;
  background-color: var(--color-surface-raised);
  box-shadow:
    inset 0 0 0 1px oklch(30% 0.04 150 / 0.45),
    0 1px 2px oklch(20% 0.03 150 / 0.5);
}

/* Measured against the rendered pixels, not eyeballed: the face centres on 58% across, 19% down. */
.mark img {
  display: block;
  width: 195%;
  max-width: none;
  height: auto;
  margin: -8.6% 0 0 -66.9%;
}

.naming {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}

/* Cream on cloth is 6.7:1. Nothing here sits on a sheet, so both lines are measured on baize. */
.title {
  font-family: var(--font-display);
  font-size: 1rem;
  font-weight: 600;
  line-height: 1.2;
  color: var(--color-surface-raised);
}

/* Derived from the title rather than picked, so the two cannot drift: 5.1:1 on the cloth. */
.subtitle {
  margin-top: 1px;
  font-size: 0.75rem;
  color: color-mix(in oklab, var(--color-surface-raised) 82%, var(--color-baize));
}

/** The state is a word before it is a fill, as the ad verdicts are. */
.switch {
  display: flex;
  flex: none;
  align-items: center;
  gap: 0.5rem;
  border: 1px solid;
  border-radius: 9999px;
  padding: 0.2rem 0.7rem;
  font: inherit;
  font-size: 0.8125rem;
  font-weight: 600;
}

.switch:focus-visible {
  outline: 2px solid var(--color-brass);
  outline-offset: 2px;
}

.switch[aria-checked='true'] {
  border-color: var(--color-surface-raised);
  background-color: var(--color-surface-raised);
  color: var(--color-advisor);
}

.switch[aria-checked='false'] {
  border-color: color-mix(in oklab, var(--color-surface-raised) 60%, transparent);
  background-color: color-mix(in oklab, var(--color-baize) 50%, transparent);
  color: color-mix(in oklab, var(--color-surface-raised) 88%, var(--color-baize));
}

.track {
  position: relative;
  width: 28px;
  height: 16px;
  flex: none;
  border-radius: 9999px;
}

.switch[aria-checked='true'] .track {
  background-color: color-mix(in oklab, var(--color-advisor) 35%, transparent);
}

.switch[aria-checked='false'] .track {
  background-color: color-mix(in oklab, var(--color-surface-raised) 40%, transparent);
}

.knob {
  position: absolute;
  top: 2px;
  width: 12px;
  height: 12px;
  border-radius: 9999px;
}

.switch[aria-checked='true'] .knob {
  left: 14px;
  background-color: var(--color-advisor);
}

.switch[aria-checked='false'] .knob {
  left: 2px;
  background-color: color-mix(in oklab, var(--color-surface-raised) 88%, var(--color-baize));
}

/* Held back, because the word is what says On and Off. */
.chevron {
  width: 0.875rem;
  height: 0.875rem;
  display: block;
  flex: none;
  margin-right: -0.15rem;
  opacity: 0.75;
}

[aria-expanded='true'] .chevron {
  rotate: 180deg;
}

/** `overflow: hidden` is permanent: on a short tally the figure is taller than the sheet. */
.body {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  overflow: hidden;
  padding-top: 0.375rem;
}

@media (prefers-reduced-motion: no-preference) {
  .chevron {
    transition: rotate 240ms cubic-bezier(0.22, 0.61, 0.36, 1);
  }

  .fold-enter-active,
  .fold-leave-active {
    transition:
      height 260ms cubic-bezier(0.22, 0.61, 0.36, 1),
      opacity 200ms ease-out;
  }

  .fold-enter-from,
  .fold-leave-to {
    opacity: 0;
  }
}

.scribe {
  display: none;
  width: 8.25rem;
  height: auto;
  flex: none;
  filter: drop-shadow(0 2px 4px oklch(18% 0.03 150 / 0.55));
}

@media (width >= 40rem) {
  .scribe {
    display: block;
  }
}

/** Square-cut and flat: the advisor's own stock, not the torn parchment the world is drawn on. */
.sheet {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 0.8125rem;
  border-radius: 2px;
  background-color: var(--color-surface-raised);
  padding: 0.8125rem;
  box-shadow:
    inset 0 1px 0 oklch(100% 0 0 / 0.6),
    inset 0 0 0 1px oklch(35% 0.04 150 / 0.3),
    0 2px 5px oklch(18% 0.03 150 / 0.5);
}

.rule {
  height: 1px;
  border: 0;
  margin: 0;
  background-color: color-mix(in oklab, var(--color-advisor) 28%, transparent);
}
</style>
