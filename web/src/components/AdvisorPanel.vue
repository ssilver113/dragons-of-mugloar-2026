<script setup lang="ts">
import { computed } from 'vue'
import AdToolbar from './AdToolbar.vue'
import CalibrationTable from './CalibrationTable.vue'
import { advisorArt } from '../assets/artwork'
import type { CalibrationRow } from '../stores/calibration'
import type { FilterId, Posture, SortKey } from '../advisor/ranking'

/**
 * Everything the advisor is and everything it has been wrong about, in one box above both columns.
 *
 * It was three things before — a switch in the board's heading row, a toolbar between that heading
 * and the first card, and the calibration tally in a disclosure of its own somewhere below. Three
 * surfaces for one opinion, and the two halves of the advisor's own argument were never next to
 * each other: what it thinks of this board, and how often it has been right about boards like it.
 *
 * Above the board and the shop rather than inside either, and the reason is movement. It governs
 * both columns, so anywhere inside one of them it would push that column's contents around every
 * time it opened while the other stood still. At the top of the page it opens downwards into space
 * of its own and its header never moves.
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

/**
 * The fold, measured rather than guessed. `height: auto` cannot be transitioned in the browsers
 * this ships to, so the element is given its own scroll height for the length of the animation and
 * handed back to the layout at the end of it.
 */
function measure(el: Element): void {
  const box = el as HTMLElement
  box.style.height = '0px'
  // Read, so the browser has a start value to animate from rather than one paint at the end.
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
    <!--
      One control, not two. The switch used to sit beside a twisty that opened the same panel, which
      let the advisor be on with its table shut and off with its table open — four states for a
      thing that has two. On is open and off is shut, so the switch is the disclosure as well.

      Not a `<details>`, for the same reason as before: it cannot be animated in the browsers this
      ships to, because the user agent hides the content itself and leaves nothing to transition.
    -->
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
      <!--
        Named from the title beside it plus its own word, so the accessible name is "The Advisor
        On" and contains the label a voice user can see. A bare `aria-label` of "Advisor" would read
        well and would not: the visible word on the control is the state, and a name that leaves it
        out is a name nobody can say out loud.
      -->
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
        <!--
          The track is decoration; `aria-checked` is what is read out and the fill is what is seen.
          The chevron is the second half of the same statement — this control opens the table as
          well as switching the advice on, and a disclosure that gives no sign of being one is a
          control people do not find.
        -->
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

    <!--
      The fold. Height is written by JavaScript rather than transitioned from `auto`, because the
      declarative ways of doing it are both too new for the browsers this ships to: `auto` keywords
      need `interpolate-size`, and the `0fr`/`1fr` grid trick needs a Chrome and a Safari newer than
      the build targets. The transition itself is still CSS, and still lives inside the
      reduced-motion query, so under `reduce` there is no rule to obey and the panel simply appears.
    -->
    <Transition name="fold" @enter="measure" @after-enter="release" @leave="collapse">
      <div v-show="advisor" id="advisor-body" class="body">
        <!--
          Sitting at the table rather than printed on it. Hidden below `sm`, where a figure this
          size would take a third of the sheet and the four filters already wrap twice at 375px.
        -->
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
/**
 * The table. Its surface is the `baize` utility, shared with nothing else — the advisor is the only
 * thing in the app laid out on cloth. This rule sets what is particular to a table with a sheet and
 * a counsellor at it.
 */
.advisor {
  position: relative;
  padding: 0.75rem;
}

/**
 * Brass corner clips, drawn with `filter: drop-shadow` rather than `box-shadow`.
 *
 * That is not a preference. A shadow is cast by the border box whatever is painted in it, so an
 * L-shaped clip with only two borders drawn throws the shadow of a whole square and leaves stray
 * dark lines along the two edges that were never painted. The same mistake cost the board its
 * corner brackets once.
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
 * The header row, and the one thing on this panel that never moves.
 *
 * Its padding is even on all four sides, so the mark and the two lines are centred on the closed
 * box and stay exactly where they are when it opens — the gap to the sheet below is the sheet's
 * own top padding, not extra space under the header. Uneven padding here is what made the closed
 * bar look top-heavy and made the whole row shift by ten pixels on every toggle.
 */
.header {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  padding: 0.375rem;
}

/**
 * The mark, and the same drawing as the figure below it seen close. Cream behind it because the
 * portrait's own ground is transparent and the head would otherwise sit on cloth; the crop is set
 * from the head rather than the box, since the drawing is a whole dragon at a desk and only the top
 * fifth of it survives at this size.
 */
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

/* Set from the head's own place in the drawing rather than by eye, and then corrected against the
   rendered pixels twice over: the face centres on 58% across and 19% down. The horizontal figure
   is roughly the mirror of what it was, because the drawing itself was flipped so the dragon faces
   into the page rather than off its left edge. At this scale the head fills most of a 36px disc,
   which is what a roundel is for. */
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

/* Cream on cloth is 6.7:1; nothing on this surface has a sheet under it, so both lines were
   measured against the baize itself. */
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

/**
 * On, the switch is a cream plate with the advisor's own ink on it; off, it is the cloth showing
 * through a pale rim. The state is a word before it is a fill, as the ad verdicts are.
 */
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

/* Inside the switch now, so it takes the colour of whichever state the plate is in rather than
   carrying one of its own. Held back a little, because the word is what says On and Off. */
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

/**
 * The fold. `overflow: hidden` is permanent rather than only worn during the transition, because
 * the figure is taller than the sheet on a short tally and would otherwise hang out of the box for
 * the length of the animation.
 */
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

/**
 * The working sheet. Square-cut and set flat on the cloth: paper in this app is torn and pinned,
 * and this is not the world's paper — it is the sheet the advisor is writing on, so it is the same
 * machine-made stock idea as the log's without being the log.
 */
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
