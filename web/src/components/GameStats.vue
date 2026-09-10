<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import AppIcon from './AppIcon.vue'
import StatTile from './StatTile.vue'
import type { GameView } from '../api/types'

/** `announce` is off under the solver: at max speed the queue would outlive the run. */
const props = withDefaults(defineProps<{ game: GameView; announce?: boolean }>(), {
  announce: true,
})

/**
 * The only figure worth marking as it moves: the others change most turns, so striking them would
 * be a light that is always on. A flourish only — the number itself says what happened.
 */
const levelledUp = ref(false)
let clear: ReturnType<typeof setTimeout> | undefined

watch(
  () => props.game.level,
  (now, before) => {
    if (now <= before) {
      return
    }
    levelledUp.value = true
    clearTimeout(clear)
    clear = setTimeout(() => (levelledUp.value = false), 1200)
  },
)

onBeforeUnmount(() => clearTimeout(clear))

/**
 * Four hearts is the widest row the gauge holds at 375px; above that the count is the statement.
 * Zero takes the same path, because a window drawing nothing would look like one that failed.
 */
const HEART_LIMIT = 4
const hearts = computed(() =>
  props.game.lives >= 1 && props.game.lives <= HEART_LIMIT ? props.game.lives : 0,
)

/**
 * Whether the bar has left its place. Observed on the bar itself: a marker would need a wrapper,
 * and a snug wrapper gives a sticky element one pixel of travel. The one-pixel inset is what turns
 * "on screen" into "pinned". It pins at every width, which is what the strip was shrunk for.
 */
const bar = ref<HTMLElement | null>(null)
const stuck = ref(false)
let observer: IntersectionObserver | undefined

onMounted(() => {
  if (bar.value === null || typeof IntersectionObserver === 'undefined') {
    return
  }
  observer = new IntersectionObserver(([entry]) => (stuck.value = entry.intersectionRatio < 1), {
    threshold: [1],
    rootMargin: '-1px 0px 0px 0px',
  })
  observer.observe(bar.value)
})

onBeforeUnmount(() => observer?.disconnect())
</script>

<template>
  <!-- One size pinned or not: resizing as it sticks would move every row below it, which is the
       jump this was meant to remove. `-mx-4 px-4` is a net zero that reaches the column's edges,
       so nothing scrolls through the gaps beside the hide. -->
  <div
    ref="bar"
    class="sticky top-0 z-20 -mx-4 px-4 py-1.5 sm:py-2"
    :class="stuck ? 'bg-surface/95 backdrop-blur-sm' : ''"
  >
    <!-- Five windows sunk into leather, the app's own machine as against the world's furniture.
         Nothing is read against the hide: every gauge brings its own ground. `aria-atomic` is what
         makes this one announcement rather than five. -->
    <dl
      class="leather bed grid grid-cols-5 gap-1 sm:gap-2"
      aria-label="Dragon status"
      :class="{ lifted: stuck }"
      :aria-live="announce ? 'polite' : 'off'"
      aria-atomic="true"
    >
      <StatTile label="Score" icon="score" :value="game.score" />
      <StatTile label="Gold" icon="gold" :value="game.gold" />
      <StatTile label="Lives" icon="life" :value="game.lives">
        <!-- Absent above the limit, so a large count falls back to the gauge's own figure. -->
        <template v-if="hearts" #figure>
          <!-- Own spacing: four hearts must fit a fifth of a 375px screen. -->
          <span class="flex items-center gap-px sm:gap-1">
            <AppIcon
              v-for="n in hearts"
              :key="n"
              name="life"
              :size="20"
              class="size-2.5 sm:size-5"
            />
          </span>
          <!-- The hearts are pictures. This is what the live region actually reads out. -->
          <span class="sr-only">{{ game.lives }}</span>
        </template>
      </StatTile>
      <StatTile label="Level" icon="level" :value="game.level" :emphasis="levelledUp" />
      <StatTile label="Turn" icon="turn" :value="game.turn" />
    </dl>
  </div>
</template>

<style scoped>
/** The bed the windows are sunk into. The radius is the log volume's, not a panel's. */
.bed {
  padding: 0.3125rem;
  border-radius: 3px;
  box-shadow:
    inset 0 0 0 1px oklch(20% 0.02 40 / 0.75),
    inset 0 1px 0 oklch(100% 0 0 / 0.1),
    0 2px 5px oklch(30% 0.028 52 / 0.35);
}

@media (width >= 40rem) {
  .bed {
    padding: 0.4375rem;
  }
}

/** The only thing that changes when the bar pins: the rim stays and the cast shadow deepens. */
.lifted {
  box-shadow:
    inset 0 0 0 1px oklch(20% 0.02 40 / 0.75),
    inset 0 1px 0 oklch(100% 0 0 / 0.1),
    0 6px 14px oklch(30% 0.028 52 / 0.4);
}
</style>
