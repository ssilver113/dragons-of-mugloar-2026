<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import AppIcon from './AppIcon.vue'
import StatTile from './StatTile.vue'
import type { GameView } from '../api/types'

/**
 * `announce` is off while the solver holds the game. A turn a player took is worth hearing; a
 * hundred taken for them is the same chatter the decision log is deliberately silent about, and at
 * max speed the queue would outlive the run.
 */
const props = withDefaults(defineProps<{ game: GameView; announce?: boolean }>(), {
  announce: true,
})

/**
 * A level is the only figure here worth marking as it moves. Score and gold change most turns and
 * the turn counter changes every one of them, so flashing those would be a light that is always
 * on; a level is bought deliberately, a few times a game, and is what the whole ad scale is read
 * against. It is a flourish and nothing more — the number itself is what says what happened.
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
 * Lives are drawn rather than counted, up to the point where drawing them stops being readable.
 * Five hearts is where a row still reads at a glance on a 375px screen; past that the count is
 * the clearer statement and the heart goes back to being a label.
 *
 * Zero takes the same path as a large number on purpose — a tile that drew nothing at all would
 * look like a tile that had failed to load, on the one turn where it matters most.
 */
const HEART_LIMIT = 5
const hearts = computed(() =>
  props.game.lives >= 1 && props.game.lives <= HEART_LIMIT ? props.game.lives : 0,
)

/**
 * Whether the bar has left its place in the page — watched on the bar itself rather than on a
 * marker above it, because a marker would need a wrapper and a wrapper is exactly what a sticky
 * element must not have. Sticky travels only within its own parent's box, so a wrapper drawn
 * snugly around the bar gives it a single pixel of travel and it scrolls away like anything else.
 * The root here is the sticky element, which makes the page column its containing block.
 *
 * A one-pixel negative margin at the top is what turns "is on screen" into "is pinned": the bar
 * can only be fully within that inset region while it is still in its resting place.
 *
 * It pins from `sm` up. Below that the five figures wrap onto two rows, and two rows of tiles
 * nailed to the top of a 375px screen would take a sixth of it away from the board they are
 * there to be read against.
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
  <!--
    Pinned so the figures a job is weighed against stay on screen while the board scrolls past
    them. The bar keeps one size whether pinned or not: shrinking it as it sticks would move every
    row below it, which is the jump this was meant to remove rather than add.

    `-mx-4 px-4` is a net zero for the content and gives the pinned bar a ground that reaches the
    column's edges, so a card scrolling underneath does not show through the gaps.
  -->
  <div
    ref="bar"
    class="z-20 -mx-4 px-4 py-2 sm:sticky sm:top-0"
    :class="stuck ? 'sm:border-b sm:border-accent/20 sm:bg-surface/95 sm:backdrop-blur-sm' : ''"
  >
    <!--
      `aria-atomic` is what makes this one announcement rather than five: without it a polite
      region reads out each figure that changed, which is every figure on most turns.
    -->
    <dl
      class="grid grid-cols-3 gap-2 sm:grid-cols-5"
      aria-label="Dragon status"
      :aria-live="announce ? 'polite' : 'off'"
      aria-atomic="true"
    >
      <StatTile label="Score" icon="score" :value="game.score" />
      <StatTile label="Gold" icon="gold" :value="game.gold" />
      <StatTile label="Lives" icon="life" :value="game.lives">
        <!--
          Provided only while the hearts are drawable. Above the limit the slot is absent and the
          tile falls back to its own mark-and-figure, which is what makes a large count look like
          every other figure on the strip rather than like a broken row of hearts.
        -->
        <template v-if="hearts" #figure>
          <!--
            Their own row, with its own spacing: five hearts have to fit a third of a 375px screen,
            which the tile's ordinary mark-to-figure gap does not leave room for.
          -->
          <span class="flex items-center gap-0.5 sm:gap-1">
            <AppIcon
              v-for="n in hearts"
              :key="n"
              name="life"
              :size="20"
              class="size-3.5 sm:size-5"
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
